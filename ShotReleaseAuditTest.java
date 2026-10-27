package dev.leadshot;

import java.util.UUID;

public final class ShotReleaseAuditTest {
    private static long assertions;
    static void check(boolean ok,String desc) {
        assertions++;
        if (!ok) throw new AssertionError(desc+" assertion "+assertions);
    }
    static CoreBallistics.Vec v(double x,double y,double z) {return new CoreBallistics.Vec(x,y,z);}
    static void near(double a,double b,double delta,String desc) {
        check(Double.isFinite(a)&&Math.abs(a-b)<delta,desc+" "+a+" != "+b);
    }
    public static void main(String[] args) {
        var a = new ShotReleaseAudit();
        UUID target=UUID.randomUUID(), wrong=UUID.randomUUID();
        check(a.pendingCount()==0,"empty");
        check(!a.recordUseEnd(null,0,10,v(1,2,3)),"reject null");
        check(!a.recordUseEnd(target,0,0,v(1,2,3)),"reject zero ticks");
        check(!a.recordUseEnd(target,0,121,v(1,2,3)),"reject too long");
        check(!a.recordUseEnd(target,-1,2,v(1,2,3)),"reject negative tick");
        check(!a.recordUseEnd(target,0,5,v(Double.NaN,2,3)),"reject nan");
        check(!a.recordUseEnd(target,0,5,v(30_000_001,2,3)),"reject bad world limit");
        check(a.recordUseEnd(target,100,10,v(5,0,0)),"record valid");
        a.observe(target,109,v(10,0,0));
        a.observe(wrong,110,v(10,0,0));
        check(a.summary().recentPairs()==0 && a.pendingCount()==1,"exact tick and correct ID");
        a.observe(target,110,v(4,0,0));
        near(a.summary().recentMean(),1,1e-8,"exact error");
        near(a.summary().recentP90(),1,1e-8,"single p90");
        check(a.pendingCount()==0,"consume once");
        a.observe(target,110,v(4,0,0));
        check(a.summary().allPairs()==1,"do not double count");
        for (int i=0;i<4000;i++) {
            check(a.recordUseEnd(target,1000L+i*2,1,v(0,0,0)),"record stress");
            a.observe(target,1001L+i*2,v((i%17)/2.0,0,0));
            check(a.pendingCount()==0,"no pending stress");
            check(a.summary().recentPairs()<=256,"bounded ring");
        }
        check(a.summary().allPairs()==4001,"lifetime count");
        check(a.summary().recentPairs()==256,"latest 256");
        check(a.summary().enoughSamples(),"sample threshold");
        near(a.summary().recentMean(),4,1,"sample mean reasonable");
        check(a.summary().recentP90()>=a.summary().recentMean(),"p90 above mean");
        for(int i=0;i<80;i++)check(a.recordUseEnd(target,20_000,30,v(1,0,0)),"pending cap");
        check(a.pendingCount()==64,"pending bounded");
        check(a.summary().expired()>=16,"evicted counted unknown");
        a.expire(20_031);
        check(a.pendingCount()==0,"expired due");
        check(a.summary().expired()>=80,"expiration is unknown not hit");
        a.clear();
        check(a.summary().allPairs()==0 && a.pendingCount()==0 && a.summary().recentPairs()==0,"world reset");
        check(a.summary().recentPairs()==0 && Double.isNaN(a.summary().recentP90()),"empty p90");
        System.out.println("PASS: ShotReleaseAudit "+assertions+" assertions");
    }
}
