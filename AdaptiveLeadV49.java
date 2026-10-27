package dev.leadshot;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Experimental, opt-in CV/CA/CT lead correction for ONE selected target.
 * Consumes the shared 64-target observation frame. Default OFF. Does not
 * independently authorize steering, READY, or firing. Only adjusts the
 * effective average target velocity used by the existing inverse solver.
 */
public final class AdaptiveLeadV49 {
    public static final AdaptiveLeadV49 INSTANCE=new AdaptiveLeadV49();
    public record Adjustment(CoreBallistics.Vec velocity, boolean applied, String reason,
            MotionForecastV481.Model model, double correctionBlocks) {}
    private final TrackRepositoryV481 tracks=new TrackRepositoryV481();
    private Object world;
    private long epoch=Long.MIN_VALUE;
    private boolean enabled;
    private long applied, deferred;
    public synchronized boolean enabled(){return enabled;}
    public synchronized void setEnabled(boolean on){enabled=on;}
    public synchronized void readConfiguration(Path path){
        enabled=false;
        if(path==null||!Files.isRegularFile(path))return;
        try(InputStream in=Files.newInputStream(path)){
            Properties p=new Properties();p.load(in);
            enabled=Boolean.parseBoolean(p.getProperty("enabled","false"));
        }catch(Exception ignored){enabled=false;}
    }
    public synchronized void resetWorld(Object nextWorld){
        if(world==nextWorld)return;
        world=nextWorld;epoch=Long.MIN_VALUE;applied=deferred=0;
        // A new world is not allowed to inherit per-target model skill.
        tracks.reset(-1);
    }
    /** Called once per client targeting tick, immediately after publishing
     * the immutable SharedObservationV484 frame. Never scans the world.
     */
    public synchronized boolean acceptFrame(Object currentWorld,ObservationBusV483.Frame frame){
        if(currentWorld==null||frame==null)return false;
        if(world!=currentWorld)resetWorld(currentWorld);
        if(epoch!=Long.MIN_VALUE && frame.epoch()<epoch)return false;
        boolean result=tracks.observeFrame(frame);
        if(result)epoch=frame.epoch();
        return result;
    }
    public synchronized TrackRepositoryV481.Summary summary(){return tracks.summary();}
    public synchronized Map<MotionForecastV481.Model,Integer> adaptiveCounts(int n){
        return tracks.adaptiveCounts(n);
    }
    public synchronized long appliedCount(){return applied;}
    public synchronized long deferredCount(){return deferred;}
    /** Returns a velocity suitable for CoreBallistics.solveRefined's legacy
     * constant-velocity interface. The nonlinear difference is averaged over
     * flight time; this is NOT a per-tick curved-AABB collision model.
     * Untrained, stale, distant and high-error predictions fall back to CV.
     */
    public synchronized Adjustment adjust(UUID id,long nowTick,int flightTicks,
            CoreBallistics.Vec observedCenter,CoreBallistics.Vec originalVelocity,
            CoreBallistics.Vec halfBox){
        if(originalVelocity==null||!originalVelocity.finite())return new Adjustment(
                originalVelocity,false,"invalid-baseline",MotionForecastV481.Model.CONSTANT_VELOCITY,0);
        if(!enabled)return fallback(originalVelocity,"disabled");
        if(id==null||observedCenter==null||halfBox==null||!observedCenter.finite()||!halfBox.finite()
                ||flightTicks<4||flightTicks>20)return fallback(originalVelocity,"range");
        TimedObservationV48 observation=tracks.observation(id);
        if(observation==null||observation.sampledClientTick()!=nowTick || observation.worldEpoch()!=epoch)
            return fallback(originalVelocity,"stale");
        if(observedCenter.minus(observation.center()).length()>1.5)
            return fallback(originalVelocity,"different-position");
        TrackRepositoryV481.AdaptiveForecast prediction=tracks.adaptiveForecast(id,flightTicks);
        if(prediction==null||!prediction.trained() ||prediction.model()==MotionForecastV481.Model.CONSTANT_VELOCITY)
            return fallback(originalVelocity,"untrained-or-cv");
        // Reject motion models with large held-out error at the matching horizon.
        double allowedRms=Math.max(.85,Math.min(2.5,halfBox.length()*1.8));
        if(!Double.isFinite(prediction.recentRms())||prediction.recentRms()>allowedRms)
            return fallback(originalVelocity,"poor-fit");
        MotionForecastV481.Estimate baseline=tracks.forecast(id,flightTicks,
                MotionForecastV481.Model.CONSTANT_VELOCITY);
        if(baseline==null||prediction.estimate()==null)
            return fallback(originalVelocity,"invalid-model");
        CoreBallistics.Vec extra=prediction.estimate().center().minus(baseline.center());
        // Time-averaged lead correction with conservative gain and max shift.
        double displacement=extra.length();
        if(!Double.isFinite(displacement)||displacement<.1)
            return fallback(originalVelocity,"negligible");
        double shift=Math.min(1.6,displacement*.45);
        CoreBallistics.Vec delta=extra.times(shift/displacement/flightTicks);
        CoreBallistics.Vec effective=originalVelocity.add(delta);
        if(!effective.finite())return fallback(originalVelocity,"nonfinite");
        applied++;
        return new Adjustment(effective,true,"applied",prediction.model(),shift);
    }
    /** Builds an immutable per-tick center path for the selected target, in the
     * SAME coordinate frame as the legacy center and CV velocity. At the lead
     * horizon the nonlinear offset matches the correction used in solveRefined;
     * intermediate points retain the model's original curvature. This is not
     * a prediction of Minecraft server packets or a claim of hit probability.
     * Null means NO audit can be trusted (fail closed, never authorize fire).
     */
    public synchronized java.util.List<CoreBallistics.Vec> curvedPath(UUID id,long nowTick,
            int leadHorizon,int traceTicks,CoreBallistics.Vec legacyCenter,
            CoreBallistics.Vec legacyVelocity,Adjustment adjustment){
        if(!enabled||id==null||adjustment==null||!adjustment.applied()
                ||legacyCenter==null||legacyVelocity==null||!legacyCenter.finite()
                ||!legacyVelocity.finite()||leadHorizon<4||leadHorizon>20
                ||traceTicks<1||traceTicks>CoreBallistics.MAX_TICKS)return null;
        TimedObservationV48 observation=tracks.observation(id);
        if(observation==null||observation.sampledClientTick()!=nowTick
                ||observation.worldEpoch()!=epoch)return null;
        var model=adjustment.model();
        if(model==null||model==MotionForecastV481.Model.CONSTANT_VELOCITY)return null;
        MotionForecastV481.Estimate selectedAt=tracks.forecast(id,leadHorizon,model);
        MotionForecastV481.Estimate linearAt=tracks.forecast(id,leadHorizon,
                MotionForecastV481.Model.CONSTANT_VELOCITY);
        if(selectedAt==null||linearAt==null)return null;
        CoreBallistics.Vec modelLead=selectedAt.center().minus(linearAt.center());
        double displacement=modelLead.length();
        if(!Double.isFinite(displacement)||displacement<.1)return null;
        // The v0.49 lead limiter uses correctionBlocks / model displacement.
        // Reproduce exactly the correction that was used to aim this shot.
        double gain=adjustment.correctionBlocks()/displacement;
        if(!Double.isFinite(gain)||gain<0||gain>1.01)return null;
        var points=new java.util.ArrayList<CoreBallistics.Vec>(traceTicks+1);
        for(int t=0;t<=traceTicks;t++){
            var predicted=tracks.forecast(id,t,model);
            var constant=tracks.forecast(id,t,MotionForecastV481.Model.CONSTANT_VELOCITY);
            if(predicted==null||constant==null)return null;
            CoreBallistics.Vec offset=predicted.center().minus(constant.center()).times(gain);
            double distance=offset.length();
            // Limit extrapolation past the chosen lead horizon as well.
            if(distance>1.6)offset=offset.times(1.6/distance);
            CoreBallistics.Vec center=legacyCenter.add(legacyVelocity.times(t)).add(offset);
            if(!center.finite())return null;
            points.add(center);
        }
        return java.util.List.copyOf(points);
    }

    private Adjustment fallback(CoreBallistics.Vec old,String reason){
        deferred++;
        return new Adjustment(old,false,reason,
                MotionForecastV481.Model.CONSTANT_VELOCITY,0);
    }
}
