package dev.leadshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded opportunity-quality ranking for the shared G2G/G2A/A2G/A2A
 * interception planner. Lower score is preferred; scores are heuristic
 * angle-equivalent *costs*, never probabilities or permission to fire.
 *
 * A physically intersecting arrow may merely graze an AABB for a fraction
 * of one tick. Consequently we combine (1) the existing bearing/lead cost,
 * (2) actual swept contact dwell and interior margin, (3) contiguous launch
 * windows, and (4) motion-model evidence and relative speed.
 *
 * Predictions are speculative, especially when the shooter is gliding.
 * Returning a preferred future time cannot bypass READY, LOS, obstruction,
 * contact or the ordinary assisted release gate on the eventual release tick.
 */
public final class InterceptOpportunityV513 {
    private InterceptOpportunityV513() {}

    public enum Evidence { TRAINED_CURVE, LINEAR_ONLY }

    /** Measurements for one candidate launch tick, already proven to intersect
     * the predicted moving hitbox AND the legacy CV hitbox. */
    public record Sample(int delayTicks, double legacyCost, double contactDwellTicks,
                         double normalizedInteriorMargin) {
        public Sample {
            if (delayTicks < 0 || delayTicks > 3 ||
                !Double.isFinite(legacyCost) ||
                !Double.isFinite(contactDwellTicks) || contactDwellTicks < 0 ||
                !Double.isFinite(normalizedInteriorMargin) ||
                normalizedInteriorMargin < 0 || normalizedInteriorMargin > 1.000001)
                throw new IllegalArgumentException("invalid opportunity sample");
        }
    }

    public record Choice(int bestDelayTicks, double currentCost, double bestFutureCost,
                         int validLaunchTicks, int bestContiguousWidth,
                         Evidence evidence, String reason) {}

    /** Always requires a real, valid present-tick sample. No observation of
     * future geometry is used for firing authority, only a bounded hold hint. */
    public static Choice rank(EngagementProfileV511.Policy policy, Evidence evidence,
                              int maxWaitTicks, List<Sample> provided) {
        Choice missing = new Choice(0,Double.NaN,Double.NaN,0,0,evidence,"invalid-samples");
        if (policy == null || evidence == null || provided == null ||
            maxWaitTicks < 1 || maxWaitTicks > 3) return missing;
        Sample[] samples = new Sample[maxWaitTicks+1];
        for (Sample sample: provided) {
            if (sample == null || sample.delayTicks() > maxWaitTicks ||
                samples[sample.delayTicks()] != null) return missing;
            samples[sample.delayTicks()] = sample;
        }
        if (samples[0] == null) return missing;
        if (policy.curvedRequired() && evidence != Evidence.TRAINED_CURVE)
            return new Choice(0,Double.NaN,Double.NaN,0,0,evidence,"curve-evidence-required");
        int valid=0;
        for (Sample s : samples) if (s != null) valid++;
        double presentCost = cost(samples[0], 1, policy, evidence);
        int bestWait=0, bestWidth=0;
        double bestFuture=Double.POSITIVE_INFINITY;
        for (int wait=1; wait<=maxWaitTicks; wait++) {
            Sample candidate=samples[wait];
            if (candidate==null) continue;
            // Reject almost tangent future intersections; they are especially
            // sensitive to interpolation and unobserved acceleration.
            if (candidate.contactDwellTicks() < .008) continue;
            int width=contiguousWidth(samples,wait);
            double q=cost(candidate,width,policy,evidence);
            if (q < bestFuture-1e-9 || (Math.abs(q-bestFuture)<=1e-9 && wait<bestWait)) {
                bestFuture=q; bestWait=wait; bestWidth=width;
            }
        }
        if (bestWait==0) return new Choice(0,presentCost,Double.NaN,valid,0,
                evidence,"no-robust-future-contact");
        return new Choice(bestWait,presentCost,bestFuture,valid,bestWidth,
                evidence,"ranked-robust-window");
    }

    /** The base v0.51.2 score assigned 0.20 cost per bearing degree, while
     * EngagementProfile.minimumGainDegrees() is in degrees. Convert the WHOLE
     * existing cost to bearing-degree equivalents before comparing them.
     * The terms below remain conservative HEURISTICS, not calibrated error.
     */
    private static double cost(Sample s,int contiguousWidth,
                               EngagementProfileV511.Policy policy,Evidence evidence) {
        double dwell=Math.min(1.0,s.contactDwellTicks()/.20);
        double margin=Math.min(1.0,s.normalizedInteriorMargin()/.40);
        double tangentPenalty=.55*(1-dwell);
        double edgePenalty=.40*(1-margin);
        double wait=s.delayTicks();
        double kinematicUncertainty=wait*.08*Math.min(4,policy.relativeSpeed());
        double evidenceUncertainty= evidence==Evidence.LINEAR_ONLY
                ? wait*.16*Math.min(4,policy.relativeSpeed()):0;
        double theaterUncertainty = switch (policy.theater()) {
            case GROUND_TO_GROUND -> 0.0;
            case GROUND_TO_AIR -> .10*wait;
            case AIR_TO_GROUND -> .16*wait;
            case AIR_TO_AIR -> .24*wait;
        };
        // A future single-tick opening is weaker evidence than a contiguous
        // set of valid release ticks, but a one-tick theater cap is legal.
        double isolationPenalty=wait==0?0:contiguousWidth<=1?.30:0;
        double windowReward=wait==0?0:.24*Math.min(2,contiguousWidth-1);
        return 5*s.legacyCost()+tangentPenalty+edgePenalty+
                kinematicUncertainty+evidenceUncertainty+theaterUncertainty+
                isolationPenalty-windowReward;
    }

    private static int contiguousWidth(Sample[] samples,int pivot) {
        int left=pivot,right=pivot;
        while(left>0 && samples[left-1]!=null && samples[left-1].contactDwellTicks()>=.008)left--;
        while(right+1<samples.length && samples[right+1]!=null &&
                samples[right+1].contactDwellTicks()>=.008)right++;
        return right-left+1;
    }
}
