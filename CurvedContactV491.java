package dev.leadshot;

import java.util.List;

/** Time-varying moving-AABB intersection for a vanilla-like discrete arrow trace.
 * The target path contains one predicted center for EVERY flight tick. The
 * arrow trajectory and target center are linearly interpolated inside a tick
 * for swept collision; the velocity and gravity update remain discrete.
 * This is a prediction, not a Minecraft server hit confirmation.
 */
public final class CurvedContactV491 {
    private CurvedContactV491() {}
    public record Contact(boolean hit,double contactTick,double nearestCenterDistance) {}

    public static Contact evaluate(CoreBallistics.Vec origin,CoreBallistics.Vec arrowVelocity,
            List<CoreBallistics.Vec> targetCenters,CoreBallistics.Vec halfBox,double radius){
        Contact unknown=new Contact(false,Double.NaN,Double.NaN);
        if(origin==null||arrowVelocity==null||!origin.finite()||!arrowVelocity.finite()
                ||halfBox==null||!halfBox.finite()||halfBox.x()<0||halfBox.y()<0||halfBox.z()<0
                ||!Double.isFinite(radius)||radius<0||targetCenters==null
                ||targetCenters.size()<2||targetCenters.size()>CoreBallistics.MAX_TICKS+1)return unknown;
        for(CoreBallistics.Vec center:targetCenters)
            if(center==null||!center.finite())return unknown;
        var trace=CanonicalArrowV48.air(origin,arrowVelocity,targetCenters.size()-1);
        if(trace==null)return unknown;
        double[] half={halfBox.x()+radius,halfBox.y()+radius,halfBox.z()+radius};
        double nearest=Double.POSITIVE_INFINITY;
        for(int step=0;step<trace.duration();step++){
            var start=trace.at(step).position().minus(targetCenters.get(step));
            var end=trace.at(step+1).position().minus(targetCenters.get(step+1));
            double[] a={start.x(),start.y(),start.z()};
            double[] d={end.x()-start.x(),end.y()-start.y(),end.z()-start.z()};
            double enter=0,exit=1;
            for(int axis=0;axis<3;axis++){
                if(Math.abs(d[axis])<1e-12){
                    if(a[axis]<-half[axis]||a[axis]>half[axis]){enter=2;break;}
                }else{
                    double near=(-half[axis]-a[axis])/d[axis];
                    double far=(half[axis]-a[axis])/d[axis];
                    if(near>far){double temp=near;near=far;far=temp;}
                    enter=Math.max(enter,near);exit=Math.min(exit,far);
                    if(enter>exit)break;
                }
            }
            if(enter<=exit && enter<=1 && exit>=0)
                return new Contact(true,step+Math.max(0,enter),0);
            // Conservative diagnostic approximation of segment/box miss; not
            // used for release authorization or represented as exact distance.
            double sq=d[0]*d[0]+d[1]*d[1]+d[2]*d[2];
            double lambda=sq<1e-12?0:Math.max(0,Math.min(1,
                    -(a[0]*d[0]+a[1]*d[1]+a[2]*d[2])/sq));
            double gapSquared=0;
            for(int axis=0;axis<3;axis++){
                double gap=Math.max(0,Math.abs(a[axis]+d[axis]*lambda)-half[axis]);
                gapSquared+=gap*gap;
            }
            nearest=Math.min(nearest,Math.sqrt(gapSquared));
        }
        return new Contact(false,Double.NaN,nearest);
    }
}
