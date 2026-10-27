package dev.leadshot;
/** Lightweight filtered per-tick target velocity. Null or abrupt motion resets
 * rather than allowing teleport spikes to destroy the lead prediction. */
public final class TargetVelocityFilter {
    public static CoreBallistics.Vec smooth(CoreBallistics.Vec previous,CoreBallistics.Vec observed){
        var zero=new CoreBallistics.Vec(0,0,0);
        if(observed==null||!observed.finite()||observed.length()>12.0)return zero;
        if(previous==null||!previous.finite())return observed;
        // Fast reaction when the target reverses direction, otherwise smooth jitter.
        double dot=previous.x()*observed.x()+previous.y()*observed.y()+previous.z()*observed.z();
        double gain=dot<-.002?0.95:observed.length()>1.2?0.88:0.66;
        var out=previous.times(1-gain).add(observed.times(gain));
        return out.finite()?out:zero;
    }
    /** Bounded 1-tick lead to offset client snapshot/control latency.
     * Do not extrapolate accelerated/teleporting targets across many ticks. */
    public static CoreBallistics.Vec lead(CoreBallistics.Vec center,CoreBallistics.Vec velocity){
        if(center==null||!center.finite()||velocity==null||!velocity.finite())return center;
        var displacement=velocity.times(.8);
        double length=displacement.length();
        if(length>1.25)displacement=displacement.times(1.25/length);
        return center.add(displacement);
    }
    /** Optional bounded extrapolation to bridge client interpolation/network delay.
     *  This only shifts the sampled centre; it never authorizes READY or release. */
    public static CoreBallistics.Vec lead(CoreBallistics.Vec center,CoreBallistics.Vec velocity,double latencyTicks){
        if(center==null||!center.finite()||velocity==null||!velocity.finite())return center;
        double dt=Math.max(0,Math.min(4.0,latencyTicks));
        var delta=velocity.times(dt);
        double length=delta.length();
        if(length>20)delta=delta.times(20/length);
        return center.add(delta);
    }
    private TargetVelocityFilter(){}
}
