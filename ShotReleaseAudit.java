package dev.leadshot;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only retrospective audit of an expected target position at the tick when
 * a bow-use session ends. NOT proof that an arrow was spawned, flew or hit.
 * Inputs are client-observed target centers, not server-authoritative data.
 * Names/UUIDs are never included in diagnostic lines or written to disk.
 */
public final class ShotReleaseAudit {
    private static final int MAX_PENDING = 64;
    private static final int MAX_FLIGHT_TICKS = 120;
    private static final int RECENT = 256;
    private static final double MAX_ERROR = 512.0;

    private record Pending(UUID target, long dueTick, CoreBallistics.Vec predicted) { }
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final double[] recentErrors = new double[RECENT];
    private int count, cursor;
    private long total, expired, invalid, rejected;
    private double sum;

    /** Returns true only when a well-formed future observation has been queued. */
    public boolean recordUseEnd(UUID target, long tick, int flightTicks, CoreBallistics.Vec futureCenter) {
        if (target == null || tick < 0 || flightTicks < 1 || flightTicks > MAX_FLIGHT_TICKS
                || futureCenter == null || !futureCenter.finite()
                || Math.max(Math.max(Math.abs(futureCenter.x()), Math.abs(futureCenter.y())),
                        Math.abs(futureCenter.z())) > 30_000_000) {
            rejected++;
            return false;
        }
        while (pending.size() >= MAX_PENDING) {
            pending.removeFirst();
            expired++;
        }
        pending.addLast(new Pending(target, tick + flightTicks, futureCenter));
        return true;
    }

    /** Only samples EXACT due ticks; no interpolation or invented observation. */
    public void observe(UUID target, long tick, CoreBallistics.Vec actualCenter) {
        if (target == null || actualCenter == null || !actualCenter.finite()) return;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
            Pending p = it.next();
            if (p.dueTick() < tick) { it.remove(); expired++; continue; }
            if (p.dueTick() != tick || !p.target().equals(target)) continue;
            double distance = p.predicted().minus(actualCenter).length();
            if (Double.isFinite(distance) && distance <= MAX_ERROR) {
                recentErrors[cursor] = distance;
                cursor = (cursor + 1) % RECENT;
                count = Math.min(RECENT, count + 1);
                total++;
                sum += distance;
            } else invalid++;
            it.remove();
        }
    }

    /** Releases that cannot be observed precisely at their due tick are unknown. */
    public void expire(long tick) {
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
            if (it.next().dueTick() < tick) { it.remove(); expired++; }
        }
    }

    public record Summary(int recentPairs, long allPairs, int pending, long expired,
                          long invalid, long rejected, double recentMean,
                          double recentP90, double allTimeMean) {
        public boolean enoughSamples() { return recentPairs >= 30; }
    }
    public Summary summary() {
        if (count == 0) return new Summary(0,total,pending.size(),expired,invalid,rejected,
                Double.NaN,Double.NaN, total == 0 ? Double.NaN : sum/total);
        double[] ordered = Arrays.copyOf(recentErrors,count);
        double recentSum = 0;
        for (double e : ordered) recentSum += e;
        Arrays.sort(ordered);
        return new Summary(count,total,pending.size(),expired,invalid,rejected,
                recentSum/count, ordered[(int)Math.ceil(0.90 * count)-1], sum/total);
    }
    public String diagnosticLine() {
        Summary s = summary();
        if (s.recentPairs() == 0) return "弓使用終了監査: 観測ペアなし / 命中判定ではありません";
        return String.format(Locale.ROOT,
                "終了時予測の位置誤差: n=%d 平均%.2fm P90 %.2fm (命中未判定)",
                s.recentPairs(),s.recentMean(),s.recentP90());
    }
    public void clear() {
        pending.clear();Arrays.fill(recentErrors,0);
        total = expired = invalid = rejected = 0;
        count = cursor = 0;
        sum = 0;
    }
    public int pendingCount() { return pending.size(); }
}
