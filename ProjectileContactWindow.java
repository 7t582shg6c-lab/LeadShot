package dev.leadshot;

/**
 * Minecraft-independent, continuous relative-segment / moving AABB collision audit.
 * It is only a broad mathematical prediction for a constant-velocity target and an
 * approximate projectile (gravity=0.05, drag=0.99). The real game/server may differ.
 * Does NOT sample world blocks, test server authority, or steer/fire the player.
 */
public final class ProjectileContactWindow {
    private ProjectileContactWindow() { }

    public record Vec(double x, double y, double z) {
        public Vec add(Vec b) { return new Vec(x+b.x, y+b.y, z+b.z); }
        public Vec subtract(Vec b) { return new Vec(x-b.x, y-b.y, z-b.z); }
        public Vec scale(double k) { return new Vec(x*k, y*k, z*k); }
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Window(boolean intersects, double first, double last) {
        public static Window miss() { return new Window(false, Double.NaN, Double.NaN); }
    }
    public record Contact(boolean intersects, double tick, Vec projectilePoint) {
        public static Contact miss() { return new Contact(false, Double.NaN, null); }
    }

    /**
     * Arrow p0->p1 and target center t0->t1 are observed/simulated over the same
     * one-tick interval. The target has a fixed half-size and an additional
     * conservative arrow radius. Return the full fractional-tick contact window.
     */
    public static Window relativeSegment(Vec p0, Vec p1, Vec t0, Vec t1,
                                         Vec halfSize, double arrowRadius) {
        if (!valid(p0, p1, t0, t1, halfSize, arrowRadius)) return Window.miss();
        Vec a = p0.subtract(t0), b = p1.subtract(t1), delta = b.subtract(a);
        if (!a.finite() || !delta.finite()) return Window.miss();
        double[] min = { -halfSize.x()-arrowRadius, -halfSize.y()-arrowRadius,
                         -halfSize.z()-arrowRadius };
        double[] max = {  halfSize.x()+arrowRadius,  halfSize.y()+arrowRadius,
                          halfSize.z()+arrowRadius };
        double[] origin = { a.x(), a.y(), a.z() };
        double[] velocity = { delta.x(), delta.y(), delta.z() };
        double enter = 0.0, exit = 1.0;
        for (int axis=0; axis<3; axis++) {
            double o = origin[axis], d = velocity[axis];
            if (Math.abs(d) < 1e-12) {
                if (o < min[axis] || o > max[axis]) return Window.miss();
                continue;
            }
            double n = (min[axis] - o) / d, f = (max[axis] - o) / d;
            if (n > f) { double tmp=n; n=f; f=tmp; }
            enter = Math.max(enter, n);
            exit = Math.min(exit, f);
            if (enter > exit) return Window.miss();
        }
        if (!Double.isFinite(enter) || !Double.isFinite(exit)) return Window.miss();
        return new Window(true, Math.max(0, enter), Math.min(1, exit));
    }

    /**
     * Audit a trajectory no more than 120 ticks into the future. targetVelocity
     * is per game tick. No claim of a clear path is made; blocks are NOT checked.
     */
    public static Contact earliest(Vec launch, Vec velocity, Vec targetCenter,
                                   Vec targetVelocity, Vec targetHalfSize,
                                   double arrowRadius, int maxTicks) {
        if (!valid(launch, velocity, targetCenter, targetVelocity, targetHalfSize, arrowRadius)
                || maxTicks < 1 || maxTicks > 120) return Contact.miss();
        Vec p0 = launch, vel = velocity, t0 = targetCenter;
        for (int i=0; i<maxTicks; i++) {
            Vec p1 = p0.add(vel);
            Vec t1 = t0.add(targetVelocity);
            if (!p1.finite() || !t1.finite()) return Contact.miss();
            Window interval = relativeSegment(p0, p1, t0, t1, targetHalfSize, arrowRadius);
            if (interval.intersects()) {
                Vec intersection = p0.add(p1.subtract(p0).scale(interval.first()));
                return new Contact(true, i + interval.first(), intersection);
            }
            // Mirrors the original LeadShot simple-model integration order.
            vel = new Vec(vel.x()*.99, vel.y()*.99-.05, vel.z()*.99);
            if (!vel.finite()) return Contact.miss();
            p0 = p1;
            t0 = t1;
        }
        return Contact.miss();
    }

    private static boolean valid(Vec p0, Vec p1, Vec t0, Vec t1, Vec half, double arrowRadius) {
        return p0 != null && p1 != null && t0 != null && t1 != null && half != null
                && p0.finite() && p1.finite() && t0.finite() && t1.finite() && half.finite()
                && half.x() > 0 && half.y() > 0 && half.z() > 0
                && half.x() <= 100 && half.y() <= 100 && half.z() <= 100
                && Double.isFinite(arrowRadius) && arrowRadius >= 0 && arrowRadius <= 2;
    }
}
