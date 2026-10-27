package dev.leadshot;

/** Small bounded, deterministic target-motion hypotheses for offline evaluation.
 * These are not server-authoritative and MUST NOT independently allow shooting.
 */
public final class MotionForecastV481 {
    private MotionForecastV481() {}
    public enum Model { CONSTANT_VELOCITY, DAMPED_ACCELERATION, DAMPED_TURN }
    public record Estimate(CoreBallistics.Vec center, CoreBallistics.Vec velocity,
                           double observedAcceleration, double turnRadians) {}

    public static Estimate estimate(CoreBallistics.Vec p, CoreBallistics.Vec v,
                                    CoreBallistics.Vec previousVelocity, int horizon, Model model) {
        if (p == null || v == null || !p.finite() || !v.finite() ||
                horizon < 0 || horizon > 80 || model == null) return null;
        if (previousVelocity == null || !previousVelocity.finite()) previousVelocity = v;
        CoreBallistics.Vec delta = v.minus(previousVelocity);
        double acceleration = delta.length();
        if (!Double.isFinite(acceleration)) return null;
        CoreBallistics.Vec limited = delta.times(acceleration > .35 ? .35 / acceleration : 1);
        double cross = previousVelocity.x() * v.z() - previousVelocity.z() * v.x();
        double dot = previousVelocity.x() * v.x() + previousVelocity.z() * v.z();
        double turn = (Math.hypot(previousVelocity.x(), previousVelocity.z()) > .15 &&
                Math.hypot(v.x(), v.z()) > .15) ? Math.atan2(cross, dot) : 0;
        turn = Math.max(-.30, Math.min(.30, turn));
        CoreBallistics.Vec pos = p, velocity = v;
        for (int t = 0; t < horizon; t++) {
            pos = pos.add(velocity);
            if (model == Model.DAMPED_ACCELERATION) {
                // Damp near-instant acceleration so it cannot grow forever.
                velocity = velocity.add(limited.times(Math.pow(.78, t + 1)));
            } else if (model == Model.DAMPED_TURN) {
                double a = turn * Math.pow(.88, t + 1);
                double cs = Math.cos(a), sn = Math.sin(a);
                velocity = new CoreBallistics.Vec(
                        velocity.x() * cs - velocity.z() * sn,
                        velocity.y() + Math.max(-.22, Math.min(.22, limited.y())) * Math.pow(.75, t + 1),
                        velocity.x() * sn + velocity.z() * cs);
            }
            // Bound runaway acceleration from inconsistent network observations.
            double length = velocity.length();
            if (length > 14) velocity = velocity.times(14 / length);
        }
        return pos.finite() && velocity.finite() ? new Estimate(pos, velocity, acceleration, turn) : null;
    }
}
