package dev.leadshot;
public final class TargetVelocityFilterTest {
 static int n;
 static void ok(boolean v){if(!v)throw new AssertionError("velocity assertion "+n);n++;}
 public static void main(String[] args){
  var zero=new CoreBallistics.Vec(0,0,0);
  var positive=new CoreBallistics.Vec(.35,0,0);
  var estimate=TargetVelocityFilter.smooth(zero,positive);
  ok(estimate.x()>.2&&estimate.x()<.35);
  var reverse=TargetVelocityFilter.smooth(estimate,new CoreBallistics.Vec(-.5,0,0));
  ok(reverse.x()<0);
  var teleport=TargetVelocityFilter.smooth(estimate,new CoreBallistics.Vec(14,0,0));
  ok(teleport.length()==0);
  var center=new CoreBallistics.Vec(20,4,0);
  var future=TargetVelocityFilter.lead(center,positive);
  ok(future.x()>20);
  var huge=TargetVelocityFilter.lead(center,new CoreBallistics.Vec(3.0,0,0));
  ok(huge.minus(center).length()<=1.250000000001);
  for(int i=0;i<1000;i++){
   double raw=Math.sin(i/30.0)*.4+Math.cos(i)*.013;
   estimate=TargetVelocityFilter.smooth(estimate,new CoreBallistics.Vec(raw,0,0));
   ok(estimate.finite()&&estimate.length()<3.5);
  }
  System.out.println("PASS TargetVelocityFilterTest "+n+" assertions");
 }
}
