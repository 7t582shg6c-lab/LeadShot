package dev.leadshot;

import java.util.UUID;

/**
 * Render-frame smoothing of WORLD-space aim, future target point and trajectory.
 * The live camera is intentionally never smoothed: mouse motion must be responsive.
 *
 * Frame parameters are immutable tick samples; render frames approach them with
 * an elapsed-time exponential filter, not with a fixed alpha per FPS.
 * Settings: 0=OFF, 1=FAST (35ms), 2=BALANCED (70ms), 3=SMOOTH (110ms).
 * This improves visual continuity at >20 FPS; it does not improve prediction accuracy.
 */
public final class GuideFrameSmoother {
    private static final long RESUME_GAP_NS = 350_000_000L;
    private static final long MAX_FRAME_DT_NS = 75_000_000L;
    public record Frame(double yaw, double pitch, CoreBallistics.Vec future,
                        CoreBallistics.Vec eye, CoreBallistics.Vec velocity, int ticks) {
        public boolean finite() {
            return Double.isFinite(yaw) && Double.isFinite(pitch)
                    && future != null && future.finite() && eye != null && eye.finite()
                    && velocity != null && velocity.finite() && ticks >= 1 && ticks <= 120;
        }
    }
    private UUID targetId;
    private Frame target;
    private Frame current;
    private long renderNanos = Long.MIN_VALUE;

    public void reset() {
        targetId = null;
        target = null;
        current = null;
        renderNanos = Long.MIN_VALUE;
    }

    /** Update desired guide once per tick. Different targets NEVER interpolate together. */
    public void observe(UUID id, Frame next) {
        if (id == null || next == null || !next.finite()) { reset(); return; }
        if (!id.equals(targetId) || target == null || current == null) {
            targetId = id;
            target = next;
            current = next; // snap immediately on first acquisition and lock switch
            renderNanos = Long.MIN_VALUE;
            return;
        }
        // Same UUID can teleport or be re-positioned by chunk correction.
        // Large discontinuities must snap instead of sweeping the cue through walls.
        if (next.future().minus(target.future()).length() > 12.0
                || next.eye().minus(target.eye()).length() > 8.0
                || Math.abs(CoreBallistics.wrap(next.yaw() - target.yaw())) > 75.0) {
            target = next;
            current = next;
            renderNanos = Long.MIN_VALUE;
            return;
        }
        target = next;
    }

    /** Called every HUD render frame with a monotonic nanosecond timestamp. */
    public Frame sample(long nowNanos, int setting) {
        if (target == null) return null;
        if (setting <= 0) {
            current = target;
            renderNanos = nowNanos;
            return current;
        }
        if (renderNanos == Long.MIN_VALUE || nowNanos <= renderNanos
                || nowNanos - renderNanos > RESUME_GAP_NS) {
            // Initial draw, paused window, tick/time discontinuity: no lingering ghost.
            renderNanos = nowNanos;
            current = target; // no after-pause ghosting
            return current;
        }
        long delta = Math.min(MAX_FRAME_DT_NS, nowNanos - renderNanos);
        renderNanos = nowNanos;
        double tau = switch (setting) {
            case 1 -> 0.035;
            case 2 -> 0.070;
            default -> 0.110;
        };
        double alpha = -Math.expm1(-delta * 1.0e-9 / tau);
        if (current == null || !current.finite()) { current = target; return current; }
        double yaw = wrapLerp(current.yaw(), target.yaw(), alpha);
        double pitch = lerp(current.pitch(), target.pitch(), alpha);
        current = new Frame(yaw, pitch,
                blend(current.future(), target.future(), alpha),
                blend(current.eye(), target.eye(), alpha),
                blend(current.velocity(), target.velocity(), alpha),
                target.ticks());
        return current;
    }
    public UUID targetId() { return targetId; }
    static double wrapLerp(double previous, double next, double alpha) {
        return previous + CoreBallistics.wrap(next - previous) * alpha;
    }
    private static double lerp(double a, double b, double alpha) { return a + (b - a)*alpha; }
    private static CoreBallistics.Vec blend(CoreBallistics.Vec a, CoreBallistics.Vec b, double t) {
        return new CoreBallistics.Vec(lerp(a.x(),b.x(),t),lerp(a.y(),b.y(),t),lerp(a.z(),b.z(),t));
    }
}
