package dev.leadshot;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if(policy==null){
            // Older integrations keep their old conservative policy.
            policy=EngagementProfileV511.assess(gliding,false,inheritedVelocity,
                    targetVelocity,halfBox,presentShot.ticks(),curvedPath!=null);
        }
        var optimized=JointInterceptV512.evaluate(policy,gliding,cameraYaw,cameraPitch,
                origin,inheritedVelocity,presentShot,targetCenter,targetVelocity,
                halfBox,curvedPath,maxDelay);
        int checks=optimized.candidatesChecked();
        double present=optimized.presentScore();
        double future=optimized.bestFutureScore();
        double requiredGain=Math.max(MIN_GAIN_DEGREES,policy.minimumGainDegrees());
        if(optimized.bestWaitTicks()==0 || !Double.isFinite(present) ||
                !Double.isFinite(future) || present-future<requiredGain){
            windows++;
            return new Decision(true,0,checks,present,future,
                    "current-window-preferred:"+optimized.reason());
        }
        holds++;
        return new Decision(false,optimized.bestWaitTicks(),checks,present,future,
                "joint-future-window-preferred:"+policy.theater());
    }

    private static Decision pass(String reason){
        return new Decision(true,0,0,Double.NaN,Double.NaN,reason);
    }
}
