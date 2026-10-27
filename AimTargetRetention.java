package dev.leadshot;

import java.util.UUID;

/** Maintain the selected shot while a player steers from target body to lead bearing. */
public final class AimTargetRetention {
    private AimTargetRetention() {}
    public static boolean active(boolean autoAim, boolean drawingBow){
        return autoAim && drawingBow;
    }
    public static boolean bypassCone(boolean active, UUID selected, UUID candidate){
        return active && selected!=null && selected.equals(candidate);
    }
    public static boolean holdPrevious(boolean active, boolean stillEligible){
        // Eligibility (alive, LOS, range, valid trajectory) is checked upstream.
        return active && stillEligible;
    }
}
