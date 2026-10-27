package dev.leadshot;

import java.util.ArrayList;
import java.util.List;

/** Independent shadow-mode version of the discrete air-arrow trajectory.
 * Does not claim to simulate water, random dispersion, block collision,
 * server physics, or target acceleration. No impact on firing decisions.
 */
public final class CanonicalArrowV48 {
    private CanonicalArrowV48() {}
    public record State(int tick, CoreBallistics.Vec position, CoreBallistics.Vec velocity) {}
    public record Contact(boolean hit, double localFlightTick, double minimumSeparation) {}
    public record Trace(List<State> states) {
        public Trace { states = List.copyOf(states); }
        public State at(int tick) { return tick < 0 || tick >= states.size() ? null : states.get(tick); }
        public int duration() { return states.size() - 1; }
    }
    public static Trace air(CoreBallistics.Vec origin, CoreBallistics.Vec initialVelocity, int ticks) {
        if (origin == null || initialVelocity == null || !origin.finite() || !initialVelocity.finite() ||
                ticks < 0 || ticks > CoreBallistics.MAX_TICKS) return null;
        ArrayList<State> steps = new ArrayList<>(ticks + 1);
        CoreBallistics.Vec pos=origin, vel=initialVelocity;
        steps.add(new State(0,pos,vel));
        for (int t=1; t<=ticks; t++) {
            pos=pos.add(vel);
            vel=new CoreBallistics.Vec(vel.x()*CoreBallistics.DRAG,
                    vel.y()*CoreBallistics.DRAG-CoreBallistics.GRAVITY,
                    vel.z()*CoreBallistics.DRAG);
            if (!pos.finite() || !vel.finite()) return null;
            steps.add(new State(t,pos,vel));
        }
        return new Trace(steps);
    }
    /** Target center at local shot-release time, moving at constant velocity.
     * Compare each arrow tick segment against the corresponding swept AABB.
     * Radius is a conservative *test parameter*, not a guaranteed Minecraft hitbox.
     */
    public static Contact sweptAabb(Trace path,CoreBallistics.Vec targetAtRelease,
                                    CoreBallistics.Vec targetVelocity,CoreBallistics.Vec half,
                                    double projectileRadius) {
        if (path == null || targetAtRelease == null || targetVelocity == null || half == null ||
                !targetAtRelease.finite() || !targetVelocity.finite() || !half.finite() ||
                half.x()<0 || half.y()<0 || half.z()<0 ||
                !Double.isFinite(projectileRadius) || projectileRadius<0) {
            return new Contact(false,Double.NaN,Double.NaN);
        }
        double[] extent={half.x()+projectileRadius,half.y()+projectileRadius,half.z()+projectileRadius};
        double best=Double.POSITIVE_INFINITY;
        for (int tick=0;tick<path.duration();tick++) {
            CoreBallistics.Vec target0=targetAtRelease.add(targetVelocity.times(tick));
            CoreBallistics.Vec target1=target0.add(targetVelocity);
            CoreBallistics.Vec start=path.at(tick).position().minus(target0);
            CoreBallistics.Vec end=path.at(tick+1).position().minus(target1);
            double[] a={start.x(),start.y(),start.z()}, b={end.x(),end.y(),end.z()};
            double enter=0,exit=1;
            for (int axis=0; axis<3; axis++) {
                double d=b[axis]-a[axis];
                if (Math.abs(d)<1e-12) {
                    if (a[axis]<-extent[axis] || a[axis]>extent[axis]) { enter=2; break; }
                } else {
                    double p=(-extent[axis]-a[axis])/d;
                    double q=(extent[axis]-a[axis])/d;
                    if (p>q) {double tmp=p;p=q;q=tmp;}
                    enter=Math.max(enter,p);exit=Math.min(exit,q);
                    if (enter>exit) break;
                }
            }
            if (enter<=exit && enter<=1 && exit>=0) {
                return new Contact(true,tick+Math.max(0,enter),0);
            }
            // Center-to-box separation at closest relative segment point is a
            // diagnostic lower-fidelity metric, never used for shot authorization.
            double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
            double length2=dx*dx+dy*dy+dz*dz;
            double alpha=length2<1e-12?0:Math.max(0,Math.min(1,
                    -(a[0]*dx+a[1]*dy+a[2]*dz)/length2));
            double miss2=0;
            for(int k=0;k<3;k++){
                double gap=Math.max(0,Math.abs(a[k]+(b[k]-a[k])*alpha)-extent[k]);
                miss2+=gap*gap;
            }
            best=Math.min(best,Math.sqrt(miss2));
        }
        return new Contact(false,Double.NaN,best);
    }
}
