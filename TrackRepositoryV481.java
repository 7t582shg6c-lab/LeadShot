package dev.leadshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 64-contact timestamped tracking and walk-forward evaluation.
 * Each prediction is archived BEFORE future observations arrive, preventing
 * look-ahead in a motion-model comparison. This component never fires arrows.
 */
public final class TrackRepositoryV481 {
    public static final int MAX_CONTACTS = 64;
    public static final int[] HORIZONS = {5, 10, 20};
    private static final int MAX_PENDING_PER_CONTACT = 48;
    private final Map<UUID, Track> contacts = new HashMap<>();
    private final EnumMap<MotionForecastV481.Model, Score> scores =
            new EnumMap<>(MotionForecastV481.Model.class);
    private long worldEpoch = -1;
    private long lastFrameTick = Long.MIN_VALUE;
    private long gaps, maneuvers, teleports, evictions, evaluated;

    private static final class Score {
        long count;
        double squaredError, maxError;
        final long[] byHorizon = new long[HORIZONS.length];
        final double[] errorByHorizon = new double[HORIZONS.length];
        void record(double err,int horizonIndex) {
            count++; squaredError += err * err; maxError = Math.max(maxError, err);
            byHorizon[horizonIndex]++; errorByHorizon[horizonIndex] += err * err;
        }
    }
    public record ModelScore(long count, double rootMeanSquare, double maximum,
                             long[] samplesByHorizon, double[] rmsByHorizon) {
        public ModelScore {
            samplesByHorizon = samplesByHorizon.clone();
            rmsByHorizon = rmsByHorizon.clone();
        }
        @Override public long[] samplesByHorizon() { return samplesByHorizon.clone(); }
        @Override public double[] rmsByHorizon() { return rmsByHorizon.clone(); }
    }
    public record Summary(int active, long evaluated, long gaps, long maneuvers,
                          long teleports, long evictions,
                          Map<MotionForecastV481.Model, ModelScore> models) {
        public Summary { models = Map.copyOf(models); }
    }
    private record Pending(long dueTick,int horizonIndex,MotionForecastV481.Model model,
                           CoreBallistics.Vec predicted) {}
    private static final class Track {
        TimedObservationV48 latest;
        CoreBallistics.Vec previousVelocity;
        final List<Pending> pending = new ArrayList<>();
        final AdaptiveMotionModelV482 adaptive = new AdaptiveMotionModelV482();
        Track(TimedObservationV48 first) { latest = first; previousVelocity = first.velocity(); }
    }
    public TrackRepositoryV481() {
        for (var model : MotionForecastV481.Model.values()) scores.put(model, new Score());
    }
    public void reset(long newEpoch) {
        contacts.clear(); worldEpoch = newEpoch; lastFrameTick=Long.MIN_VALUE;
        gaps = maneuvers = teleports = evictions = evaluated = 0;
        for (var s : scores.values()) {
            s.count = 0; s.squaredError = s.maxError = 0;
            java.util.Arrays.fill(s.byHorizon, 0);
            java.util.Arrays.fill(s.errorByHorizon, 0);
        }
    }
    public boolean observe(TimedObservationV48 now) {
        if (now == null) return false;
        if (worldEpoch < 0 || now.worldEpoch() != worldEpoch) reset(now.worldEpoch());
        Track track = contacts.get(now.entityId());
        if (track != null) {
            long dt = now.sampledClientTick() - track.latest.sampledClientTick();
            if (dt <= 0) return false;
            double displacement = now.center().minus(track.latest.center()).length();
            if (dt > 1) gaps++;
            if (!Double.isFinite(displacement) || displacement > dt * 12.0) {
                teleports++;
                // Abandon old velocity and archived projections after a large correction.
                track = new Track(now);
                contacts.put(now.entityId(), track);
            } else {
                // Evaluate archived predictions only against an exact matching tick.
                Iterator<Pending> it = track.pending.iterator();
                while (it.hasNext()) {
                    Pending forecast = it.next();
                    if (forecast.dueTick() <= now.sampledClientTick()) {
                        if (forecast.dueTick() == now.sampledClientTick()) {
                            double error = forecast.predicted().minus(now.center()).length();
                            if (Double.isFinite(error)) {
                                scores.get(forecast.model()).record(error,forecast.horizonIndex());
                                track.adaptive.record(forecast.model(),forecast.horizonIndex(),error);
                                evaluated++;
                            }
                        }
                        it.remove();
                    }
                }
                CoreBallistics.Vec prior = track.latest.velocity();
                // Some remotely interpolated entities expose zero delta movement
                // despite visibly changing position. In that case infer motion
                // from successive timed centers instead of treating them as still.
                CoreBallistics.Vec observedStep = now.center().minus(track.latest.center()).times(1.0/dt);
                if (now.velocity().length()<.04 && observedStep.length()>.06 && dt==1) {
                    now = new TimedObservationV48(now.entityId(),now.worldEpoch(),
                            now.sampledClientTick(),now.center(),observedStep,
                            now.halfBox(),now.airborne());
                }
                double acceleration = now.velocity().minus(prior).length() / dt;
                if (acceleration > .35) maneuvers++;
                track.previousVelocity = dt == 1 ? prior : now.velocity();
                track.latest = now;
            }
        } else {
            if (contacts.size() >= MAX_CONTACTS) {
                // Caller sends the nearest 64. Dropping the stalest ID protects bounded memory.
                UUID remove = null;
                long oldest = Long.MAX_VALUE;
                for (var e : contacts.entrySet()) {
                    long t = e.getValue().latest.sampledClientTick();
                    if (t < oldest) { oldest=t; remove=e.getKey(); }
                }
                if (remove != null) { contacts.remove(remove); evictions++; }
            }
            track = new Track(now);
            contacts.put(now.entityId(), track);
        }
        // Every 5 ticks store 5/10/20-tick projections; maximum 48 pending per contact.
        if (now.sampledClientTick() % 5 == 0) {
            for (int k = 0; k < HORIZONS.length; k++) {
                for (var model : MotionForecastV481.Model.values()) {
                    var prediction = MotionForecastV481.estimate(now.center(),now.velocity(),
                            track.previousVelocity,HORIZONS[k],model);
                    if (prediction != null) track.pending.add(new Pending(
                            now.sampledClientTick()+HORIZONS[k],k,model,prediction.center()));
                }
            }
            while (track.pending.size() > MAX_PENDING_PER_CONTACT) track.pending.remove(0);
        }
        return true;
    }
    public void retain(long epoch,long tick,Set<UUID> active) {
        if (worldEpoch != epoch) { reset(epoch); return; }
        contacts.entrySet().removeIf(e -> !active.contains(e.getKey()) ||
                tick < e.getValue().latest.sampledClientTick() ||
                tick - e.getValue().latest.sampledClientTick() > 2);
    }
    /** Consume a sampler frame once; preserves per-target held-out forecasts and
     * performs retain as part of the same operation. Independent calls to observe()
     * remain for test harnesses and older integrations.
     */
    public boolean observeFrame(ObservationBusV483.Frame frame){
        if(frame==null)return false;
        if(worldEpoch>=0 && frame.epoch()<worldEpoch)return false;
        if(frame.epoch()==worldEpoch && frame.tick()<=lastFrameTick)return false;
        if(worldEpoch!=frame.epoch())reset(frame.epoch());
        var active=new java.util.HashSet<UUID>(frame.contacts().size());
        for(var observation:frame.contacts()){
            active.add(observation.entityId());
            observe(observation);
        }
        retain(frame.epoch(),frame.tick(),active);
        lastFrameTick=frame.tick();
        return true;
    }
    public TimedObservationV48 observation(UUID uuid) {
        Track t = contacts.get(uuid); return t == null ? null : t.latest;
    }
    public MotionForecastV481.Estimate forecast(UUID uuid,int horizon,MotionForecastV481.Model model) {
        Track t=contacts.get(uuid);
        return t == null ? null : MotionForecastV481.estimate(
                t.latest.center(),t.latest.velocity(),t.previousVelocity,horizon,model);
    }
    /** Selects CV/CA/CT from the target's own held-out accuracy data.
     * This is diagnostic evidence, NOT an authorization to steer or fire.
     */
    public record AdaptiveForecast(MotionForecastV481.Model model,
                                   MotionForecastV481.Estimate estimate,
                                   long evaluatedSamples,double recentRms,boolean trained) {}
    public AdaptiveForecast adaptiveForecast(UUID id,int horizon){
        if(id==null||horizon<0||horizon>20)return null;
        Track track=contacts.get(id);
        if(track==null)return null;
        var decision=track.adaptive.decision(horizon);
        var predicted=MotionForecastV481.estimate(track.latest.center(),track.latest.velocity(),
                track.previousVelocity,horizon,decision.model());
        return predicted==null?null:new AdaptiveForecast(decision.model(),predicted,
                decision.samples(),decision.recentRms(),decision.trained());
    }
    public Map<MotionForecastV481.Model,Integer> adaptiveCounts(int horizon){
        var result=new EnumMap<MotionForecastV481.Model,Integer>(MotionForecastV481.Model.class);
        for(var m:MotionForecastV481.Model.values())result.put(m,0);
        for(Track t:contacts.values()){
            var decision=t.adaptive.decision(horizon);
            if(decision.trained())result.put(decision.model(),result.get(decision.model())+1);
        }
        return result;
    }
    public Summary summary() {
        var snap = new EnumMap<MotionForecastV481.Model,ModelScore>(MotionForecastV481.Model.class);
        for (var entry : scores.entrySet()) {
            Score s = entry.getValue();
            double[] rms = new double[HORIZONS.length];
            for(int i=0;i<HORIZONS.length;i++)
                rms[i] = s.byHorizon[i]==0 ? Double.NaN :
                        Math.sqrt(s.errorByHorizon[i]/s.byHorizon[i]);
            snap.put(entry.getKey(),new ModelScore(s.count,
                    s.count==0 ? Double.NaN : Math.sqrt(s.squaredError/s.count),
                    s.count==0 ? Double.NaN : s.maxError,s.byHorizon,rms));
        }
        return new Summary(contacts.size(),evaluated,gaps,maneuvers,
                teleports,evictions,snap);
    }
}
