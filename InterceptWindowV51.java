package dev.leadshot;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Experimental short-horizon fire-window planner. Never authorizes a shot:
 * the caller MUST also pass fresh legacy/curved contact, LOS, READY and user
 * intent through the existing release gate. This class can ONLY delay that
 * existing gate for a short bounded interval. No queued or synthetic click.
 *
 * Candidate shots are hypothetical and are discarded at the end of each tick.
 * They are NOT aimed ahead of time or sent to the server.
 */
public final class InterceptWindowV51 {
    public static final InterceptWindowV51 INSTANCE = new InterceptWindowV51();
    private static final double SPEED=CoreBallistics.FULL_BOW_SPEED;
    private static final double RADIUS=.12;
    private static final double MIN_GAIN_DEGREES=.65;
    private boolean enabled;
    private int maxHoldTicks=2;
    private UUID armedTarget;
    private EngagementProfileV511.Theater armedTheater;
    private long firstArmedTick=Long.MIN_VALUE, previousTick=Long.MIN_VALUE;
    private long holds, windows, bypasses;

    public record Decision(boolean permitExistingReleaseGate,int preferredWaitTicks,
                           int evaluatedCandidates,double presentScore,double bestFutureScore,
                           String reason) {}
    private record Candidate(int delay,double score,int evaluations) {}

    public synchronized boolean enabled(){return enabled;}
    public synchronized void setEnabled(boolean enabled){this.enabled=enabled;reset();}
    public synchronized void setMaxHoldTicks(int ticks){maxHoldTicks=Math.max(0,Math.min(3,ticks));reset();}
    public synchronized void readConfiguration(Path path){
        enabled=false;maxHoldTicks=2;reset();
        if(path==null||!Files.isRegularFile(path))return;
        try(InputStream in=Files.newInputStream(path)){
            Properties p=new Properties();p.load(in);
            enabled=Boolean.parseBoolean(p.getProperty("enabled","false"));
            maxHoldTicks=Math.max(0,Math.min(3,Integer.parseInt(p.getProperty("maxHoldTicks","2"))));
        }catch(Exception ignored){enabled=false;maxHoldTicks=2;}
    }
    public synchronized void reset(){armedTarget=null;armedTheater=null;firstArmedTick=previousTick=Long.MIN_VALUE;}
    public synchronized long holds(){return holds;}
    public synchronized long opportunities(){return windows;}
    public synchronized long bypasses(){return bypasses;}

    /**
     * Called immediately BEFORE existing RELEASE_GATE.allow(...). The current
     * shot has already been checked by the caller; future solutions do not
     * override current release eligibility. A temporal preference is only
     * advisory unless the player is actively holding both attack and use.
     */
    public synchronized Decision decide(UUID target,long tick,boolean heldIntent,
            boolean currentReady,boolean gliding,
            double cameraYaw,double cameraPitch,
            CoreBallistics.Vec origin,CoreBallistics.Vec inheritedVelocity,
            CoreBallistics.Solution presentShot,CoreBallistics.Vec targetCenter,
            CoreBallistics.Vec targetVelocity,CoreBallistics.Vec halfBox,
            List<CoreBallistics.Vec> curvedPath){
        return decide(target,tick,heldIntent,currentReady,gliding,cameraYaw,cameraPitch,
                origin,inheritedVelocity,presentShot,targetCenter,targetVelocity,halfBox,
                curvedPath,null);
    }

    /**
     * Four-theater overload. The policy only reduces speculative waits or
     * increases the required expected gain. The old API is retained for
     * compatibility with callers that do not have theater classification.
     */
    public synchronized Decision decide(UUID target,long tick,boolean heldIntent,
            boolean currentReady,boolean gliding,
            double cameraYaw,double cameraPitch,
            CoreBallistics.Vec origin,CoreBallistics.Vec inheritedVelocity,
            CoreBallistics.Solution presentShot,CoreBallistics.Vec targetCenter,
            CoreBallistics.Vec targetVelocity,CoreBallistics.Vec halfBox,
            List<CoreBallistics.Vec> curvedPath,
            EngagementProfileV511.Policy policy){
        if(!enabled||maxHoldTicks==0){reset();return pass("disabled");}
        if(policy!=null && !policy.allowsWaiting()){
            reset();bypasses++;return pass("theater:"+policy.theater()+":"+policy.reason());
        }
        if(!heldIntent||!currentReady){reset();return pass("not-armed-or-ready");}
        if(target==null||tick<0||origin==null||inheritedVelocity==null||presentShot==null
                ||targetCenter==null||targetVelocity==null||halfBox==null
                ||!origin.finite()||!inheritedVelocity.finite()||!targetCenter.finite()
                ||!targetVelocity.finite()||!halfBox.finite()
                ||halfBox.x()<0||halfBox.y()<0||halfBox.z()<0
                ||!Double.isFinite(cameraYaw)||!Double.isFinite(cameraPitch)
                ||!Double.isFinite(presentShot.yaw())||!Double.isFinite(presentShot.pitch())
                ||presentShot.ticks()<2||presentShot.ticks()>20){
            reset();bypasses++;return pass("invalid-input-or-range");
        }
        // A simple linear extrapolation of shooter position is not defensible
        // across several ticks of extremely fast gliding or rocket boosts.
        double speed=inheritedVelocity.length();
        if(!Double.isFinite(speed)||speed>3.0){reset();bypasses++;return pass("unknown-shooter-path");}
        EngagementProfileV511.Theater currentTheater=policy==null?null:policy.theater();
        if(armedTarget==null||!target.equals(armedTarget)||tick<=previousTick
                ||previousTick!=Long.MIN_VALUE&&tick-previousTick>1
                || armedTheater!=currentTheater){
            armedTarget=target;firstArmedTick=tick;armedTheater=currentTheater;
        }
        previousTick=tick;
        int theaterCap=policy==null?maxHoldTicks:policy.maximumWaitTicks();
        int allowedWait=Math.min(maxHoldTicks,theaterCap);
        if(tick-firstArmedTick>=allowedWait){windows++;return pass("maximum-wait-reached");}
        int maxDelay=Math.min(allowedWait,gliding?1:3);
        int minPathLength=Math.min(CoreBallistics.MAX_TICKS,maxDelay+presentShot.ticks()+4);
        List<CoreBallistics.Vec> path=path(curvedPath,targetCenter,targetVelocity,minPathLength);
        if(path==null){bypasses++;return pass("missing-target-path");}
        double immediate=angularError(presentShot,cameraYaw,cameraPitch)
                +Math.min(2,presentShot.speedMismatch()*2);
        if(!Double.isFinite(immediate)){bypasses++;return pass("invalid-present-angle");}
        Candidate best=null;
        int checks=0;
        // At most three future release offsets, each with a bounded angular
        // optimizer; no unbounded search, retained shot or simulated click.
        for(int delay=1;delay<=maxDelay;delay++){
            if(delay+presentShot.ticks()+1>=path.size())continue;
            CoreBallistics.Vec futureOrigin=origin.add(inheritedVelocity.times(delay));
            CoreBallistics.Vec futureCenter=path.get(delay);
            CoreBallistics.Vec futureVelocity=path.get(delay+1).minus(path.get(delay));
            List<CoreBallistics.Vec> futurePath=path.subList(delay,path.size());
            CoreBallistics.Solution seed=CoreBallistics.solveRefined(futureOrigin,
                    futureCenter,futureVelocity,inheritedVelocity,SPEED);
            checks++;
            if(seed==null||seed.ticks()<2||seed.ticks()>20
                    ||seed.speedMismatch()>Math.max(.18,SPEED*.1))continue;
            var fitted=CurvedAimOptimizerV492.optimize(futureOrigin,seed,
                    inheritedVelocity,futureCenter,futureVelocity,halfBox,
                    futurePath,SPEED,RADIUS);
            checks+=fitted.evaluations();
            if(!fitted.bothModelsHit())continue;
            CoreBallistics.Solution future=fitted.shot();
            double angular=angularError(future,cameraYaw,cameraPitch);
            // Prefer a current valid shot over uncertain future geometry.
            // The future must save at least 0.65 degrees after delay penalty.
            double score=angular+delay*.32+Math.min(2,future.speedMismatch()*2);
            if(Double.isFinite(score)&&(best==null||score<best.score()))
                best=new Candidate(delay,score,checks);
        }
        double requiredGain=policy==null?MIN_GAIN_DEGREES:
                Math.max(MIN_GAIN_DEGREES,policy.minimumGainDegrees());
        if(best==null||immediate-best.score()<requiredGain){
            windows++;return new Decision(true,0,checks,immediate,
                    best==null?Double.NaN:best.score(),"current-window-preferred");
        }
        holds++;
        return new Decision(false,best.delay(),checks,immediate,best.score(),"future-window-preferred");
    }

    private static double angularError(CoreBallistics.Solution shot,double yaw,double pitch){
        return Math.hypot(CoreBallistics.wrap(shot.yaw()-yaw),shot.pitch()-pitch);
    }
    private static List<CoreBallistics.Vec> path(List<CoreBallistics.Vec> predicted,
            CoreBallistics.Vec center,CoreBallistics.Vec velocity,int minimum){
        if(minimum<4||minimum>CoreBallistics.MAX_TICKS)return null;
        if(predicted!=null){
            if(predicted.size()<minimum+1||predicted.size()>CoreBallistics.MAX_TICKS+1)return null;
            for(var c:predicted)if(c==null||!c.finite())return null;
            return predicted;
        }
        var points=new ArrayList<CoreBallistics.Vec>(minimum+1);
        for(int t=0;t<=minimum;t++){
            var next=center.add(velocity.times(t));
            if(!next.finite())return null;
            points.add(next);
        }
        return points;
    }
    private static Decision pass(String reason){
        return new Decision(true,0,0,Double.NaN,Double.NaN,reason);
    }
}
