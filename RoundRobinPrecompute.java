package dev.leadshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Least-recently-visited scheduler across the SAME tracked-entity set.
 * Based on UUID rather than changing distance-sort indices: nearby entities
 * swapping order cannot indefinitely starve a tracked target.
 */
public final class RoundRobinPrecompute {
    private final Map<UUID,Long> lastVisit=new HashMap<>();
    private long serial;
    public void reset(){lastVisit.clear();serial=0;}
    public <T> List<T> next(List<T> tracked,int maxPerTick,Function<T,UUID> idOf){
        if(tracked.isEmpty()||maxPerTick<=0){
            if(tracked.isEmpty())lastVisit.clear();
            return List.of();
        }
        Set<UUID> live=new HashSet<>();
        for(T element:tracked)live.add(idOf.apply(element));
        lastVisit.keySet().retainAll(live);
        int count=Math.min(tracked.size(),maxPerTick);
        ArrayList<T> out=new ArrayList<>(count);
        Set<UUID> chosen=new HashSet<>();
        for(int k=0;k<count;k++){
            T best=null;
            long oldest=Long.MAX_VALUE;
            for(T element:tracked){
                UUID id=idOf.apply(element);
                if(chosen.contains(id))continue;
                long last=lastVisit.getOrDefault(id,Long.MIN_VALUE);
                if(best==null||last<oldest){best=element;oldest=last;}
            }
            if(best==null)break;
            UUID id=idOf.apply(best);
            chosen.add(id);
            lastVisit.put(id,++serial);
            out.add(best);
        }
        return out;
    }
}
