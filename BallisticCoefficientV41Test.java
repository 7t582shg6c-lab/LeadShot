package dev.leadshot;
import java.util.Random;
public final class BallisticCoefficientV41Test {
    private static int assertions;
    private static void check(boolean ok,String reason){ assertions++;if(!ok)throw new AssertionError(reason); }
    /** Original v0.40 equations, used as a regression reference. */
    private static CoreBallistics.Solution original(CoreBallistics.Vec eye,CoreBallistics.Vec center,
            CoreBallistics.Vec velocity,CoreBallistics.Vec shooter,double speed){
        var best=(CoreBallistics.Solution)null;
        for(int n=2;n<=CoreBallistics.MAX_TICKS;n++){
            double s=(1-Math.pow(CoreBallistics.DRAG,n))/(1-CoreBallistics.DRAG);
            double fall=CoreBallistics.GRAVITY*(n-s)/(1-CoreBallistics.DRAG);
            var future=center.add(velocity.times(n));
            var v=future.minus(eye).add(new CoreBallistics.Vec(0,fall,0)).times(1/s).minus(shooter);
            double mismatch=Math.abs(v.length()-speed);
            if(v.finite() && (best==null || mismatch<best.speedMismatch()))
                best=new CoreBallistics.Solution(v,future,n,mismatch);
        }
        return best;
    }
    public static void main(String[] args){
        Random rnd=new Random(4100263);
        for(int i=0;i<16000;i++){
            var eye=new CoreBallistics.Vec(rnd.nextDouble()*20-10,rnd.nextDouble()*7+1,rnd.nextDouble()*20-10);
            var target=new CoreBallistics.Vec(rnd.nextDouble()*120-60,rnd.nextDouble()*16-3,rnd.nextDouble()*120-60);
            var velocity=new CoreBallistics.Vec(rnd.nextDouble()*.9-.45,rnd.nextDouble()*.2-.1,rnd.nextDouble()*.9-.45);
            var shooter=new CoreBallistics.Vec(rnd.nextDouble()*.25-.125,0,rnd.nextDouble()*.25-.125);
            double speed=CoreBallistics.FULL_BOW_SPEED;
            var fast=CoreBallistics.solve(eye,target,velocity,shooter,speed);
            var slow=original(eye,target,velocity,shooter,speed);
            check(fast!=null && slow!=null,"nonnull solutions");
            check(Math.abs(fast.speedMismatch()-slow.speedMismatch())<1e-10,"same optimum mismatch");
            check(Math.abs(fast.ticks()-slow.ticks())<=1 || Math.abs(fast.speedMismatch()-slow.speedMismatch())<1e-12,
                    "same minimum choice or near-tie");
            var hit=CoreBallistics.simulate(eye,fast.launchVelocity().add(shooter),fast.ticks());
            check(hit.minus(fast.futureCenter()).length()<1e-7,"discrete physics parity");
        }
        System.out.println("PASS BallisticCoefficientV41Test: "+assertions+" checks");
    }
}
