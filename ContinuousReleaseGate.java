package dev.leadshot;
/**
 * One release per bow draw cycle, even when ATTACK+USE are continuously held.
 * The caller must call rearm() whenever bow draw is not fully charged.
 * No input emulation or pending/queued shots. Read-only decision logic.
 */
public final class ContinuousReleaseGate {
    private boolean armed=true;
    private long lastTick=Long.MIN_VALUE;
    public void reset(){armed=true;lastTick=Long.MIN_VALUE;}
    public void rearm(){armed=true;}
    public boolean allow(long tick,boolean enabled,boolean bothHeld,boolean usingBow,
                         boolean fullCharge,boolean hostile,boolean clearRoute,boolean ready){
        if(!usingBow||!fullCharge){armed=true;return false;}
        if(!enabled||!bothHeld||!hostile||!clearRoute||!ready||!armed)return false;
        if(lastTick!=Long.MIN_VALUE && (tick<lastTick || tick-lastTick<20))return false;
        armed=false;
        lastTick=tick;
        return true;
    }
}
