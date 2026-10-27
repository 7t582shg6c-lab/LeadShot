package dev.leadshot;

/** Physics-aware shot origin and inherited shooter momentum.
 * In vanilla projectile launch logic the X/Z components of shooter velocity
 * are inherited; Y is inherited only while airborne. The arrow spawns roughly
 * 0.1 block below the player's eye. A small optional airborne future-origin
 * correction compensates for predicted motion before the server processes a
 * release. This correction is bounded, empirical and not server-authoritative.
 */
public final class BowFlightFrame {
    private BowFlightFrame(){}
    public record Frame(CoreBallistics.Vec origin,CoreBallistics.Vec inheritedVelocity,
                        double originLeadTicks){}
    public static Frame prepare(CoreBallistics.Vec eye,CoreBallistics.Vec velocity,
                                boolean onGround,double latencyTicks){
        if(eye==null||velocity==null||!eye.finite()||!velocity.finite())return null;
        double horizontal=Math.hypot(velocity.x(),velocity.z());
        // Only extrapolate a clearly fast, airborne shooter; avoid movement
        // extrapolation for normal walking/jumping and suppress large lag spikes.
        boolean fastAirborne=!onGround && Math.hypot(horizontal,velocity.y())>.65;
        double extra=fastAirborne ? Math.max(0,Math.min(.65,latencyTicks*.32)) : 0;
        CoreBallistics.Vec origin=eye.add(velocity.times(extra)).add(new CoreBallistics.Vec(0,-.10,0));
        CoreBallistics.Vec inherited=new CoreBallistics.Vec(
                velocity.x(),onGround?0:velocity.y(),velocity.z());
        return new Frame(origin,inherited,extra);
    }
}
