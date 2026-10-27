package dev.leadshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client tick-only, bounded memoization of FULL-DRAW solutions, never READY decisions.
 * A cached solution is only a candidate hint; the chosen target is always freshly solved.
 */
public final class FullDrawWarmCache {
    private record Entry(long tick, CoreBallistics.Vec eye, CoreBallistics.Vec target,
                         CoreBallistics.Vec velocity, CoreBallistics.Vec shooterVelocity,
                         CoreBallistics.Solution solution) {}
    private final Map<UUID,Entry> entries=new HashMap<>();
    private long hits, misses, solves;
    public void clear(){entries.clear();hits=misses=solves=0;}
    public void retain(Set<UUID> ids){entries.keySet().retainAll(ids);}
    public int size(){return entries.size();}
    public long hits(){return hits;}
    public long misses(){return misses;}
    public long solves(){return solves;}
    private static double distance(CoreBallistics.Vec a,CoreBallistics.Vec b){return a.minus(b).length();}
    private CoreBallistics.Solution valid(UUID id,long tick,CoreBallistics.Vec eye,
                     CoreBallistics.Vec target,CoreBallistics.Vec velocity,CoreBallistics.Vec shooterVelocity,
                     int ttl){
        Entry e=entries.get(id);
        if(e==null || tick<e.tick() || tick-e.tick()>ttl || !target.finite() || !eye.finite() ||
                !velocity.finite() || !shooterVelocity.finite())return null;
        // Reject stale bearings and altered motion; recompute rather than pretending accuracy.
        if(distance(eye,e.eye())>.28 || distance(target,e.target())>.22 ||
                distance(velocity,e.velocity())>.045 || distance(shooterVelocity,e.shooterVelocity())>.08)return null;
        return e.solution();
    }
    /** Returns cached preliminary solution or null; does NOT create one. */
    public CoreBallistics.Solution peek(UUID id,long tick,CoreBallistics.Vec eye,
                     CoreBallistics.Vec target,CoreBallistics.Vec velocity,CoreBallistics.Vec shooterVelocity,int ttl){
        CoreBallistics.Solution s=valid(id,tick,eye,target,velocity,shooterVelocity,ttl);
        if(s==null)misses++;else hits++;
        return s;
    }
    /** Reuse or solve at full bow power. Intended ONLY for the selected mode's warm budget. */
    public CoreBallistics.Solution warm(UUID id,long tick,CoreBallistics.Vec eye,
                     CoreBallistics.Vec target,CoreBallistics.Vec velocity,CoreBallistics.Vec shooterVelocity,int ttl){
        CoreBallistics.Solution cached=peek(id,tick,eye,target,velocity,shooterVelocity,ttl);
        if(cached!=null)return cached;
        CoreBallistics.Solution s=CoreBallistics.solve(eye,target,velocity,shooterVelocity,CoreBallistics.FULL_BOW_SPEED);
        solves++;
        if(s!=null)store(id,tick,eye,target,velocity,shooterVelocity,s);
        return s;
    }
    public void store(UUID id,long tick,CoreBallistics.Vec eye,
                     CoreBallistics.Vec target,CoreBallistics.Vec velocity,CoreBallistics.Vec shooterVelocity,
                     CoreBallistics.Solution solution){
        if(solution!=null && (entries.size()<64 || entries.containsKey(id)))
            entries.put(id,new Entry(tick,eye,target,velocity,shooterVelocity,solution));
    }
}
