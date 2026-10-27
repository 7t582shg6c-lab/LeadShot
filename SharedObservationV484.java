package dev.leadshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Single-producer contact frame for LeadShot and diagnostic consumers.
 * World identity is tracked by OBJECT REFERENCE, not by dimension name.
 * Only the LeadShot targeting tick is permitted to publish observations.
 * Never treats client position observations as server-authoritative.
 */
public final class SharedObservationV484 {
    public static final SharedObservationV484 INSTANCE = new SharedObservationV484();
    public record Sample(UUID id, CoreBallistics.Vec center, CoreBallistics.Vec velocity,
                         CoreBallistics.Vec halfBox, boolean airborne) {
        public Sample {
            if(id == null || center == null || velocity == null || halfBox == null ||
                    !center.finite() || !velocity.finite() || !halfBox.finite() ||
                    halfBox.x()<0 || halfBox.y()<0 || halfBox.z()<0)
                throw new IllegalArgumentException("invalid shared sample");
        }
    }
    private final ObservationBusV483 bus = new ObservationBusV483();
    private Object world;
    private long epoch = 0;
    private long previousTick = Long.MIN_VALUE;

    /** Called at world-change and on logout to immediately invalidate stale data. */
    public synchronized void resetWorld(Object nextWorld){
        if(world == nextWorld)return;
        world=nextWorld;
        epoch++;
        previousTick=Long.MIN_VALUE;
        bus.clear();
    }
    public synchronized long epoch(){return epoch;}
    public synchronized ObservationBusV483.Frame latest(Object currentWorld,long nowTick,int maxAge){
        if(currentWorld == null || currentWorld != world || maxAge < 0)return null;
        ObservationBusV483.Frame f=bus.latest();
        return f != null && f.validFor(epoch,nowTick,maxAge) ? f : null;
    }
    /** Once per targeting tick, after original observed velocities have been computed.
     * Returns false without replacing the last good frame on malformed input.
     */
    public synchronized boolean publish(Object currentWorld,long tick,List<Sample> samples){
        if(currentWorld == null || tick < 0 || samples == null || samples.size()>ObservationBusV483.MAX_CONTACTS)
            return false;
        if(world!=currentWorld)resetWorld(currentWorld);
        if(tick<=previousTick)return false;
        List<TimedObservationV48> next = new ArrayList<>(samples.size());
        try {
            for(Sample s : samples){
                if(s==null)return false;
                next.add(new TimedObservationV48(s.id(),epoch,tick,s.center(),s.velocity(),s.halfBox(),s.airborne()));
            }
        } catch(IllegalArgumentException invalid){return false;}
        if(!bus.publish(epoch,tick,next))return false;
        previousTick=tick;
        return true;
    }
}
