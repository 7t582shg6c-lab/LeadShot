package dev.leadshot;
import java.util.Random;
public final class FlightPrecisionV44Test {
  private static int count,improved;
  private static void check(boolean b,String why){count++;if(!b)throw new AssertionError(why+" #"+count);}
  public static void main(String[]args){
    Random r=new Random(44263);
    for(int i=0;i<10000;i++){
      var eye=new CoreBallistics.Vec(r.nextDouble()*20-10,r.nextDouble()*30+20,r.nextDouble()*20-10);
      var centre=new CoreBallistics.Vec(r.nextDouble()*140-70,r.nextDouble()*30+20,r.nextDouble()*140-70);
      // Elytra-like horizontal/vertical player velocities. Simultaneous moving shooter.
      var v=new CoreBallistics.Vec(r.nextDouble()*5-2.5,r.nextDouble()*1.5-.75,r.nextDouble()*5-2.5);
      var shooter=new CoreBallistics.Vec(r.nextDouble()*5-2.5,r.nextDouble()*1.5-.75,r.nextDouble()*5-2.5);
      var a=CoreBallistics.solve(eye,centre,v,shooter,3);
      var b=CoreBallistics.solveRefined(eye,centre,v,shooter,3);
      check(a!=null&&b!=null,"solution exists");
      check(b.speedMismatch()<=a.speedMismatch()+1e-10,"refinement never worse");
      check(b.launchVelocity().finite()&&b.futureCenter().finite(),"finite");
      if(b.speedMismatch()+1e-5<a.speedMismatch())improved++;
    }
    check(improved>1500,"many fast moving intercepts benefit from sub-tick refinement, got "+improved);
    var z=new CoreBallistics.Vec(0,0,0);
    check(CoreBallistics.solveRefined(z,z,z,z,0)==null,"invalid speed safe");
    check(AimAssistController.step(0,0,100,0,120,true).yaw()==100,"fast steering can catch flight target");
    check(!AimAssistController.step(0,0,179,0,120,true).moved(),"does not snap behind");
    check(TargetVelocityFilter.smooth(z,new CoreBallistics.Vec(7,0,0)).x()>0,"glider speed not discarded");
    check(TargetVelocityFilter.smooth(z,new CoreBallistics.Vec(14,0,0)).length()==0,"abnormal teleport still rejected");
    var base=new CoreBallistics.Vec(0,80,0);
    check(TargetVelocityFilter.lead(base,new CoreBallistics.Vec(3,0,0),2.0).x()==6,"latency lead");
    check(TargetVelocityFilter.lead(base,new CoreBallistics.Vec(3,0,0),0).x()==0,"disable latency lead");
    System.out.println("PASS FlightPrecisionV44Test "+count+", refined intercepts="+improved);
  }
}
