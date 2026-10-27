package dev.leadshot;
import java.util.Random;
public final class ProjectedAimRingsTest {
    private static int checks;
    private static void ok(boolean success,String msg){checks++;if(!success)throw new AssertionError(msg);}
    public static void main(String[] args){
        var eye=new CoreBallistics.Vec(0,64,0);
        var ring=ProjectedAimRings.aimRing(eye,0,0,0,0,800,450,70,10);
        ok(ring.size()==16,"full centered aim ring");
        ok(ProjectedAimRings.targetRing(eye,new CoreBallistics.Vec(0,64,-10),0,0,800,450,70,.5).isEmpty(),"back-facing ring");
        ok(ProjectedAimRings.cameraFacingRing(null,eye,0,0,800,450,70,.5,16).isEmpty(),"reject null");
        ok(ProjectedAimRings.cameraFacingRing(eye,eye,0,0,800,450,70,Double.NaN,16).isEmpty(),"reject nan");
        ok(ProjectedAimRings.cameraFacingRing(eye,eye,0,0,800,450,70,.5,100).isEmpty(),"reject huge segments");
        Random rng=new Random(438133);
        for(int i=0;i<10000;i++){
            double yaw=rng.nextDouble()*720-360;
            double pitch=rng.nextDouble()*130-65;
            var result=ProjectedAimRings.aimRing(eye,yaw,pitch,yaw,pitch,800,450,70,12);
            ok(result.size()==16,"all visible when aim=camera");
            for(var segment:result){
                ok(segment.from().visible() && segment.to().visible(),"visible segments");
                ok(segment.from().x()>=0 && segment.from().x()<=800 && segment.to().x()>=0 && segment.to().x()<=800,"bounded x");
                ok(segment.from().y()>=0 && segment.from().y()<=450 && segment.to().y()>=0 && segment.to().y()<=450,"bounded y");
            }
        }
        System.out.println("PASS ProjectedAimRingsTest "+checks+" assertions");
    }
}
