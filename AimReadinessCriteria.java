package dev.leadshot;
/** Conservative geometric aim tolerance. An angular bound shrinks with range
 * and motion; readiness is NOT a hit probability or a guarantee. */
public final class AimReadinessCriteria {
    public record Bounds(double yawDegrees,double pitchDegrees){}
    public static Bounds bounds(double range,CoreBallistics.Vec half,double speed){
        if(!Double.isFinite(range)||range<=0||half==null||!half.finite()||
                !Double.isFinite(speed)||speed<0)return new Bounds(.16,.16);
        double horizontalRadius=Math.hypot(Math.abs(half.x()),Math.abs(half.z()));
        double verticalRadius=Math.abs(half.y());
        double penalty=1./(1.+Math.min(2.,speed)*.6);
        double yaw=Math.toDegrees(Math.atan2(horizontalRadius*.55,range))*penalty;
        double pitch=Math.toDegrees(Math.atan2(verticalRadius*.55,range))*penalty;
        return new Bounds(clamp(yaw,.16,.78),clamp(pitch,.16,.78));
    }
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private AimReadinessCriteria(){}
}
