package dev.leadshot;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Properties;

/**
 * Read-only empirical audit of v0.50's approximate Elytra dynamics.
 * Predictions are saved BEFORE future samples arrive. Only uninterrupted,
 * near-constant-view, no-impulse intervals are scored as physics-calibration
 * samples; changing camera or rocket-like impulses are reported as excluded.
 * This module NEVER supplies a shot, a steering command, or a READY signal.
 */
public final class GlideCalibrationV501 {
    public static final int[] HORIZONS={1,3,5};
    private static final double CAMERA_TOLERANCE_DEGREES=.65;
    private static final double VELOCITY_IMPULSE_LIMIT=.38;
    private static final int MAX_PENDING=18;
    // Recent, eligible walk-forward errors only. A fixed-size / fixed-age
    // window prevents obsolete flights from masquerading as current evidence.
    private static final int MAX_RECENT_FIVE=64;
    private static final long RECENT_MAX_AGE=180;
    private record RecentError(long dueTick,double modelSquared,double linearSquared) {}
    public record RecentFive(long newestTick,int evaluations,double modelPositionRms,
                             double baselinePositionRms,long latestEvaluatedTick) {}

    public record Sample(long tick,CoreBallistics.Vec position,CoreBallistics.Vec velocity,
                         double yaw,double pitch,boolean gliding,boolean collision){
        public boolean finite(){return tick>=0 && position!=null&&position.finite()
                &&velocity!=null&&velocity.finite()&&Double.isFinite(yaw)&&Double.isFinite(pitch);}
    }
    public record HorizonStats(int ticks,long evaluated,double modelPositionRms,
                               double baselinePositionRms,double velocityRms,double maxPositionError) {}
    public record Summary(long observations,long skippedTurning,long skippedImpulses,
                          long skippedGaps,long skippedInactive,long skippedInvalid,
                          HorizonStats[] horizons){
        public Summary {horizons=horizons.clone();}
        @Override public HorizonStats[] horizons(){return horizons.clone();}
    }
    private static final class Pending {
        final long due;
        final int index;
        final CoreBallistics.Vec predictedPosition,predictedVelocity,linearBaseline;
        final double referenceYaw,referencePitch;
        boolean turning,impulse;
        Pending(long due,int index,GlideDynamicsV50.State estimate,CoreBallistics.Vec baseline,
                double yaw,double pitch){
            this.due=due;this.index=index;
            this.predictedPosition=estimate.position();this.predictedVelocity=estimate.velocity();
            this.linearBaseline=baseline;referenceYaw=yaw;referencePitch=pitch;
        }
    }
    private final Deque<Pending> pending=new ArrayDeque<>();
    private final Deque<RecentError> recentFive=new ArrayDeque<>();
    private final long[] count=new long[3];
    private final double[] modelSq=new double[3],baselineSq=new double[3],velocitySq=new double[3],maxErr=new double[3];
    private Sample previous;
    private long observations,turning,impulses,gaps,inactive,invalid;
    private boolean enabled;
    private int logInterval=600;
    public void readConfiguration(Path file){
        enabled=false;logInterval=600;
        if(file==null||!Files.isRegularFile(file))return;
        try(InputStream stream=Files.newInputStream(file)){
            Properties prop=new Properties();prop.load(stream);
            enabled=Boolean.parseBoolean(prop.getProperty("enabled","false"));
            String raw=prop.getProperty("reportIntervalTicks","600");
            logInterval=Math.max(120,Math.min(72000,Integer.parseInt(raw.trim())));
        }catch(Exception ignored){enabled=false;logInterval=600;}
    }
    public void setEnabled(boolean value){enabled=value;}
    public boolean enabled(){return enabled;}
    public int logInterval(){return logInterval;}
    /** New world identity or disconnect: NEVER compare across worlds. */
    public void reset(){
        pending.clear();recentFive.clear();previous=null;observations=turning=impulses=gaps=inactive=invalid=0;
        java.util.Arrays.fill(count,0);java.util.Arrays.fill(modelSq,0);
        java.util.Arrays.fill(baselineSq,0);java.util.Arrays.fill(velocitySq,0);
        java.util.Arrays.fill(maxErr,0);
    }
    /** Called once per END_CLIENT_TICK. The tick is a client tick, never server time. */
    public void sample(Sample sample){
        if(!enabled)return;
        if(sample==null||!sample.finite()){
            invalid++;pending.clear();recentFive.clear();previous=null;return;
        }
        if(previous!=null&&sample.tick()<=previous.tick())return; // Duplicate or stale sample
        if(previous!=null&&sample.tick()!=previous.tick()+1){
            gaps++;pending.clear();recentFive.clear();
        }
        observations++;
        pruneRecent(sample.tick());
        if(!sample.gliding()||sample.collision()){
            inactive++;pending.clear();recentFive.clear();previous=sample;return;
        }
        if(previous==null||!previous.gliding()||previous.collision()){
            pending.clear();previous=sample;enqueue(sample);return;
        }
        double change=sample.velocity().minus(previous.velocity()).length();
        // Reject impulses unlike the v0.50 passive gliding recurrence; the
        // current client API does not tell us which impulse was a rocket.
        boolean impulse=change>VELOCITY_IMPULSE_LIMIT;
        if(impulse) recentFive.clear();
        for(Pending p:pending){
            if(Math.abs(CoreBallistics.wrap(sample.yaw()-p.referenceYaw))>CAMERA_TOLERANCE_DEGREES
                    ||Math.abs(sample.pitch()-p.referencePitch)>CAMERA_TOLERANCE_DEGREES)p.turning=true;
            if(impulse)p.impulse=true;
        }
        // Horizons from adjacent origin ticks interleave: do NOT assume that
        // the deque is sorted by due tick. Scan all (at most 18) pending tests.
        for(var it=pending.iterator();it.hasNext();){
            Pending p=it.next();
            if(p.due>sample.tick())continue;
            it.remove();
            if(p.due!=sample.tick()){
                gaps++;continue;
            }
            if(p.impulse){impulses++;continue;}
            if(p.turning){turning++;continue;}
            double modelError=p.predictedPosition.minus(sample.position()).length();
            double baselineError=p.linearBaseline.minus(sample.position()).length();
            double motionError=p.predictedVelocity.minus(sample.velocity()).length();
            if(!Double.isFinite(modelError)||!Double.isFinite(baselineError)||!Double.isFinite(motionError)){
                invalid++;continue;
            }
            count[p.index]++;
            modelSq[p.index]+=modelError*modelError;
            baselineSq[p.index]+=baselineError*baselineError;
            velocitySq[p.index]+=motionError*motionError;
            maxErr[p.index]=Math.max(maxErr[p.index],modelError);
            if(p.index==2){
                recentFive.addLast(new RecentError(sample.tick(),modelError*modelError,
                        baselineError*baselineError));
                pruneRecent(sample.tick());
            }
        }
        previous=sample;
        enqueue(sample);
    }
    private void enqueue(Sample sample){
        for(int i=0;i<HORIZONS.length;i++){
            int horizon=HORIZONS[i];
            GlideDynamicsV50.State estimate=GlideDynamicsV50.project(sample.position(),sample.velocity(),
                    sample.yaw(),sample.pitch(),horizon);
            if(estimate==null){invalid++;continue;}
            pending.addLast(new Pending(sample.tick()+horizon,i,estimate,
                    sample.position().add(sample.velocity().times(horizon)),sample.yaw(),sample.pitch()));
        }
        while(pending.size()>MAX_PENDING)pending.removeFirst();
    }
    private void pruneRecent(long nowTick){
        while(!recentFive.isEmpty() && (recentFive.size()>MAX_RECENT_FIVE ||
                nowTick-recentFive.peekFirst().dueTick()>RECENT_MAX_AGE))
            recentFive.removeFirst();
    }
    /** 5-tick held-out history without older or invalid flight segments.
     * Pure diagnostic; a result here cannot authorize a shot.
     */
    public RecentFive recentFive(long nowTick){
        if(nowTick<0)return new RecentFive(nowTick,0,Double.NaN,Double.NaN,Long.MIN_VALUE);
        int n=0;double model=0,linear=0;long last=Long.MIN_VALUE;
        for(RecentError item:recentFive){
            if(item.dueTick()>nowTick || nowTick-item.dueTick()>RECENT_MAX_AGE)continue;
            n++;model+=item.modelSquared();linear+=item.linearSquared();last=item.dueTick();
        }
        return new RecentFive(nowTick,n,n==0?Double.NaN:Math.sqrt(model/n),
                n==0?Double.NaN:Math.sqrt(linear/n),last);
    }
    public Summary summary(){
        HorizonStats[] stats=new HorizonStats[3];
        for(int i=0;i<3;i++){
            stats[i]=new HorizonStats(HORIZONS[i],count[i],count[i]>0?Math.sqrt(modelSq[i]/count[i]):Double.NaN,
                    count[i]>0?Math.sqrt(baselineSq[i]/count[i]):Double.NaN,
                    count[i]>0?Math.sqrt(velocitySq[i]/count[i]):Double.NaN,maxErr[i]);
        }
        return new Summary(observations,turning,impulses,gaps,inactive,invalid,stats);
    }
    /** Aggregated counts only; never logs player name, coordinates or target IDs. */
    public String report(){
        Summary s=summary();StringBuilder b=new StringBuilder("[LeadShot v0.50.1] エリトラ物理診断 (実射/命中判定には不使用): ");
        b.append("samples=").append(s.observations())
         .append(" camera-change-skip=").append(s.skippedTurning())
         .append(" impulse-skip=").append(s.skippedImpulses())
         .append(" gaps=").append(s.skippedGaps())
         .append(" non-gliding=").append(s.skippedInactive());
        for(HorizonStats stat:s.horizons())b.append(String.format(Locale.ROOT,
                " | %dt n=%d model-pos-RMS=%.4f CV-pos-RMS=%.4f vel-RMS=%.4f",
                stat.ticks(),stat.evaluated(),stat.modelPositionRms(),
                stat.baselinePositionRms(),stat.velocityRms()));
        return b.toString();
    }
}
