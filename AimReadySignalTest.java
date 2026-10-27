package dev.leadshot;
import java.util.UUID;
public final class AimReadySignalTest {
 private static int n;
 private static void check(boolean v){if(!v)throw new AssertionError("assertion "+n);n++;}
 public static void main(String[] args){
   AimReadySignal a=new AimReadySignal(); UUID x=UUID.randomUUID(),y=UUID.randomUUID();
   var yes=ShotConfidence.Status.CONSISTENT_ESTIMATE;
   var no=ShotConfidence.Status.UNVERIFIED;
   check(!a.update(x,1,0,0,yes,true).ready());
   check(!a.update(x,2,0,0,yes,true).pulse());
   var hit=a.update(x,3,.3,-.6,yes,true);
   check(hit.ready()&&hit.pulse()&&hit.ticksHeld()==3);
   for(int i=4;i<=50;i++){var s=a.update(x,i,0,0,yes,true);check(s.ready()&&!s.pulse());}
   check(!a.update(x,51,0,0,no,true).ready());
   check(!a.update(x,52,1.26,0,yes,true).ready());
   check(!a.update(x,53,0,-1.26,yes,true).ready());
   check(!a.update(x,54,Double.NaN,0,yes,true).ready());
   check(!a.update(x,55,0,0,yes,false).ready());
   check(!a.update(x,56,0,0,yes,true).pulse());
   check(!a.update(x,57,0,0,yes,true).pulse());
   check(a.update(x,58,0,0,yes,true).pulse());
   check(!a.update(y,59,0,0,yes,true).ready());
   check(!a.update(y,60,0,0,yes,true).ready());
   check(!a.update(y,61,0,0,yes,true).pulse()); // 3 ticks, but global cooldown
   check(a.update(y,61,0,0,yes,true).ready());
   a.reset();check(!a.update(y,62,0,0,yes,true).ready());
   System.out.println("PASS AimReadySignalTest "+n+" assertions");
 }
}
