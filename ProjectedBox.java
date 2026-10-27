package dev.leadshot;
import java.util.ArrayList;
import java.util.List;

/** Screen-space approximation of a future 3D AABB, not occlusion-tested. */
public final class ProjectedBox {
    private ProjectedBox(){}
    public record Segment(AimProjection.Point from,AimProjection.Point to) {}
    private static final int[][] EDGES={
            {0,1},{0,2},{0,4},{1,3},{1,5},{2,3},
            {2,6},{3,7},{4,5},{4,6},{5,7},{6,7}
    };
    /** Edges are only drawn when BOTH endpoints are within the viewport. */
    public static List<Segment> edges(CoreBallistics.Vec eye,CoreBallistics.Vec center,
             CoreBallistics.Vec half,double yaw,double pitch,int width,int height,int fov){
        if(eye==null||center==null||half==null||!eye.finite()||!center.finite()||!half.finite()||
           half.x()<0||half.y()<0||half.z()<0||half.x()>32||half.y()>32||half.z()>32)return List.of();
        AimProjection.Point[] pts=new AimProjection.Point[8];
        for(int i=0;i<8;i++){
            var corner=center.add(new CoreBallistics.Vec(
                  (i&1)==0?-half.x():half.x(),
                  (i&2)==0?-half.y():half.y(),
                  (i&4)==0?-half.z():half.z()));
            pts[i]=AimProjection.world(eye,corner,yaw,pitch,width,height,fov);
        }
        List<Segment> lines=new ArrayList<>(12);
        for(int[] e:EDGES){
            var a=pts[e[0]];var b=pts[e[1]];
            if(a!=null&&b!=null&&a.inFront()&&b.inFront()&&a.visible()&&b.visible())lines.add(new Segment(a,b));
        }
        return List.copyOf(lines);
    }
}
