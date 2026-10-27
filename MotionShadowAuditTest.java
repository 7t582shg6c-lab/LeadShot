package dev.leadshot;
import java.util.UUID;
import java.util.Random;
public final class MotionShadowAuditTest {
    private static long count;
    private static void check(boolean ok, String what) {count++;if(!ok)throw new AssertionError(what);}
    private static void close(double a,double b,double tol,String label){check(Math.abs(a-b)<tol,label+": "+a+" vs "+b);}
    private static void runLinear(){
        MotionShadowAudit a=new MotionShadowAudit();UUID id=UUID.randomUUID();
        for(int tick=0;tick<500;tick++)a.observe(id,tick,0.25*tick,64,0);
        for(int h:new int[]{5,10,20}) {
            var s=a.comparison(h);check(s.pairs()>30,"linear paired  "+h);
            close(s.rawDeltaMae(),0,1e-8,"constant velocity raw");
            close(s.experimentalMae(),0,1e-8,"constant velocity filter");
        }
        check(a.trackedCount()==1,"tracked count");
        String line=a.diagnosticLine();
        check(line.contains("10t")&&!line.contains(id.toString()),"anonymous summary");
    }
    private static void gapAndTeleport(){
        MotionShadowAudit a=new MotionShadowAudit();UUID id=UUID.randomUUID();
        for(int i=0;i<45;i++) a.observe(id,i,.15*i,0,0);
        a.observe(id,45,1200,0,0); // jump clears pending
        for(int i=46;i<70;i++) a.observe(id,i,1200+.1*(i-45),0,0);
        a.observe(id,78,1210,0,0); // missing due observation clears pending
        for(int i=79;i<90;i++) a.observe(id,i,1210+.1*(i-78),0,0);
        check(a.comparison(10).pairs()>=1,"paired valid");
        a.expire(10000);check(a.trackedCount()==0,"stale expiry");
        a.clear();check(a.comparison(5).pairs()==0,"reset stat");
    }
    private static void bounded(){
        MotionShadowAudit a=new MotionShadowAudit();
        for(int i=0;i<500;i++)a.observe(new UUID(0,i),i,0,0,0);
        check(a.trackedCount()<=128,"cap tracks");
        a.observe(new UUID(1,1),500,Double.NaN,0,0);
        check(a.trackedCount()<=128,"bad sample ignored");
        var c=a.comparison(7);check(c.pairs()==0&&Double.isNaN(c.experimentalMae()),"unsupported horizon");
    }
    private static void randomNoise(){
        MotionShadowAudit a=new MotionShadowAudit();UUID id=UUID.randomUUID();Random r=new Random(20261027);
        for(int t=0;t<5000;t++) {
            double x=t*.18+Math.sin(t/17.0)*.8+r.nextGaussian()*.03;
            a.observe(id,t,x,40+Math.sin(t/21.0)*.1,0);
        }
        for(int h:new int[]{5,10,20}) {
            var c=a.comparison(h);
            check(c.pairs()>100,"samples random");
            check(c.rawDeltaMae()>=0&&Double.isFinite(c.rawDeltaMae()),"raw finite");
            check(c.experimentalMae()>=0&&Double.isFinite(c.experimentalMae()),"experimental finite");
        }
    }
    public static void main(String[] args){runLinear();gapAndTeleport();bounded();randomNoise();System.out.println("PASS MotionShadowAuditTest: "+count+" assertions");}
}
