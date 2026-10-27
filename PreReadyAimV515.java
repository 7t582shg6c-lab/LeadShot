package dev.leadshot;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Opt-in early aiming advice for the selected target BEFORE READY.
 * This module only proposes a small change to the aiming GOAL. It does not
 * move the camera, select a target, mark a shot ready or release an item.
 * The caller must still pass the usual eligibility/LOS/current-contact checks
 * and then use AimAssistController and GlideSteeringV50 to limit rotation.
 */
public final class PreReadyAimV515 {
    public static final PreReadyAimV515 INSTANCE=new PreReadyAimV515();
    public record Goal(double yaw,double pitch,boolean applied,
                       EngagementProfileV511.Theater theater,String reason) {}
    private boolean enabled;
    private UUID lastTarget;
    private long lastTick=Long.MIN_VALUE;
    private double lastYawOffset,lastPitchOffset;
    private PreReadyAimV515() {}

    public synchronized void reset(){lastTarget=null;lastTick=Long.MIN_VALUE;
        lastYawOffset=lastPitchOffset=0;}
    public synchronized boolean enabled(){return enabled;}
    public synchronized void setEnabled(boolean active){enabled=active;reset();}
    /** Additional opt-in is independent of v0.51.4's READY-only future hints. */
    public synchronized void readConfiguration(Path file){
        enabled=false;reset();
        if(file==null||!Files.isRegularFile(file))return;
        try(InputStream in=Files.newInputStream(file)){
            Properties properties=new Properties();properties.load(in);
            enabled=Boolean.parseBoolean(properties.getProperty("enabled","false"));
        }catch(Exception ignored){enabled=false;}
    }

    /** Evaluate ONE bounded hypothetical release at t+1. The result is never
     * interpreted as a hit or a shot authorization. Returning applied=false
     * leaves the existing current-shot aiming direction unchanged.
     *
     * For curved target motion, its forecast is consumed only if the already
     * validated targetPath contains states at t=0,1,2; the curve's first step
     * defines velocity at the hypothetical release. Otherwise constant velocity
     * is assumed. All values are local client snapshots, not future facts.
     */
    public synchronized Goal guide(UUID target,long tick,boolean charging,
            boolean manualRetargeting,boolean gliding,boolean targetAirborne,
            boolean previousReady,double cameraYaw,double cameraPitch,
            CoreBallistics.Solution currentShot,CoreBallistics.Vec shotOrigin,
            CoreBallistics.Vec inheritedVelocity,CoreBallistics.Vec targetCenter,
            CoreBallistics.Vec targetVelocity,List<CoreBallistics.Vec> targetPath){
        EngagementProfileV511.Theater theater=
                EngagementProfileV511.theater(gliding,targetAirborne);
        Goal legacy=currentShot==null ? new Goal(cameraYaw,cameraPitch,false,theater,"no-shot")
                : new Goal(currentShot.yaw(),currentShot.pitch(),false,theater,"legacy");
        if(!enabled||!charging||manualRetargeting||previousReady||target==null){
            reset();return legacy;
        }
        if(tick<0||currentShot==null||shotOrigin==null||inheritedVelocity==null||
                targetCenter==null||targetVelocity==null||!shotOrigin.finite()||
                !inheritedVelocity.finite()||!targetCenter.finite()||!targetVelocity.finite()||
                !Double.isFinite(cameraYaw)||!Double.isFinite(cameraPitch)||
                !Double.isFinite(currentShot.yaw())||!Double.isFinite(currentShot.pitch())||
                currentShot.ticks()<2||currentShot.ticks()>30||
                !Double.isFinite(currentShot.speedMismatch())||currentShot.speedMismatch()>.30){
            reset();return legacy;
        }
        // An explicit lock change or manual replacement cannot inherit an old
        // steering correction. Re-sample the new target for one full client tick.
        if(!target.equals(lastTarget)||lastTick==Long.MIN_VALUE||tick!=lastTick+1){
            lastTarget=target;lastTick=tick;
            lastYawOffset=lastPitchOffset=0;
            return legacy;
        }
        lastTick=tick;
        double remainingAngle=Math.hypot(CoreBallistics.wrap(currentShot.yaw()-cameraYaw),
                currentShot.pitch()-cameraPitch);
        // The final READY cone is based on the CURRENT shot, not the future one.
        // Stop biasing the goal once the camera gets close to the live bearing.
        if(!Double.isFinite(remainingAngle)||remainingAngle<.55||remainingAngle>145){
            lastYawOffset=lastPitchOffset=0;
            return legacy;
        }
        double shooterSpeed=inheritedVelocity.length();
        if(shooterSpeed>(gliding?3.0:2.5)||targetVelocity.length()>4.5){
            lastYawOffset=lastPitchOffset=0;return legacy;
        }
        CoreBallistics.Vec futureOrigin=shotOrigin.add(inheritedVelocity);
        CoreBallistics.Vec futureInherited=inheritedVelocity;
        if(gliding && shooterSpeed>.65){
            GlideDynamicsV50.State flight=GlideDynamicsV50.project(shotOrigin,
                    inheritedVelocity,cameraYaw,cameraPitch,1);
            if(flight==null||!flight.position().finite()||!flight.velocity().finite()||
                    flight.velocity().length()>3.0){
                lastYawOffset=lastPitchOffset=0;return legacy;
            }
            futureOrigin=flight.position();futureInherited=flight.velocity();
        }
        CoreBallistics.Vec center1=targetCenter.add(targetVelocity);
        CoreBallistics.Vec velocity1=targetVelocity;
        if(targetPath!=null&&targetPath.size()>=3){
            CoreBallistics.Vec p0=targetPath.get(0),p1=targetPath.get(1),p2=targetPath.get(2);
            if(p0==null||p1==null||p2==null||!p0.finite()||!p1.finite()||!p2.finite()||
                    p0.minus(targetCenter).length()>1.5){
                lastYawOffset=lastPitchOffset=0;return legacy;
            }
            center1=p1;velocity1=p2.minus(p1);
            if(velocity1.length()>5){lastYawOffset=lastPitchOffset=0;return legacy;}
        }
        CoreBallistics.Solution estimated=CoreBallistics.solveRefined(futureOrigin,
                center1,velocity1,futureInherited,CoreBallistics.FULL_BOW_SPEED);
        if(estimated==null||!Double.isFinite(estimated.speedMismatch())||
                estimated.speedMismatch()>.30||estimated.ticks()<2||estimated.ticks()>30){
            lastYawOffset=lastPitchOffset=0;return legacy;
        }
        double deltaYaw=CoreBallistics.wrap(estimated.yaw()-currentShot.yaw());
        double deltaPitch=estimated.pitch()-currentShot.pitch();
        double angularOffset=Math.hypot(deltaYaw,deltaPitch);
        double maxDifference=gliding?1.05:theater==EngagementProfileV511.Theater.GROUND_TO_AIR?2.5:1.9;
        if(!Double.isFinite(angularOffset)||angularOffset<.025||angularOffset>maxDifference){
            lastYawOffset=lastPitchOffset=0;return legacy;
        }
        // Conservative four-theater guidance, measured in DEGREES per goal.
        double gain=switch(theater){
            case GROUND_TO_GROUND -> .25;
            case GROUND_TO_AIR -> .32;
            case AIR_TO_GROUND -> .18;
            case AIR_TO_AIR -> .15;
        };
        double maxOffset=gliding?.16:(targetAirborne?.38:.28);
        // Fade prelead continuously to zero near the live shot's READY cone.
        double fade=Math.min(1.0,Math.max(0,(remainingAngle-.55)/2.0));
        if(fade<=0)return legacy;
        double fraction=Math.min(gain,maxOffset/angularOffset)*fade;
        double rawYaw=deltaYaw*fraction,rawPitch=deltaPitch*fraction;
        // One-tick low-pass filtering prevents rapidly oscillating lead on
        // client-interpolated targets; changing targets always clears history.
        double filteredYaw=.65*rawYaw+.35*lastYawOffset;
        double filteredPitch=.65*rawPitch+.35*lastPitchOffset;
        lastYawOffset=filteredYaw;lastPitchOffset=filteredPitch;
        double yaw=CoreBallistics.wrap(currentShot.yaw()+filteredYaw);
        double pitch=currentShot.pitch()+filteredPitch;
        if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||Math.abs(pitch)>89.9){
            lastYawOffset=lastPitchOffset=0;return legacy;
        }
        return new Goal(yaw,pitch,true,theater,"pre-ready-one-tick-estimate");
    }
}
