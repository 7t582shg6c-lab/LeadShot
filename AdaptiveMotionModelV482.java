package dev.leadshot;

import java.util.EnumMap;

/** Forward-only model selection using delayed observations. Data from future
 * ticks is never consumed before the due tick; scores are diagnostic only.
 * The three horizons have independent errors to avoid picking a model whose
 * 5-tick accuracy does not generalize to 20-tick lead prediction.
 */
public final class AdaptiveMotionModelV482 {
    public static final int[] HORIZONS = {5, 10, 20};
    private static final double EMA_ALPHA=.25;
    private static final int MIN_EVALUATIONS=4;
    private static final double REQUIRED_GAIN=.90;
    private final EnumMap<MotionForecastV481.Model, double[]> errors =
            new EnumMap<>(MotionForecastV481.Model.class);
    private final EnumMap<MotionForecastV481.Model, long[]> counts =
            new EnumMap<>(MotionForecastV481.Model.class);
    private final MotionForecastV481.Model[] selected = new MotionForecastV481.Model[3];
    public record Decision(MotionForecastV481.Model model, long samples,
                           double recentRms, boolean trained) {}
    public AdaptiveMotionModelV482(){
        for(var model:MotionForecastV481.Model.values()){
            errors.put(model,new double[3]); counts.put(model,new long[3]);
        }
        java.util.Arrays.fill(selected,MotionForecastV481.Model.CONSTANT_VELOCITY);
    }
    /** Records only the actual out-of-sample (walk-forward) error. */
    public void record(MotionForecastV481.Model model,int horizonIndex,double error){
        if(model==null||horizonIndex<0||horizonIndex>=3 ||
                !Double.isFinite(error)||error<0)return;
        // Cap extreme network jumps; a single teleport should not dominate EMA.
        double sq=Math.min(2500,error*error);
        double[] arr=errors.get(model);long[] n=counts.get(model);
        arr[horizonIndex] = n[horizonIndex]==0 ? sq :
                (1-EMA_ALPHA)*arr[horizonIndex]+EMA_ALPHA*sq;
        n[horizonIndex]++;
    }
    public Decision decision(int horizonTicks){
        int bucket=0;
        if(horizonTicks>7)bucket=horizonTicks<=15?1:2;
        var current=selected[bucket];
        var best=current;
        double currentSq=errors.get(current)[bucket];
        long currentN=counts.get(current)[bucket];
        if(currentN<MIN_EVALUATIONS){
            // Until all three models have enough *observed* held-out results,
            // CV remains the conservative baseline.
            return new Decision(MotionForecastV481.Model.CONSTANT_VELOCITY,currentN,
                    currentN==0?Double.NaN:Math.sqrt(currentSq),false);
        }
        for(var model:MotionForecastV481.Model.values()){
            long n=counts.get(model)[bucket];
            if(n<MIN_EVALUATIONS)continue;
            // Require 10% squared-error improvement before switching models.
            if(errors.get(model)[bucket]<currentSq*REQUIRED_GAIN){
                currentSq=errors.get(model)[bucket];best=model;
            }
        }
        selected[bucket]=best;
        return new Decision(best,counts.get(best)[bucket],
                Math.sqrt(errors.get(best)[bucket]),true);
    }
    public int totalSamples(){
        int sum=0;
        for(long[] arr:counts.values())for(long n:arr)sum+=(int)Math.min(Integer.MAX_VALUE-sum,n);
        return sum;
    }
}
