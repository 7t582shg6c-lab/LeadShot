package dev.leadshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-entity computation-depth telemetry and motion lookahead. ALL-level
 * audits are preliminary: firing/READY NEVER reads cached audits.
 */
public final class StagedPrecomputeCache {
    public record Motion(CoreBallistics.Vec at5,CoreBallistics.Vec at10,CoreBallistics.Vec at20) {}
    public record Audit(ContactAudit.Result contact,BallisticOcclusion.Result obstruction,
                        TrajectoryConsensus.Result consensus) {}
    private static final class Entry {
        long seenTick, ballisticTick=Long.MIN_VALUE, auditTick=Long.MIN_VALUE;
        CoreBallistics.Vec center,velocity;
        Motion motion;
        Audit audit;
    }
    private final Map<UUID,Entry> entries=new HashMap<>();
    public void clear(){entries.clear();}
    public void retain(Set<UUID> ids){entries.keySet().retainAll(ids);}
    public int size(){return entries.size();}
    public void observe(UUID id,long tick,CoreBallistics.Vec center,
                        CoreBallistics.Vec velocity,boolean predict){
        if(id==null||center==null||velocity==null||!center.finite()||!velocity.finite())return;
        Entry e=entries.get(id);
        if(e==null){if(entries.size()>=PrecomputePolicy.MAX_TRACKED)return;
            e=new Entry();entries.put(id,e);}
        if(e.center!=null && (e.center.minus(center).length()>.22 ||
                e.velocity.minus(velocity).length()>.045)){
            e.ballisticTick=Long.MIN_VALUE;e.auditTick=Long.MIN_VALUE;e.audit=null;
        }
        e.seenTick=tick;e.center=center;e.velocity=velocity;
        e.motion=predict?new Motion(center.add(velocity.times(5)),
                center.add(velocity.times(10)),center.add(velocity.times(20))):null;
    }
    public Motion motion(UUID id){Entry e=entries.get(id);return e==null?null:e.motion;}
    public void ballistic(UUID id,long tick){Entry e=entries.get(id);if(e!=null)e.ballisticTick=tick;}
    public void audited(UUID id,long tick,Audit value){
        Entry e=entries.get(id);
        if(e!=null&&value!=null){e.ballisticTick=tick;e.auditTick=tick;e.audit=value;}
    }
    public Audit audit(UUID id,long tick,int ttl){
        Entry e=entries.get(id);
        return e!=null&&e.audit!=null&&ageOk(tick,e.auditTick,ttl)?e.audit:null;
    }
    private static boolean ageOk(long tick,long then,int ttl){
        return then!=Long.MIN_VALUE && tick>=then && tick-then<=ttl;
    }
    /** Number of tracked entities CURRENTLY processed to the given stage. */
    public int countAtStage(int stage,long tick,int ttl){
        int n=0;
        for(Entry e:entries.values()){
            if(stage<=PrecomputePolicy.LOW){if(e.seenTick==tick)n++;}
            else if(stage==PrecomputePolicy.DEFAULT){if(e.seenTick==tick&&e.motion!=null)n++;}
            else if(stage==PrecomputePolicy.HIGH){if(ageOk(tick,e.ballisticTick,ttl))n++;}
            else if(ageOk(tick,e.auditTick,ttl))n++;
        }
        return n;
    }
}
