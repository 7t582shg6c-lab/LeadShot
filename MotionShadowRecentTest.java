package dev.leadshot;
import java.util.UUID;
import java.util.Random;
/** Regression for recent paired stats/quantiles and bounded storage. */
public final class MotionShadowRecentTest {
    private static long assertions;
    private static void yes(boolean ok,String context) {assertions++; if(!ok)throw new AssertionError(context);}
    private static void near(double x,double y,double tolerance,String context) {
        yes(Double.isFinite(x)&&Math.abs(x-y)<tolerance,context+" "+x+" vs "+y);
    }
    public static void main(String[] args) {
        var audit=new MotionShadowAudit();
        for(int h: new int[]{5,10,20}) {
            var empty=audit.recentComparison(h);
            yes(empty.samples()==0,"no paired samples");
            yes(Double.isNaN(empty.rawP90()),"empty P90 is NaN");
            yes(!empty.hasEnoughSamples() && "INSUFFICIENT".equals(empty.bestModel()),"no superiority claim");
        }
        UUID id=new UUID(0,17);
        for(int t=0;t<5000;t++) audit.observe(id,t,.1*t,64,0);
        for(int h:new int[]{5,10,20}) {
            var c=audit.recentComparison(h);
            yes(c.samples()==256,"last 256 exact paired observations, h="+h);
            yes(c.hasEnoughSamples(),"enough samples");
            near(c.rawMae(),0,1e-9,"straight line raw mean");
            near(c.experimentalMae(),0,1e-9,"straight line model mean");
            near(c.rawP90(),0,1e-9,"straight line raw P90");
            near(c.experimentalP90(),0,1e-9,"straight line model P90");
            yes(c.winFraction()>=0&&c.winFraction()<=1,"valid win fraction");
        }
        var msg=audit.fullDiagnosticLine();
        yes(msg.contains("5t")&&msg.contains("10t")&&msg.contains("20t"),"all horizons");
        yes(!msg.contains(id.toString()),"UUID not disclosed");
        yes(!msg.contains("64."),"no coordinates");
        audit.clear();
        yes(audit.recentComparison(10).samples()==0 && audit.trackedCount()==0,"reset");
        Random r=new Random(20261027);
        for(int t=0;t<7000;t++) {
            audit.observe(id,t, t*.02 + Math.sin(t*.12)*.85 + r.nextGaussian()*.018, 0, 0);
        }
        for(int h:new int[]{5,10,20}) {
            var c=audit.recentComparison(h);
            yes(c.samples()==256,"bounded after jitter "+h);
            yes(Double.isFinite(c.rawP90()) && Double.isFinite(c.experimentalP90()),"jitter finite P90");
            yes(c.rawP90()>=c.rawMae()*0.4 && c.experimentalP90()>=c.experimentalMae()*0.4,"P90 plausible");
            yes(c.winFraction()>=0 && c.winFraction()<=1,"win fraction range");
        }
        yes(audit.recentComparison(7).samples()==0,"unsupported horizon");
        System.out.println("PASS MotionShadowRecentTest: "+assertions+" assertions; rolling recent window=256");
    }
}
