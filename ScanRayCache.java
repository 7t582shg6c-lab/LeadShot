package dev.leadshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Visibility memoization for one client tick. Never authorizes a shot using a
 * previous tick's LOS. This avoids duplicate raycasts for SCAN BOX, idle radar,
 * candidate discovery and target validation in the same client frame.
 */
public final class ScanRayCache {
    private final Map<UUID,Boolean> byEntity=new HashMap<>();
    private long epoch=Long.MIN_VALUE;
    public void reset(){epoch=Long.MIN_VALUE;byEntity.clear();}
    public boolean read(long tick,UUID target,BooleanSupplier evaluator){
        if(target==null||evaluator==null)return false;
        if(epoch!=tick){byEntity.clear();epoch=tick;}
        Boolean cached=byEntity.get(target);
        if(cached!=null)return cached;
        boolean answer=evaluator.getAsBoolean();
        byEntity.put(target,answer);
        return answer;
    }
}
