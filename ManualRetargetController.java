package dev.leadshot;

/** Distinguishes a player's camera turn from the steering performed by LeadShot.
 * A voluntary turn temporarily suspends steering and allows a centered contact
 * to replace the previous automatic target; explicit I/P/K locks are unaffected.
 * This is a local heuristic, not a raw-mouse input hook or a server signal.
 */
public final class ManualRetargetController {
    private long lastSampleTick=Long.MIN_VALUE;
    private double yaw,pitch;
    private double accumulated;
    private long manualUntil=Long.MIN_VALUE;

    public void reset(){lastSampleTick=Long.MIN_VALUE;accumulated=0;manualUntil=Long.MIN_VALUE;}

    /** Sample BEFORE this tick's camera steering. */
    public boolean observe(long tick,double currentYaw,double currentPitch){
        if(lastSampleTick!=Long.MIN_VALUE && tick==lastSampleTick+1 &&
                Double.isFinite(currentYaw) && Double.isFinite(currentPitch)){
            double drift=Math.hypot(CoreBallistics.wrap(currentYaw-yaw),currentPitch-pitch);
            // Tiny camera corrections are not intentional retargeting.
            if(drift>.33) accumulated=Math.min(12,accumulated+drift);
            else accumulated*=.7;
            if(accumulated>=1.55){manualUntil=tick+8;accumulated=0;}
        }else if(lastSampleTick!=tick){accumulated=0;}
        return manual(tick);
    }

    /** Record the FINAL angle after LeadShot has applied any automatic steering. */
    public void remember(long tick,double finalYaw,double finalPitch){
        if(!Double.isFinite(finalYaw)||!Double.isFinite(finalPitch)){reset();return;}
        lastSampleTick=tick;yaw=finalYaw;pitch=finalPitch;
    }
    public boolean manual(long tick){return manualUntil!=Long.MIN_VALUE && tick<=manualUntil;}

    /** Require a decisive centered replacement; proximity alone is insufficient. */
    public boolean switchTo(boolean manuallyTurning,boolean explicitlyLocked,
                            double proposedMiss,double previousMiss){
        return manuallyTurning && !explicitlyLocked && Double.isFinite(proposedMiss)
                && Double.isFinite(previousMiss) && proposedMiss<=2.8
                && proposedMiss+1.0<previousMiss;
    }
    public void switched(){manualUntil=Long.MIN_VALUE;accumulated=0;}
}
