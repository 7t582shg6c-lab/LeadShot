package dev.leadshot;
import java.util.Random;
public final class WorldGuideGeometryTest {
    private static int checks;
    private static void check(boolean b,String s) {checks++;if(!b)throw new AssertionError(s);}
    private static CoreBallistics.Vec v(double x,double y,double z){return new CoreBallistics.Vec(x,y,z);}
    public static void main(String[] args) {
        var eye=v(0,65,0);var future=v(0,65,18);var half=v(.3,.9,.3);var initial=v(0,.5,3);
        var f=WorldGuideGeometry.build(eye,0,-10,0,0,future,half,initial,12,true);
        check(f.count(WorldGuideGeometry.Role.AIM)==16,"aim closed circle");
        check(f.count(WorldGuideGeometry.Role.FUTURE)==16,"future closed circle");
        check(f.count(WorldGuideGeometry.Role.TARGET_BOX)==12,"box has 12 edges");
        check(f.count(WorldGuideGeometry.Role.PROJECTILE_PATH)==12,"trajectory max 12");
        check(f.lines().size()==56,"bounded basic geometry");
        var noPath=WorldGuideGeometry.build(eye,0,-10,0,0,future,half,initial,12,false);
        check(noPath.lines().size()==44,"path off");
        check(WorldGuideGeometry.build(eye,0,-10,0,0,v(0,65,257),half,initial,12,true).lines().isEmpty(),"too far");
        check(WorldGuideGeometry.build(eye,0,-10,0,0,future,v(-1,1,1),initial,12,true).lines().isEmpty(),"bad halfsize");
        check(WorldGuideGeometry.build(eye,Double.NaN,-10,0,0,future,half,initial,12,true).lines().isEmpty(),"bad angles");
        check(WorldGuideGeometry.build(eye,0,-10,0,0,future,half,initial,0,true).lines().isEmpty(),"bad ticks");
        Random r=new Random(42);
        for(int i=0;i<2500;i++) {
            double yaw=r.nextDouble()*720-360, pitch=r.nextDouble()*150-75;
            double x=r.nextDouble()*40-20,y=r.nextDouble()*40-20,z=r.nextDouble()*40-20;
            var origin=v(x,y,z);
            var g=WorldGuideGeometry.build(origin,yaw,pitch,yaw,pitch,
                    origin.add(v(5,3,25)),half,initial,1+r.nextInt(80),true);
            check(g.lines().size()<=56,"max segments");
            check(g.count(WorldGuideGeometry.Role.AIM)==16,"yaw ring");
            check(g.count(WorldGuideGeometry.Role.TARGET_BOX)==12,"box");
            for(var line:g.lines()) {
                check(line.a().finite()&&line.b().finite(),"finite");
                check(line.b().minus(line.a()).length()<=256,"bounded");
            }
        }
        System.out.println("PASS: WorldGuideGeometry "+checks+" checks");
    }
}
