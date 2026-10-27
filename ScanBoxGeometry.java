package dev.leadshot;

import java.util.function.Function;

/** Perspective-correct projected AABB; pure math, no rendering dependencies. */
public final class ScanBoxGeometry {
    private ScanBoxGeometry() {}
    public record Bounds(int left,int top,int right,int bottom) {}
    public static CoreBallistics.Vec interpolate(CoreBallistics.Vec from,CoreBallistics.Vec to,double tickFraction){
        if(from==null)return to;
        double t=Math.max(0,Math.min(1,Double.isFinite(tickFraction)?tickFraction:1));
        return from.times(1-t).add(to.times(t));
    }
    public static Bounds project(CoreBallistics.Vec center,CoreBallistics.Vec half,int w,int h,
                                 Function<CoreBallistics.Vec,AimProjection.Point> projector){
        if(center==null||half==null||!center.finite()||!half.finite()||
                half.x()<0||half.y()<0||half.z()<0||w<32||h<32||projector==null)return null;
        // Do not render a detached, misleading fragment when the hitbox spans
        // the near camera plane. All corners must project in front of the camera.
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(int ix=-1;ix<=1;ix+=2)for(int iy=-1;iy<=1;iy+=2)for(int iz=-1;iz<=1;iz+=2){
            CoreBallistics.Vec corner=center.add(new CoreBallistics.Vec(ix*half.x(),iy*half.y(),iz*half.z()));
            AimProjection.Point p=projector.apply(corner);
            if(p==null||!p.inFront()||!Double.isFinite(p.x())||!Double.isFinite(p.y()))return null;
            minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());
            maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());
        }
        if(maxX<2||maxY<2||minX>w-2||minY>h-2)return null;
        // For partially on-screen contacts, clip rather than moving the center.
        int l=(int)Math.max(2,Math.floor(minX)),u=(int)Math.max(2,Math.floor(minY));
        int r=(int)Math.min(w-2,Math.ceil(maxX)),d=(int)Math.min(h-2,Math.ceil(maxY));
        if(r-l<4||d-u<4)return null;
        return new Bounds(l,u,r,d);
    }
}
