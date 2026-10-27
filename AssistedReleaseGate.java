package dev.leadshot;

/** One-click opt-in bow release gate. Does not queue shots or simulate mouse presses. */
public final class AssistedReleaseGate {
    private long lastReleaseTick=Long.MIN_VALUE;
    public void reset(){lastReleaseTick=Long.MIN_VALUE;}
    public boolean allow(long tick,boolean middleClicked,boolean enabled,boolean manualLock,
                         boolean drawingBow,boolean rightHeld,boolean hostileTarget,
                         boolean unobstructed,boolean ready){
        if(!middleClicked||!enabled||!manualLock||!drawingBow||!rightHeld||
           !hostileTarget||!unobstructed||!ready)return false;
        if(lastReleaseTick!=Long.MIN_VALUE && (tick<lastReleaseTick||tick-lastReleaseTick<12))return false;
        lastReleaseTick=tick;
        return true;
    }
}
