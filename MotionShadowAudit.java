package dev.leadshot;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A bounded, read-only client-observation prediction audit. Minecraft-independent.
 * Nothing from this class is used to rotate the camera or choose a target.
 * No names, UUIDs or coordinates are written to disk or to the diagnostic summary.
 *
 * The comparison reference is the raw last-observation velocity extrapolator,
 * NOT LeadShot's existing multi-model intercept solution or real server truth.
 */
public final class MotionShadowAudit {
    private static final int MAX_TRACKS = 128;
    private static final int MAX_PENDING_PER_TRACK = 12;
    private static final int MAX_GAP = 2;
    private static final int SAMPLE_INTERVAL = 10;
    private static final int STALE_AFTER = 40;
    private static final int[] HORIZONS = {5, 10, 20};

    public record Vec(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Vec plus(Vec b) { return new Vec(x+b.x, y+b.y, z+b.z); }
        public Vec minus(Vec b) { return new Vec(x-b.x, y-b.y, z-b.z); }
        public Vec times(double a) { return new Vec(x*a, y*a, z*a); }
        public double distance(Vec b) { return Math.hypot(Math.hypot(x-b.x,y-b.y),z-b.z); }
        public double length() { return Math.hypot(Math.hypot(x,y),z); }
    }
    private static final Vec ZERO = new Vec(0,0,0);

    private record Pending(long due, int horizon, Vec base, Vec filtered) { }
    private static final class Track {
        long lastTick = Long.MIN_VALUE;
        Vec last = ZERO;
        Vec rawVelocity = ZERO;
        Vec smoothedVelocity = ZERO;
        Vec smoothedAcceleration = ZERO;
        int consecutive;
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
    }
    private static final class Stat {
        long count;
        double baselineErrorSum;
        double filteredErrorSum;
        // Recent paired metrics only. Bounded memory; all-time means remain in sums above.
        private static final int RECENT = 256;
        final double[] recentRaw = new double[RECENT];
        final double[] recentFiltered = new double[RECENT];
        int recentCount;
        int cursor;
        void add(double raw, double filtered) {
            count++;
            baselineErrorSum += raw;
            filteredErrorSum += filtered;
            recentRaw[cursor] = raw;
            recentFiltered[cursor] = filtered;
            cursor = (cursor + 1) % RECENT;
            if (recentCount < RECENT) recentCount++;
        }
        void clear() {
            count=0; baselineErrorSum=0; filteredErrorSum=0;
            recentCount=0; cursor=0;
            java.util.Arrays.fill(recentRaw, 0);
            java.util.Arrays.fill(recentFiltered, 0);
        }
    }

    public record Comparison(int horizon, long pairs, double rawDeltaMae, double experimentalMae) { }
    /** Last <=256 matched observations at the same target and future tick. */
    public record RecentComparison(int horizon, int samples, double rawMae, double experimentalMae,
                                   double rawP90, double experimentalP90, double winFraction) {
        /** No performance superiority claim with fewer than 50 recent valid pairs. */
        public boolean hasEnoughSamples() { return samples >= 50; }
        public String bestModel() {
            if (!hasEnoughSamples()) return "INSUFFICIENT";
            if (experimentalMae < rawMae && experimentalP90 <= rawP90) return "EXPERIMENTAL";
            if (rawMae < experimentalMae && rawP90 <= experimentalP90) return "RAW";
            return "MIXED";
        }
    }
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Map<Integer, Stat> statistics = new HashMap<>();

    public MotionShadowAudit() {
        for (int h : HORIZONS) statistics.put(h, new Stat());
    }

    public void clear() {
        tracks.clear();
        for (Stat s : statistics.values()) {
            s.clear();
        }
    }
    public int trackedCount() { return tracks.size(); }

    /** Call once per client tick per locally loaded entity, with its observed center. */
    public void observe(UUID id, long tick, double x, double y, double z) {
        if (id == null) return;
        Vec now = new Vec(x,y,z);
        if (!now.finite()) return;
        Track track = tracks.get(id);
        if (track == null) {
            if (tracks.size() >= MAX_TRACKS) removeOldest();
            track = new Track();
            tracks.put(id, track);
        }
        if (tick <= track.lastTick) return;
        if (track.consecutive == 0) {
            reset(track, now, tick);
            return;
        }
        long dt = tick-track.lastTick;
        double jump = now.distance(track.last);
        if (dt > MAX_GAP || jump > Math.max(12.0, 4.0*dt) || !Double.isFinite(jump)) {
            reset(track, now, tick);
            return;
        }
        // Every forecast is scored on precisely the same due tick. No interpolation
        // across missing observations, so the denominator is a paired comparison.
        for (Iterator<Pending> iter=track.pending.iterator();iter.hasNext();) {
            Pending p=iter.next();
            if (p.due()<tick) { iter.remove(); continue; }
            if (p.due()==tick) {
                Stat stat=statistics.get(p.horizon());
                double b=now.distance(p.base()), f=now.distance(p.filtered());
                if (Double.isFinite(b) && Double.isFinite(f) && b < 256 && f < 256) {
                    stat.add(b, f);
                }
                iter.remove();
            }
        }
        Vec raw=now.minus(track.last).times(1.0/dt);
        if (!raw.finite() || raw.length()>4.0) {
            reset(track,now,tick);
            return;
        }
        Vec lastSmoothed=track.smoothedVelocity;
        if (track.consecutive<2) {
            track.smoothedVelocity=raw;
            track.smoothedAcceleration=ZERO;
        } else {
            track.smoothedVelocity=lastSmoothed.times(.45).plus(raw.times(.55));
            Vec innovation=raw.minus(lastSmoothed).times(1.0/dt);
            track.smoothedAcceleration=clampVector(track.smoothedAcceleration.times(.78)
                    .plus(innovation.times(.22)),.10);
        }
        track.rawVelocity=raw;
        track.last=now;
        track.lastTick=tick;
        track.consecutive=Math.min(1000,track.consecutive+1);
        if (track.consecutive>=4 && tick % SAMPLE_INTERVAL==0) {
            for (int h:HORIZONS) {
                // Dampen assumed acceleration: sudden mob AI turns are not observable.
                double accTicks=(h*h)*.5/(1+h/9.0);
                Vec experimental=now.plus(track.smoothedVelocity.times(h))
                        .plus(track.smoothedAcceleration.times(accTicks));
                Vec rawPosition=now.plus(track.rawVelocity.times(h));
                if (experimental.finite() && rawPosition.finite())
                    track.pending.addLast(new Pending(tick+h,h,rawPosition,experimental));
            }
            while (track.pending.size()>MAX_PENDING_PER_TRACK) track.pending.removeFirst();
        }
    }

    public void expire(long tick) {
        tracks.entrySet().removeIf(e->tick-e.getValue().lastTick>STALE_AFTER);
    }
    public Comparison comparison(int horizon) {
        Stat s=statistics.get(horizon);
        if (s==null || s.count==0) return new Comparison(horizon,0,Double.NaN,Double.NaN);
        return new Comparison(horizon,s.count,s.baselineErrorSum/s.count,s.filteredErrorSum/s.count);
    }
    public RecentComparison recentComparison(int horizon) {
        Stat s = statistics.get(horizon);
        if (s == null || s.recentCount == 0)
            return new RecentComparison(horizon, 0, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN);
        int n = s.recentCount;
        double[] raw = java.util.Arrays.copyOf(s.recentRaw, n);
        double[] filtered = java.util.Arrays.copyOf(s.recentFiltered, n);
        double sumRaw = 0, sumFiltered = 0;
        int wins = 0;
        for (int i=0; i<n; i++) {
            sumRaw += raw[i]; sumFiltered += filtered[i];
            if (filtered[i] < raw[i]) wins++;
        }
        java.util.Arrays.sort(raw);
        java.util.Arrays.sort(filtered);
        int p90 = (int)Math.ceil(.90*n) - 1;
        return new RecentComparison(horizon, n, sumRaw/n, sumFiltered/n,
                raw[p90], filtered[p90], wins/(double)n);
    }

    /** Anonymized multi-horizon diagnostics, only emitted when explicitly opted in. */
    public String fullDiagnosticLine() {
        StringBuilder sb = new StringBuilder("LeadShot shadow (client position estimates, NOT hit chance):");
        for (int h : HORIZONS) {
            RecentComparison c = recentComparison(h);
            if (c.samples() == 0) {
                sb.append(" ").append(h).append("t=no pairs;");
            } else {
                sb.append(String.format(Locale.ROOT,
                        " %dt n=%d raw=%.3f experimental=%.3f p90Raw=%.3f p90Experimental=%.3f wins=%.0f%% status=%s;",
                        h, c.samples(), c.rawMae(), c.experimentalMae(), c.rawP90(),
                        c.experimentalP90(), c.winFraction()*100, c.bestModel()));
            }
        }
        return sb.toString();
    }

    /** Anonymous compact status line; never includes names, coordinates or server URLs. */
    public String diagnosticLine() {
        Comparison c=comparison(10);
        if (c.pairs()==0) return "LeadShot shadow 10t: no paired samples";
        return String.format(Locale.ROOT,
                "LeadShot shadow 10t: n=%d raw=%.3f filter=%.3f blocks (client observations)",
                c.pairs(),c.rawDeltaMae(),c.experimentalMae());
    }
    private static Vec clampVector(Vec v, double max) {
        double size=v.length();
        return size>max?v.times(max/size):v;
    }
    private void removeOldest() {
        UUID oldest=null;
        long age=Long.MAX_VALUE;
        for (var e:tracks.entrySet()) if (e.getValue().lastTick<age) {
            oldest=e.getKey();age=e.getValue().lastTick;
        }
        if (oldest!=null) tracks.remove(oldest);
    }
    private static void reset(Track t, Vec now,long tick) {
        t.last=now;t.lastTick=tick;
        t.rawVelocity=ZERO;t.smoothedVelocity=ZERO;t.smoothedAcceleration=ZERO;
        t.consecutive=1;
        t.pending.clear();
    }
}
