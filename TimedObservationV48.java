package dev.leadshot;

import java.util.UUID;

/** Time-stamped, client-visible entity state. Never pretends to know the server tick. */
public record TimedObservationV48(UUID entityId, long worldEpoch, long sampledClientTick,
                                   CoreBallistics.Vec center, CoreBallistics.Vec velocity,
                                   CoreBallistics.Vec halfBox, boolean airborne) {
    public TimedObservationV48 {
        if (entityId == null || worldEpoch < 0 || sampledClientTick < 0 ||
                center == null || velocity == null || halfBox == null ||
                !center.finite() || !velocity.finite() || !halfBox.finite() ||
                halfBox.x() < 0 || halfBox.y() < 0 || halfBox.z() < 0) {
            throw new IllegalArgumentException("Invalid client observation");
        }
    }
    /** Number of elapsed local client ticks, never a claimed server age. */
    public long localAge(long nowClientTick) {
        return nowClientTick < sampledClientTick ? Long.MAX_VALUE : nowClientTick - sampledClientTick;
    }
    public boolean current(long world, long nowClientTick, int maximumAge) {
        return world == worldEpoch && maximumAge >= 0 && localAge(nowClientTick) <= maximumAge;
    }
    /** Explicitly bounded CV extrapolation; callers must reject stale observations. */
    public CoreBallistics.Vec predictedCenter(double horizonTicks) {
        if (!Double.isFinite(horizonTicks) || horizonTicks < 0 || horizonTicks > 80) return null;
        return center.add(velocity.times(horizonTicks));
    }
}
