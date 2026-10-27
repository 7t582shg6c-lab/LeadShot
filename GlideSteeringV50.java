package dev.leadshot;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Opt-in, flight-coupled view-rate governor. Default OFF, fail closed.
 * Does not alter vanilla Elytra physics, target selection or manual mouse input.
 */
public final class GlideSteeringV50 {
    public static final GlideSteeringV50 INSTANCE=new GlideSteeringV50();
    public record Decision(double yaw,double pitch,boolean active,boolean limited,
            boolean fireStable,double displacementDelta,String reason) {}
    private boolean enabled;
    private boolean useMeasuredConfidence;
    private GlideReliabilityV502.Assessment measured=GlideReliabilityV502.missing();
    private long confidenceTick=Long.MIN_VALUE;
    public synchronized boolean measuredGuardEnabled(){return useMeasuredConfidence;}
    public synchronized void setMeasuredGuardEnabled(boolean value){useMeasuredConfidence=value;}
    public synchronized void clearMeasuredConfidence(){
        measured=GlideReliabilityV502.missing();confidenceTick=Long.MIN_VALUE;
    }
    /** Called from the independent observer, not by target selection or firing. */
    public synchronized void acceptMeasuredConfidence(GlideCalibrationV501.RecentFive recent,long now){
        if(recent==null||now<0||now<confidenceTick)return;
        measured=GlideReliabilityV502.evaluate(recent,now);
        confidenceTick=now;
    }
    public synchronized GlideReliabilityV502.Assessment measuredConfidence(){return measured;}

    public synchronized boolean enabled(){return enabled;}
    public synchronized void setEnabled(boolean value){enabled=value;}
    public synchronized void readConfiguration(Path file){
        enabled=false;useMeasuredConfidence=false;clearMeasuredConfidence();
        if(file==null||!Files.isRegularFile(file))return;
        try(InputStream in=Files.newInputStream(file)){
            var props=new Properties();props.load(in);
            enabled=Boolean.parseBoolean(props.getProperty("enabled","false"));
            useMeasuredConfidence=Boolean.parseBoolean(props.getProperty("useMeasuredConfidence","false"));
        }catch(Exception ignored){enabled=false;}
    }
    /**
     * Called AFTER the legacy aim-rate limiter, BEFORE changing camera rotation.
     * Never adjusts yaw/pitch if disabled or if the shooter is not gliding.
     * Collision flag is an immediate gate only; future collision prediction is
     * not possible from this motion-only model (no world geometry input).
     */
    public synchronized Decision regulate(double yaw,double pitch,double requestedYaw,
            double requestedPitch,CoreBallistics.Vec position,CoreBallistics.Vec velocity,
            boolean gliding,boolean horizontalCollision){
        if(!enabled||!gliding)return new Decision(requestedYaw,requestedPitch,false,false,true,0,"off-or-ground");
        if(position==null||velocity==null||!position.finite()||!velocity.finite()
                ||!Double.isFinite(yaw)||!Double.isFinite(pitch)
                ||!Double.isFinite(requestedYaw)||!Double.isFinite(requestedPitch))
            return new Decision(yaw,pitch,true,true,false,Double.NaN,"invalid-motion");
        if(horizontalCollision)return new Decision(yaw,pitch,true,true,false,0,"current-collision");
        double speed=velocity.length();
        if(!Double.isFinite(speed)||speed>12)
            return new Decision(yaw,pitch,true,true,false,Double.NaN,"unmodelled-velocity");
        // Strict bounds on ONE game-tick's automatic camera movement.
        // Opt-in, evidence-based protective degradation. The unavailable and
        // stale cases preserve the old v0.50 behavior to avoid blocking normal
        // gliding indefinitely when the player turns frequently.
        boolean poorEvidence=useMeasuredConfidence
                && measured.state()==GlideReliabilityV502.State.POOR_FIT;
        double evidenceGain=poorEvidence ? .55 : 1.0;
        double yawLimit=Math.max(1.25,5.0/(1.0+speed*.28))*evidenceGain;
        double pitchLimit=Math.max(.85,2.8/(1.0+speed*.22))*evidenceGain;
        double dy=CoreBallistics.wrap(requestedYaw-yaw);
        double dp=requestedPitch-pitch;
        double nextYaw=yaw+clamp(dy,-yawLimit,yawLimit);
        double nextPitch=clamp(pitch+clamp(dp,-pitchLimit,pitchLimit),-65,65);
        double actualDeltaYaw=CoreBallistics.wrap(nextYaw-yaw);
        double actualDeltaPitch=nextPitch-pitch;
        boolean limited=Math.abs(actualDeltaYaw-dy)>.01||Math.abs(actualDeltaPitch-dp)>.01;
        var baseline=GlideDynamicsV50.project(position,velocity,yaw,pitch,5);
        if(baseline==null)return new Decision(yaw,pitch,true,true,false,Double.NaN,"model-invalid");
        var candidate=GlideDynamicsV50.project(position,velocity,nextYaw,nextPitch,5);
        if(candidate==null)return new Decision(yaw,pitch,true,true,false,Double.NaN,"candidate-invalid");
        double drift=candidate.position().minus(baseline.position()).length();
        // Approximate 5-tick delta against continuing the CURRENT view direction.
        // Nonlinear model is only a steering guard; it is NOT flight autopilot.
        final double driftLimit=poorEvidence?.45:.95;
        if(drift>driftLimit){
            // Monotonic local search for a smaller change. The short-horizon
            // movement model is approximate; limit rather than extrapolate.
            for(int i=0;i<8 && drift>driftLimit;i++){
                actualDeltaYaw*=.65;
                actualDeltaPitch*=.65;
                nextYaw=yaw+actualDeltaYaw;
                nextPitch=pitch+actualDeltaPitch;
                candidate=GlideDynamicsV50.project(position,velocity,nextYaw,nextPitch,5);
                if(candidate==null)return new Decision(yaw,pitch,true,true,false,Double.NaN,"candidate-invalid");
                drift=candidate.position().minus(baseline.position()).length();
            }
            limited=true;
        }
        // A large nose-down angle or steep current drop rate is treated as
        // uncertain. This does not inspect terrain; it prevents automatic fire.
        boolean unstable=(nextPitch>54 && speed>1.3) || velocity.y() < -2.8
                || drift>driftLimit+.00001;
        boolean settled=!limited && !unstable && !poorEvidence;
        return new Decision(CoreBallistics.wrap(nextYaw),nextPitch,true,limited,settled,
                drift,poorEvidence?"measured-poor-fit":unstable?"flight-unstable":limited?"rate-or-drift-limit":"stable");
    }
    private static double clamp(double a,double lo,double hi){return Math.max(lo,Math.min(hi,a));}
}
