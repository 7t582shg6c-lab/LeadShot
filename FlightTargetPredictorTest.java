package dev.leadshot;
import java.util.Random;
public final class FlightTargetPredictorTest {
  private static int count;
  private static void check(boolean b){count++;if(!b)throw new AssertionError("flight predictor test #"+count);}
  public static void main(String[]args){
    var previous=new CoreBallistics.Vec(2.6,0,.1);
    var current=new CoreBallistics.Vec(2.5,0,.4);
    var fast=FlightTargetPredictor.forecast(current,previous);
    check(fast.z()>current.z()); // anticipates changing glide direction
    check(fast.minus(current).length()<=.5500001);
    check(FlightTargetPredictor.forecast(new CoreBallistics.Vec(.2,0,0),previous).x()==.2);
    Random r=new Random(44);
    for(int i=0;i<15000;i++){
      var a=new CoreBallistics.Vec(r.nextDouble()*8-4,r.nextDouble()*8-4,r.nextDouble()*8-4);
      var b=new CoreBallistics.Vec(r.nextDouble()*8-4,r.nextDouble()*8-4,r.nextDouble()*8-4);
      var lead=FlightTargetPredictor.forecast(a,b);
      check(lead.finite());
      check(lead.minus(a).length()<=.5500001);
    }
    System.out.println("PASS FlightTargetPredictorTest "+count);
  }
}
