package dev.leadshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Projectable 3D world-space ring geometry for a future renderer. Currently drawn
 * through the HUD, WITHOUT depth testing. Every returned line has two strictly
 * visible/projectable endpoints, avoiding clipped giant primitives.
 */
public final class ProjectedAimRings {
    private ProjectedAimRings() {}
    public static List<ProjectedBox.Segment> aimRing(CoreBallistics.Vec eye,
            double aimYaw,double aimPitch,double cameraYaw,double cameraPitch,
            int width,int height,int fov,double distance) {
        if(eye==null || !eye.finite()|| !Double.isFinite(distance)||distance<2||distance>64)
            return List.of();
        var center=eye.add(AimProjection.dir(aimYaw,aimPitch).times(distance));
        return cameraFacingRing(eye,center,cameraYaw,cameraPitch,width,height,fov,.28,16);
    }
    public static List<ProjectedBox.Segment> targetRing(CoreBallistics.Vec eye,
            CoreBallistics.Vec target,double cameraYaw,double cameraPitch,
            int width,int height,int fov,double radius) {
        return cameraFacingRing(eye,target,cameraYaw,cameraPitch,width,height,fov,radius,16);
    }
    public static List<ProjectedBox.Segment> cameraFacingRing(CoreBallistics.Vec eye,
            CoreBallistics.Vec center,double cameraYaw,double cameraPitch,
            int width,int height,int fov,double radius,int segments) {
        if (eye==null || center==null || !eye.finite() || !center.finite()
                || !Double.isFinite(cameraYaw) || !Double.isFinite(cameraPitch)
                || !Double.isFinite(radius) || radius<=0 || radius>8
                || segments<4 || segments>64) return List.of();
        double yaw=Math.toRadians(cameraYaw),pitch=Math.toRadians(cameraPitch);
        // Camera basis: Minecraft yaw+ => rightward motion corresponds to -X.
        CoreBallistics.Vec right=new CoreBallistics.Vec(-Math.cos(yaw),0,-Math.sin(yaw));
        CoreBallistics.Vec up=new CoreBallistics.Vec(-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),
                Math.cos(yaw)*Math.sin(pitch));
        var output=new ArrayList<ProjectedBox.Segment>(segments);
        CoreBallistics.Vec previous=null;
        for(int i=0;i<=segments;i++) {
            double angle=2*Math.PI*i/segments;
            CoreBallistics.Vec pt=center.add(right.times(radius*Math.cos(angle)))
                    .add(up.times(radius*Math.sin(angle)));
            if(previous!=null) {
                var a=AimProjection.world(eye,previous,cameraYaw,cameraPitch,width,height,fov);
                var b=AimProjection.world(eye,pt,cameraYaw,cameraPitch,width,height,fov);
                if(a!=null && b!=null && a.inFront() && b.inFront() && a.visible() && b.visible())
                    output.add(new ProjectedBox.Segment(a,b));
            }
            previous=pt;
        }
        return List.copyOf(output);
    }
}
