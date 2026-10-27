package dev.leadshot;

/** Bounded shadow diagnostic. It cannot activate READY or assisted release. */
public final class FlightShadowV48 {
    private FlightShadowV48(){}
    public record Result(boolean usable,boolean intersects,double contactTick,
                         double minimumSeparation,double legacyEndDeviation,int observedAge) {}
    public static Result compare(CoreBallistics.Vec arrowOrigin, CoreBallistics.Vec initialVelocity,
                                 TimedObservationV48 target, long worldEpoch,
                                 long nowTick,int flightTicks) {
        if (target==null || !target.current(worldEpoch,nowTick,2))return invalid();
        CanonicalArrowV48.Trace sim=CanonicalArrowV48.air(arrowOrigin,initialVelocity,flightTicks);
        if (sim==null)return invalid();
        int age=(int)target.localAge(nowTick);
        CoreBallistics.Vec targetNow=target.predictedCenter(age);
        if(targetNow==null)return invalid();
        CanonicalArrowV48.Contact collision=CanonicalArrowV48.sweptAabb(sim,targetNow,
                target.velocity(),target.halfBox(),.12);
        CoreBallistics.Vec legacy=CoreBallistics.simulate(arrowOrigin,initialVelocity,flightTicks);
        double delta=legacy==null?Double.NaN:legacy.minus(sim.at(flightTicks).position()).length();
        return new Result(true,collision.hit(),collision.localFlightTick(),
                collision.minimumSeparation(),delta,age);
    }
    private static Result invalid(){return new Result(false,false,Double.NaN,Double.NaN,Double.NaN,-1);}
}
