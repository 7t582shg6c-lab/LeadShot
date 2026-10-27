package dev.leadshot;
import java.util.Collection;
import java.util.UUID;
/**
 * Keep the selected UUID if its live entity is present in the current range.
 * LOS is deliberately not considered here: walls must not steal a live lock.
 * Once killed/unloaded/out of range, the caller clears selection and can
 * immediately choose the next valid radar candidate on the same tick.
 */
public final class RadarLockHandoff {
    public static boolean aliveNearby(UUID selected,Collection<UUID> liveIds){
        return selected!=null && liveIds!=null && liveIds.contains(selected);
    }
    private RadarLockHandoff(){}
}
