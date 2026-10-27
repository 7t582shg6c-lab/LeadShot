package dev.leadshot;
import java.util.Random;
public final class IntegratedMathTest {
    private static int assertions;
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void close(double x,double y,double tolerance,String message){check(Double.isFinite(x)&&Math.abs(x-y)<=tolerance,message+": "+x+" vs "+y);}
    public static void main(String[] args){
        var zero=new CoreBallistics.Vec(0,0,0);
        for(int n=2;n<=80;n++){
            var shot=CoreBallistics.solve(zero,new CoreBallistics.Vec(0,-.1*n,3*n*.94),zero,zero,3);
            check(shot!=null,"shot null");
            var actual=CoreBallistics.simulate(zero,shot.launchVelocity(),shot.ticks());
            close(actual.minus(shot.futureCenter()).length(),0,1e-7,"ballistic inversion");
        }
        check(CoreBallistics.solve(zero,zero,zero,zero,0)==null,"reject zero speed");
        check(CoreBallistics.solve(zero,new CoreBallistics.Vec(Double.NaN,0,0),zero,zero,3)==null,"reject NaN");
        check(CoreBallistics.simulate(zero,zero,-1)==null,"reject negative ticks");
        close(CoreBallistics.vanillaBowPower(20),1,1e-12,"bow power");
        for(int yaw=-720;yaw<=720;yaw+=7){
            double wrapped=CoreBallistics.wrap(yaw);
            check(wrapped>=-180&&wrapped<180,"angle wrap");
        }
        Random random=new Random(2800);
        for(int n=0;n<15000;n++){
            double yaw=random.nextDouble()*720-360,pitch=random.nextDouble()*150-75;
            int w=128+random.nextInt(1024),h=100+random.nextInt(768);
            var d=AimProjection.dir(yaw,pitch);
            var p=AimProjection.relative(d,yaw,pitch,w,h,70);
            check(p!=null&&p.visible()&&p.inFront(),"view direction visible");
            close(p.x(),w*.5,1e-7,"center x");
            close(p.y(),h*.5,1e-7,"center y");
        }
        var right=AimProjection.angle(10,0,0,0,800,450,70);
        var left=AimProjection.angle(-10,0,0,0,800,450,70);
        check(right.x()>400&&left.x()<400,"yaw signs");
        check(!AimProjection.angle(180,0,0,0,800,450,70).inFront(),"behind not shown");
        check(!TargetStability.switchToNew(10,9,false)==false,"replacement after lost");
        check(!TargetStability.switchToNew(10,9,true),"keep previous within deadband");
        check(TargetStability.switchToNew(10,7,true),"switch when significantly better");
        check(!TargetStability.switchToNew(10,Double.NaN,true),"reject bad score");
        var moving=new CoreBallistics.Vec(0,0,17);
        var solution=CoreBallistics.solve(zero,moving,zero,zero,3);
        var contact=ContactAudit.evaluate(zero,solution,moving,zero,new CoreBallistics.Vec(.45,.9,.45),zero,3,.3);
        check(contact.state()==ContactAudit.State.PLAUSIBLE,"stationary contact by math");
        var unsupported=ContactAudit.evaluate(zero,solution,moving,zero,new CoreBallistics.Vec(.45,.9,.45),zero,3,0);
        check(unsupported.state()==ContactAudit.State.UNSUPPORTED||unsupported.state()==ContactAudit.State.PLAUSIBLE,"contact boundary");
        var flight=FlightGuidePoints.sample(zero,solution.launchVelocity(),solution.ticks());
        check(flight.size()<=12&&!flight.isEmpty(),"bounded flight dots");
        for(int i=0;i<flight.size();i++)check(flight.get(i).finite(),"finite flight");
        System.out.println("PASS IntegratedMathTest "+assertions+" assertions");
    }
}
