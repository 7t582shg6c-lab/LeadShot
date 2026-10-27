package dev.leadshot;

/**
 * Strictly bounded view-angle steering. Pure math: never sends input or packets.
 * Invoked only for an explicitly locked, unobstructed bow target.
 */
public final class AimAssistController {
    public record Step(double yaw, double pitch, boolean moved, boolean aligned) {}
    public static Step step(double yaw,double pitch,double goalYaw,double goalPitch,
                            double maximumStep,boolean enabled){
        if(!enabled||!Double.isFinite(yaw)||!Double.isFinite(pitch)||
           !Double.isFinite(goalYaw)||!Double.isFinite(goalPitch)||
           !Double.isFinite(maximumStep)||maximumStep<=0){
            return new Step(yaw,pitch,false,false);
        }
        double dy=CoreBallistics.wrap(goalYaw-yaw);
        double dp=goalPitch-pitch;
        // A visible fast-moving flight target can need a turn far beyond the old 65° cut-off.
        // Never rotate the camera by a full half-turn for a target behind the player.
        if(Math.abs(dy)>145||Math.abs(dp)>85) return new Step(yaw,pitch,false,false);
        double length=Math.hypot(dy,dp);
        if(length<0.00001)return new Step(yaw,pitch,false,true);
        // Vector bound (rather than per-axis bound) preserves angular speed in diagonals.
        double scale=Math.min(1.,maximumStep/length);
        double ny=yaw+dy*scale;
        double np=Math.max(-90.,Math.min(90.,pitch+dp*scale));
        return new Step(ny,np,true,Math.hypot(CoreBallistics.wrap(goalYaw-ny),goalPitch-np)<.35);
    }
    private AimAssistController() {}
}
