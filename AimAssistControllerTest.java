package dev.leadshot;
public final class AimAssistControllerTest {
 private static int n=0;
 private static void check(boolean good){if(!good)throw new AssertionError("test "+n);n++;}
 private static void near(double actual,double expected){check(Math.abs(actual-expected)<1e-6);}
 public static void main(String[] args){
   var disabled=AimAssistController.step(0,0,20,10,3,false);
   check(!disabled.moved());near(disabled.yaw(),0);near(disabled.pitch(),0);
   var step=AimAssistController.step(0,0,30,0,2,true);
   check(step.moved());near(step.yaw(),2);near(step.pitch(),0);check(!step.aligned());
   var diag=AimAssistController.step(0,0,3,4,2,true);
   near(diag.yaw(),1.2);near(diag.pitch(),1.6);
   var wrapped=AimAssistController.step(179,0,-179,0,1,true);
   near(wrapped.yaw(),180);near(wrapped.pitch(),0);
   near(AimAssistController.step(0,0,100,0,4,true).yaw(),0); // no snap across huge angle
   near(AimAssistController.step(0,0,Double.NaN,0,4,true).yaw(),0);
   near(AimAssistController.step(0,0,5,0,0,true).yaw(),0);
   check(AimAssistController.step(0,0,0.1,0.1,3,true).aligned());
   for(int i=0;i<240;i++){
     double y=(i%2==0?179:-179);
     double to=(i%3==0?-179:179);
     var j=AimAssistController.step(y,0,to,0,1.5,true);
     check(Math.abs(CoreBallistics.wrap(j.yaw()-y))<=1.500001);
     check(Double.isFinite(j.yaw()));
   }
   double yaw=0,pitch=0;
   for(int k=0;k<100;k++){
     var z=AimAssistController.step(yaw,pitch,25,-14,1.5,true);
     yaw=z.yaw();pitch=z.pitch();
   }
   near(yaw,25);near(pitch,-14);
   System.out.println("PASS AimAssistControllerTest "+n+" assertions");
 }
}
