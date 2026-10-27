package dev.leadshot;

/**
 * Tests successive one-tick sections of the predicted arrow trajectory.
 * A clear state is only returned when every section up to the requested
 * contact tick was inspected and was clear. Incomplete work is UNCHECKED.
 *
 * This is block-only geometry. It cannot promise that a server-side arrow
 * will hit the target; fluids, other moving entities, spread and lag remain.
 */
public final class BallisticOcclusion {
    private BallisticOcclusion() {}
    public static final int MAX_RAYS = 48;
    public enum State { BLOCKED, CLEAR, UNCHECKED, INVALID }
    public enum ProbeResult { BLOCKED, CLEAR, UNLOADED }

    /** Implement on the client with vanilla's block ray collision. */
    @FunctionalInterface public interface SegmentProbe {
        ProbeResult check(CoreBallistics.Vec start, CoreBallistics.Vec end);
    }

    public record Result(State state, int testedSegments, int requestedSegments, double firstBlockedTick) {
        public boolean complete() { return state == State.BLOCKED || state == State.CLEAR; }
    }

    /**
     * Evaluate at most 48 discrete trajectory sections. Checks cannot run
     * past the requested target contact tick. A budget overrun is UNCHECKED.
     */
    public static Result evaluate(CoreBallistics.Vec origin,
            CoreBallistics.Vec initialVelocity, double contactTick, SegmentProbe probe) {
        if (origin == null || initialVelocity == null || !origin.finite() || !initialVelocity.finite()
                || !Double.isFinite(contactTick) || contactTick < 0 || contactTick > 120 || probe == null)
            return new Result(State.INVALID, 0, 0, Double.NaN);
        int wholeTicks = (int) Math.ceil(contactTick);
        if (wholeTicks == 0) return new Result(State.CLEAR, 0, 0, Double.NaN);
        CoreBallistics.Vec pos = origin, velocity = initialVelocity;
        int budget = Math.min(wholeTicks, MAX_RAYS);
        for (int i=0; i<budget; i++) {
            double fraction = Math.min(1.0, contactTick - i);
            // Conservative velocity interpolation within the terminal partial tick.
            CoreBallistics.Vec next = pos.add(velocity.times(fraction));
            if (!next.finite()) return new Result(State.INVALID, i, wholeTicks, Double.NaN);
            ProbeResult outcome;
            try { outcome = probe.check(pos, next); }
            catch (RuntimeException failure) {
                // An incomplete or unexpected client level is never treated as clear.
                return new Result(State.UNCHECKED, i, wholeTicks, Double.NaN);
            }
            if (outcome == ProbeResult.BLOCKED)
                return new Result(State.BLOCKED, i + 1, wholeTicks, i + fraction);
            if (outcome != ProbeResult.CLEAR)
                return new Result(State.UNCHECKED, i + 1, wholeTicks, Double.NaN);
            pos = pos.add(velocity);
            velocity = new CoreBallistics.Vec(
                    velocity.x() * CoreBallistics.DRAG,
                    velocity.y() * CoreBallistics.DRAG - CoreBallistics.GRAVITY,
                    velocity.z() * CoreBallistics.DRAG);
        }
        return new Result(wholeTicks > MAX_RAYS ? State.UNCHECKED : State.CLEAR,
                budget, wholeTicks, Double.NaN);
    }
}
