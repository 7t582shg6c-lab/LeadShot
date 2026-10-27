package dev.leadshot;

/**
 * Independent, client-only sensitivity check for an already chosen bow solution.
 * Models nominal drag/gravity and two slightly perturbed flight models. This is
 * a consistency audit, NOT a probabilistic hit chance and NOT server authority.
 */
public final class TrajectoryConsensus {
    private TrajectoryConsensus() {}
    public enum State { CONSISTENT, DISPUTED, NO_CONTACT, INVALID }
    public record Result(State state, int supportingModels, int testedModels,
                         double earliestTick, double latestTick) {
        public boolean allAgree() { return state == State.CONSISTENT; }
    }
    private static final double[] DRAG = {.990, .988, .992};
    private static final double[] GRAVITY = {.050, .054, .046};

    public static Result evaluate(CoreBallistics.Vec launch, CoreBallistics.Vec initialVelocity,
            CoreBallistics.Vec center, CoreBallistics.Vec targetVelocity,
            CoreBallistics.Vec targetHalf, double arrowRadius, int ticks) {
        if (launch == null || initialVelocity == null || center == null
                || targetVelocity == null || targetHalf == null
                || !launch.finite() || !initialVelocity.finite() || !center.finite()
                || !targetVelocity.finite() || !targetHalf.finite()
                || targetHalf.x() <= 0 || targetHalf.y() <= 0 || targetHalf.z() <= 0
                || targetHalf.x() > 32 || targetHalf.y() > 32 || targetHalf.z() > 32
                || !Double.isFinite(arrowRadius) || arrowRadius < 0 || arrowRadius > 2
                || ticks < 1 || ticks > CoreBallistics.MAX_TICKS) {
            return new Result(State.INVALID, 0, 0, Double.NaN, Double.NaN);
        }
        int count=0;
        double earliest=Double.POSITIVE_INFINITY, latest=Double.NEGATIVE_INFINITY;
        for (int model=0;model<3;model++) {
            double hit=firstHit(launch,initialVelocity,center,targetVelocity,targetHalf,
                    arrowRadius,ticks,DRAG[model],GRAVITY[model]);
            if (Double.isFinite(hit)) {
                count++;
                earliest=Math.min(earliest,hit);
                latest=Math.max(latest,hit);
            }
        }
        State state = count == 3 ? State.CONSISTENT : count == 0
                ? State.NO_CONTACT : State.DISPUTED;
        return new Result(state,count,3,count>0?earliest:Double.NaN,count>0?latest:Double.NaN);
    }
    private static double firstHit(CoreBallistics.Vec launch,CoreBallistics.Vec velocity,
            CoreBallistics.Vec center,CoreBallistics.Vec targetVelocity,
            CoreBallistics.Vec half,double radius,int ticks,double drag,double gravity) {
        var p=launch; var v=velocity; var target=center;
        for(int i=0;i<ticks;i++) {
            var next=p.add(v); var nextTarget=target.add(targetVelocity);
            if(!next.finite()||!nextTarget.finite())return Double.NaN;
            var window=ProjectileContactWindow.relativeSegment(
                    vec(p),vec(next),vec(target),vec(nextTarget),vec(half),radius);
            if(window.intersects())return i+window.first();
            p=next;target=nextTarget;
            v=new CoreBallistics.Vec(v.x()*drag,v.y()*drag-gravity,v.z()*drag);
            if(!v.finite())return Double.NaN;
        }
        return Double.NaN;
    }
    private static ProjectileContactWindow.Vec vec(CoreBallistics.Vec v) {
        return new ProjectileContactWindow.Vec(v.x(),v.y(),v.z());
    }
}
