package dev.leadshot;
import java.util.Random;
public final class ProjectedBoxTest {
    public static void main(String[] args){
        int assertions=0;
        var eye=new CoreBallistics.Vec(0,0,0);
        var center=new CoreBallistics.Vec(0,0,20);
        var half=new CoreBallistics.Vec(.4,.8,.4);
        var lines=ProjectedBox.edges(eye,center,half,0,0,800,450,70);
        if(lines.size()!=12)throw new AssertionError("full near-center box should have 12 edges: "+lines.size());assertions++;
        if(!ProjectedBox.edges(eye,center,new CoreBallistics.Vec(Double.NaN,1,1),0,0,800,450,70).isEmpty())throw new AssertionError("NaN");assertions++;
        if(!ProjectedBox.edges(eye,new CoreBallistics.Vec(0,0,-20),half,0,0,800,450,70).isEmpty())throw new AssertionError("behind");assertions++;
        Random rnd=new Random(2800);
        for(int i=0;i<10000;i++){
            double yaw=rnd.nextDouble()*360-180,pitch=rnd.nextDouble()*160-80;
            var d=AimProjection.dir(yaw,pitch);
            var c=eye.add(d.times(10+rnd.nextDouble()*40));
            var e=ProjectedBox.edges(eye,c,half,yaw,pitch,800,450,70);
            if(e.size()>12)throw new AssertionError("too many edges");assertions++;
            for(var seg:e){
                if(!seg.from().visible()||!seg.to().visible()||
                   !Double.isFinite(seg.from().x())||!Double.isFinite(seg.to().y()))throw new AssertionError("invalid projected endpoint");
                assertions++;
            }
        }
        System.out.println("PASS ProjectedBoxTest "+assertions+" assertions");
    }
}
