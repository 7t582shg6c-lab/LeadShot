package dev.leadshot;

/** Converts an inverse bow solution to the physically launched initial velocity.
 * An inverse solution may require, e.g. 2.88 or 3.12 blocks/tick. Vanilla fires
 * at exactly the charged speed in the solution direction (before random spread);
 * collision, occlusion, READY and HUD paths must use that same real velocity.
 */
public final class ActualBowShot {
    private ActualBowShot(){}
    public static CoreBallistics.Vec initialVelocity(CoreBallistics.Solution solution,
            CoreBallistics.Vec inheritedVelocity,double chargedSpeed){
        if(solution==null||inheritedVelocity==null||!inheritedVelocity.finite()
                ||!Double.isFinite(chargedSpeed)||chargedSpeed<=0)return null;
        CoreBallistics.Vec need=solution.launchVelocity();
        if(need==null||!need.finite())return null;
        double required=need.length();
        if(!Double.isFinite(required)||required<1e-9)return null;
        CoreBallistics.Vec actual=need.times(chargedSpeed/required).add(inheritedVelocity);
        return actual.finite()?actual:null;
    }
}
