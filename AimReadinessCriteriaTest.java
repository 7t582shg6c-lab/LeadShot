package dev.leadshot;
import java.util.UUID;
public final class AimReadinessCriteriaTest {
 static int n;
 static void ok(boolean b){if(!b)throw new AssertionError("ready assertion "+n);n++;}
 public static void main(String[] args){
  var half=new CoreBallistics.Vec(.31,.9,.31);
  var near=AimReadinessCriteria.bounds(8,half,0);
  var far=AimReadinessCriteria.bounds(40,half,0);
  var moving=AimReadinessCriteria.bounds(40,half,.6);
  ok(near.yawDegrees()>far.yawDegrees());
  ok(far.yawDegrees()>=.16&&far.yawDegrees()<=.78);
  ok(far.pitchDegrees()>=.16&&far.pitchDegrees()<=.78);
  ok(moving.yawDegrees()<=far.yawDegrees());
  ok(moving.pitchDegrees()<=far.pitchDegrees());
  var signal=new AimReadySignal(); UUID target=UUID.randomUUID();
  var status=ShotConfidence.Status.CONSISTENT_ESTIMATE;
  ok(!signal.update(target,1,.65,.1,status,true,far.yawDegrees(),far.pitchDegrees()).ready());
  for(int t=2;t<=4;t++){
    var s=signal.update(target,t,0,0,status,true,far.yawDegrees(),far.pitchDegrees());
    ok(s.ready()==(t==4));
  }
  for(int t=5;t<100;t++){
    var s=signal.update(target,t,0,0,status,true,far.yawDegrees(),far.pitchDegrees());
    ok(s.ready()&&!s.pulse());
  }
  ok(!signal.update(target,101,far.yawDegrees()+.3,0,status,true,far.yawDegrees(),far.pitchDegrees()).ready());
  System.out.println("PASS AimReadinessCriteriaTest "+n+" assertions");
 }
}
