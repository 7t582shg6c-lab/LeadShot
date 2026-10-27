package dev.leadshot;

/**
 * Four-theater policy for the optional release-window optimizer.
 * All modes use the SAME legacy arrow, collision, LOS, READY and firing gate.
 * A policy can only limit speculative WAITING; it never authorizes release.
 * Airborne means gliding or off-ground according to the live caller's snapshot.
 * No gameplay server ticks, target intentions or future player input are known.
 */
public final class EngagementProfileV511 {
    private EngagementProfileV511() {}

    public enum Theater { GROUND_TO_GROUND, GROUND_TO_AIR, AIR_TO_GROUND, AIR_TO_AIR }

    public record Policy(Theater theater, int maximumWaitTicks, double minimumGainDegrees,
                         boolean curvedRequired, double relativeSpeed, String reason) {
        public Policy {
            if(theater==null || maximumWaitTicks<0 || maximumWaitTicks>3 ||
                    !Double.isFinite(minimumGainDegrees) || minimumGainDegrees<.65 ||
                    !Double.isFinite(relativeSpeed) || relativeSpeed<0 || reason==null)
                throw new IllegalArgumentException("invalid engagement policy");
        }
        public boolean allowsWaiting(){return maximumWaitTicks>0;}
    }

    /**
     * Generate a policy from CURRENT observed velocities, hitbox size, and
     * the present solution. Unknown input disables hypothetical waiting, not
     * current valid shots. No forecast result is used as a firing authority.
     */
    public static Policy assess(boolean shooterAirborne, boolean targetAirborne,
            CoreBallistics.Vec inheritedShooterVelocity, CoreBallistics.Vec targetVelocity,
            CoreBallistics.Vec targetHalf, int flightTicks, boolean curvedAvailable) {
        Theater theater = theater(shooterAirborne,targetAirborne);
        if(inheritedShooterVelocity==null || targetVelocity==null || targetHalf==null ||
                !inheritedShooterVelocity.finite() || !targetVelocity.finite() ||
                !targetHalf.finite() || targetHalf.x()<0 || targetHalf.y()<0 ||
                targetHalf.z()<0 || flightTicks<2 || flightTicks>20)
            return new Policy(theater,0,2.0,false,0,"invalid-or-out-of-range");
        double shooterSpeed=inheritedShooterVelocity.length();
        double targetSpeed=targetVelocity.length();
        double relative=targetVelocity.minus(inheritedShooterVelocity).length();
        if(!Double.isFinite(relative))
            return new Policy(theater,0,2.0,false,0,"invalid-relative-motion");

        // Delaying a shot is particularly uncertain when its future launch
        // point will be changed by gliding steering or a rocket boost.
        int wait;
        double gain;
        boolean requireCurve = targetAirborne && targetSpeed>.70;
        switch(theater){
            case GROUND_TO_GROUND -> {wait=2; gain=.65;}
            case GROUND_TO_AIR -> {wait=2; gain=.90;}
            case AIR_TO_GROUND -> {wait=1; gain=1.15;}
            case AIR_TO_AIR -> {wait=1; gain=1.45;}
            default -> throw new IllegalStateException();
        }
        if(shooterAirborne && shooterSpeed>3.0)
            return new Policy(theater,0,gain,requireCurve,relative,"fast-shooter-origin-uncertain");
        if(targetSpeed>3.5 || relative>4.0)
            return new Policy(theater,0,gain,requireCurve,relative,"fast-relative-motion");
        // A tiny hitbox cannot support a speculative wait of multiple ticks
        // unless the intermediate trajectories are very well constrained.
        double smallestHorizontal = Math.min(targetHalf.x(),targetHalf.z());
        if(smallestHorizontal<.24)wait=Math.min(wait,1);
        if(relative>1.5 || flightTicks>=16)wait=Math.min(wait,1);
        if(relative>2.8 && flightTicks>=10)wait=0;
        if(requireCurve && !curvedAvailable)
            return new Policy(theater,0,gain,true,relative,"high-speed-air-target-needs-curve");
        // The launch-point linear extrapolation becomes less useful when
        // the shooter is airborne even at ordinary velocity.
        if(shooterAirborne && shooterSpeed>2.2 && flightTicks>=14)wait=0;
        return new Policy(theater,wait,gain,requireCurve,relative,
                wait==0?"current-shot-preferred":"bounded-window");
    }

    public static Theater theater(boolean shooterAirborne, boolean targetAirborne) {
        if(shooterAirborne)return targetAirborne?Theater.AIR_TO_AIR:Theater.AIR_TO_GROUND;
        return targetAirborne?Theater.GROUND_TO_AIR:Theater.GROUND_TO_GROUND;
    }
}
