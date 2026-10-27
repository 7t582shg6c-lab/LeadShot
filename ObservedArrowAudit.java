package dev.leadshot;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side observed-arrow diagnostic. Unlike the release-position audit, a
 * launch is counted only if a locally visible projectile belonging to the
 * shooter is actually observed after a bow-use-end candidate.
 *
 * This does NOT infer a server-authoritative hit, kill, or damage event.
 * Per-entity identities exist only in RAM for association and are NEVER logged.
 */
public final class ObservedArrowAudit {
    private static final int MAX_PENDING = 12, MAX_TRACKED = 32, RECENT = 256;
    private static final int SPAWN_WINDOW = 8, FLIGHT_WINDOW = 60;
    private static final double MAX_SPAWN_DISTANCE = 8.0;
    private static final double MIN_SPEED = 0.2, MAX_SPEED = 8.0;
    private static final double MAX_SAMPLE_ERROR = 128.0;

    private record Release(long tick, CoreBallistics.Vec eye, CoreBallistics.Vec predictedVelocity) { }
    private static final class Flight {
        final CoreBallistics.Vec origin, launchVelocity;
        final long started;
        long lastSeen;
        CoreBallistics.Vec lastPosition;
        int paired;
        Flight(CoreBallistics.Vec origin, CoreBallistics.Vec launchVelocity,
               long started) {
            this.origin=origin; this.launchVelocity=launchVelocity;
            this.started=started; this.lastSeen=started; this.lastPosition=origin;
        }
    }
    private final ArrayDeque<Release> pending=new ArrayDeque<>();
    private final Map<UUID,Flight> active=new HashMap<>();
    // Prevent an already-visible older arrow from being attributed to a new
    // bow release. Only first-seen projectiles can match a new candidate.
    private final Map<UUID,Long> seenArrows=new HashMap<>();
    private final double[] recentErrors=new double[RECENT];
    private int recentSize,recentNext;
    private long candidates,matched,unmatched,finished,trajectoryPairs,ignored;
    private double launchSpeedErrorSum,launchAngleErrorSum,trajectoryErrorSum;

    public boolean noteUseEnd(long tick, CoreBallistics.Vec shooterEye,
                              CoreBallistics.Vec predictedArrowVelocity) {
        if(tick<0 || !valid(shooterEye) || !valid(predictedArrowVelocity)
                || !speedOkay(predictedArrowVelocity)) {ignored++; return false;}
        if(pending.size()==MAX_PENDING){pending.removeFirst();unmatched++;}
        pending.addLast(new Release(tick,shooterEye,predictedArrowVelocity));
        candidates++;
        return true;
    }

    /**
     * Observe an actual local-player-owned AbstractArrow, identified by UUID.
     * Called at most once per arrow and game tick by the caller.
     */
    public boolean observe(long tick, UUID arrowId, CoreBallistics.Vec position,
                           CoreBallistics.Vec velocity) {
        if(tick<0||arrowId==null||!valid(position)||!valid(velocity)) {ignored++;return false;}
        Flight flight=active.get(arrowId);
        Long previouslySeen=seenArrows.putIfAbsent(arrowId,tick);
        if(flight==null && previouslySeen!=null)return false;
        if(flight==null){
            if(!speedOkay(velocity)){ignored++;return false;}
            Release chosen=null;
            double bestScore=Double.POSITIVE_INFINITY;
            for(Release release:pending){
                long dt=tick-release.tick();
                if(dt<0||dt>SPAWN_WINDOW)continue;
                double dist=position.minus(release.eye()).length();
                if(!Double.isFinite(dist)||dist>MAX_SPAWN_DISTANCE)continue;
                double score=dist+dt*.75;
                if(score<bestScore){bestScore=score;chosen=release;}
            }
            if(chosen==null)return false;
            if(active.size()>=MAX_TRACKED){
                // Evict the oldest active session instead of growing forever.
                UUID oldest=null;long min=Long.MAX_VALUE;
                for(var e:active.entrySet())if(e.getValue().lastSeen<min){min=e.getValue().lastSeen;oldest=e.getKey();}
                if(oldest!=null){active.remove(oldest);finished++;}
            }
            pending.remove(chosen);
            flight=new Flight(position,velocity,tick);
            active.put(arrowId,flight);
            matched++;
            launchSpeedErrorSum+=Math.abs(chosen.predictedVelocity().length()-velocity.length());
            launchAngleErrorSum+=angularErrorDegrees(chosen.predictedVelocity(),velocity);
            return true;
        }
        if(tick<=flight.lastSeen)return false; // ignore same-tick duplicates
        long lifetime=tick-flight.started;
        if(lifetime>FLIGHT_WINDOW) {active.remove(arrowId);finished++;return false;}
        if(tick-flight.lastSeen>2){ // cannot interpolate missing client observations
            active.remove(arrowId);finished++;return false;
        }
        if(position.minus(flight.lastPosition).length()<0.01 && lifetime>2) {
            // Projectile may be grounded or frozen. This is NOT a hit signal.
            active.remove(arrowId);finished++;return false;
        }
        CoreBallistics.Vec predicted=CoreBallistics.simulate(flight.origin,flight.launchVelocity,(int)lifetime);
        if(predicted==null){active.remove(arrowId);finished++;return false;}
        double error=position.minus(predicted).length();
        flight.lastSeen=tick;flight.lastPosition=position;
        if(!Double.isFinite(error)||error>MAX_SAMPLE_ERROR){ignored++;return false;}
        trajectoryPairs++;
        trajectoryErrorSum+=error;
        recentErrors[recentNext]=error;
        recentNext=(recentNext+1)%RECENT;
        recentSize=Math.min(recentSize+1,RECENT);
        flight.paired++;
        return true;
    }

    public void expire(long tick){
        seenArrows.entrySet().removeIf(entry -> tick-entry.getValue()>FLIGHT_WINDOW+SPAWN_WINDOW+5);
        // Bound first-seen inventory even if a crowded area spawns hundreds of
        // arrows during one tick. Remove earliest observations first.
        while(seenArrows.size()>128){
            UUID earliest=null;long first=Long.MAX_VALUE;
            for(var entry:seenArrows.entrySet())if(entry.getValue()<first){first=entry.getValue();earliest=entry.getKey();}
            if(earliest!=null)seenArrows.remove(earliest); else break;
        }
        for(Iterator<Release> it=pending.iterator();it.hasNext();){
            Release r=it.next();if(tick-r.tick()>SPAWN_WINDOW){it.remove();unmatched++;}
        }
        for(Iterator<Map.Entry<UUID,Flight>> it=active.entrySet().iterator();it.hasNext();){
            Flight f=it.next().getValue();
            if(tick-f.lastSeen>3 || tick-f.started>FLIGHT_WINDOW){it.remove();finished++;}
        }
    }

    private static boolean valid(CoreBallistics.Vec v) {
        return v!=null&&v.finite()&&Math.abs(v.x())<=30_000_000&&Math.abs(v.y())<=30_000_000&&Math.abs(v.z())<=30_000_000;
    }
    private static boolean speedOkay(CoreBallistics.Vec v) {
        double speed=v.length();return Double.isFinite(speed)&&speed>=MIN_SPEED&&speed<=MAX_SPEED;
    }
    private static double angularErrorDegrees(CoreBallistics.Vec predicted,CoreBallistics.Vec actual) {
        double dot=predicted.x()*actual.x()+predicted.y()*actual.y()+predicted.z()*actual.z();
        double cos=dot/(predicted.length()*actual.length());
        return Math.toDegrees(Math.acos(Math.max(-1.0,Math.min(1.0,cos))));
    }
    public record Summary(long useEnds,long observedLaunches,long unmatchedUseEnds,
                          int pending,int active,long finished,long trajectoryPairs,
                          double averageLaunchSpeedError,double averageLaunchAngleErrorDegrees,
                          double trajectoryMeanError,double recentP90,long ignoredObservations) { }
    public Summary summary(){
        double p90=Double.NaN;
        if(recentSize>0){
            double[] ordered=Arrays.copyOf(recentErrors,recentSize);Arrays.sort(ordered);
            p90=ordered[(int)Math.ceil(.9*recentSize)-1];
        }
        return new Summary(candidates,matched,unmatched,pending.size(),active.size(),finished,
                trajectoryPairs,
                matched>0?launchSpeedErrorSum/matched:Double.NaN,
                matched>0?launchAngleErrorSum/matched:Double.NaN,
                trajectoryPairs>0?trajectoryErrorSum/trajectoryPairs:Double.NaN,
                p90,ignored);
    }
    public void clear(){
        pending.clear();active.clear();seenArrows.clear();Arrays.fill(recentErrors,0);
        recentSize=recentNext=0;
        candidates=matched=unmatched=finished=trajectoryPairs=ignored=0;
        launchSpeedErrorSum=launchAngleErrorSum=trajectoryErrorSum=0;
    }
}
