package dev.leadshot;

/**
 * Conservative, held-out confidence gate for the approximate 5-tick Elytra
 * model. No claim is made about server-authoritative motion. No coordinates,
 * player IDs, or server-specific values are retained. Default read-only.
 * An explicit second opt-in can request conservative camera-rate/READY gating.
 */
public final class GlideReliabilityV502 {
    public enum State { NO_DATA, RELIABLE, INCONCLUSIVE, POOR_FIT }
    public record Assessment(State state,int samples,double modelRms,
                             double baselineRms,long ageTicks) {}
    private static final int MIN_SAMPLES=24;
    private static final long MAX_FRESH_AGE=40;
    private GlideReliabilityV502() {}
    public static Assessment missing(){
        return new Assessment(State.NO_DATA,0,Double.NaN,Double.NaN,Long.MAX_VALUE);
    }
    public static Assessment evaluate(GlideCalibrationV501.RecentFive recent,long now){
        if(recent==null||now<0||recent.newestTick()>now ||recent.evaluations()<0
                ||recent.latestEvaluatedTick()>now || recent.latestEvaluatedTick()<0)
            return missing();
        long age=now-recent.latestEvaluatedTick();
        if(age>MAX_FRESH_AGE ||recent.evaluations()<MIN_SAMPLES)
            return new Assessment(State.NO_DATA,recent.evaluations(),
                    recent.modelPositionRms(),recent.baselinePositionRms(),age);
        double model=recent.modelPositionRms(),cv=recent.baselinePositionRms();
        if(!Double.isFinite(model)||!Double.isFinite(cv)||model<0||cv<0)
            return missing();
        // Absolute tolerance avoids false 'bad model' signals from near-zero
        // noise in calm gliding. Relative margin prevents needless throttling.
        State state = model>1.20 || (model>.35 && model>cv*1.25+.05)
                ? State.POOR_FIT
                : model<=.65 && model<=cv*1.15+.08
                  ? State.RELIABLE : State.INCONCLUSIVE;
        return new Assessment(state,recent.evaluations(),model,cv,age);
    }
}
