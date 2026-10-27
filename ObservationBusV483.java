package dev.leadshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One immutable time-stamped frame shared by consumers of client-visible contacts.
 * No Minecraft class dependencies. The client sampler remains the only publisher.
 * This is NOT a server tick or a server-authoritative entity snapshot.
 */
public final class ObservationBusV483 {
    public static final int MAX_CONTACTS = TrackRepositoryV481.MAX_CONTACTS;
    public record Frame(long epoch, long tick, List<TimedObservationV48> contacts) {
        public Frame {
            if (epoch < 0 || tick < 0 || contacts == null || contacts.size() > MAX_CONTACTS)
                throw new IllegalArgumentException("invalid observation frame");
            contacts = List.copyOf(contacts);
            Set<UUID> unique = new HashSet<>();
            for (TimedObservationV48 item:contacts) {
                if (item==null || item.worldEpoch()!=epoch || item.sampledClientTick()!=tick
                        || !unique.add(item.entityId()))
                    throw new IllegalArgumentException("frame contains invalid/duplicate entity");
            }
        }
        public TimedObservationV48 find(UUID id){
            if(id==null)return null;
            for(TimedObservationV48 item:contacts)if(item.entityId().equals(id))return item;
            return null;
        }
        public boolean validFor(long expectedEpoch,long nowTick,int maxAge){
            return maxAge>=0 && epoch==expectedEpoch && nowTick>=tick && nowTick-tick<=maxAge;
        }
    }
    private volatile Frame latest;
    public void clear(){latest=null;}
    public Frame latest(){return latest;}
    /** Publish only a new, strictly newer observation from the same world epoch.
     * The call is transactional: failed publication leaves the old frame unchanged.
     */
    public boolean publish(long epoch,long tick,List<TimedObservationV48> observations){
        if(epoch<0||tick<0||observations==null || observations.size()>MAX_CONTACTS)return false;
        if(latest!=null){
            if(epoch<latest.epoch())return false;
            if(epoch==latest.epoch() && tick<=latest.tick())return false;
        }
        try{
            Frame next=new Frame(epoch,tick,new ArrayList<>(observations));
            latest=next;
            return true;
        }catch(IllegalArgumentException | NullPointerException invalid){return false;}
    }
}
