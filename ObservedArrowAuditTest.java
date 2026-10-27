package dev.leadshot;
import java.util.UUID;
import java.util.Random;

public final class ObservedArrowAuditTest {
    private static long n;
    private static void yes(boolean c,String reason){n++;if(!c)throw new AssertionError(reason);}
    private static CoreBallistics.Vec p(double x,double y,double z){return new CoreBallistics.Vec(x,y,z);}
    public static void main(String[] args){
        var audit=new ObservedArrowAudit();
        UUID arrow=UUID.randomUUID();
        yes(!audit.observe(10,UUID.randomUUID(),p(0,1,1),p(0,0,3)),"unrelated arrow ignored");
        yes(audit.noteUseEnd(10,p(0,1,0),p(0,0,3)),"queue release");
        yes(audit.observe(11,arrow,p(0,1,1.5),p(0,0,2.9)),"match owner-checked arrow");
        yes(!audit.observe(11,arrow,p(0,1,1.5),p(0,0,2.9)),"dedupe");
        for(int tick=12;tick<35;tick++){
            var expected=CoreBallistics.simulate(p(0,1,1.5),p(0,0,2.9),tick-11);
            yes(audit.observe(tick,arrow,expected,p(0,0,2.9)),"tracked projectile t="+tick);
        }
        var snap=audit.summary();
        yes(snap.observedLaunches()==1 && snap.trajectoryPairs()==23,"counters");
        yes(snap.averageLaunchSpeedError()>0 && snap.averageLaunchSpeedError()<.2,"launch velocity mismatch");
        yes(snap.trajectoryMeanError()<1e-8 && snap.recentP90()<1e-8,"exact trajectory");
        yes(snap.unmatchedUseEnds()==0,"no false missing");
        audit.expire(100);
        yes(audit.summary().active()==0,"active expiry");

        // An old arrow already observed BEFORE the release cannot be credited as
        // the newly fired projectile even when it flies near the shooter.
        var oldArrow=new ObservedArrowAudit();
        UUID oldId=UUID.randomUUID();
        yes(!oldArrow.observe(15,oldId,p(0,1,1),p(0,0,3)),"old arrow seen without release");
        yes(oldArrow.noteUseEnd(16,p(0,1,0),p(0,0,3)),"candidate after old arrow");
        yes(!oldArrow.observe(17,oldId,p(0,1,3),p(0,0,3)),"old arrow must not match new release");
        UUID fresh=UUID.randomUUID();
        yes(oldArrow.observe(18,fresh,p(0,1,1),p(0,0,3)),"new arrow can match");
        yes(oldArrow.summary().observedLaunches()==1,"only new arrow counted");
        var noShot=new ObservedArrowAudit();
        yes(noShot.noteUseEnd(20,p(0,0,0),p(0,0,3)),"release only");
        noShot.expire(29);
        yes(noShot.summary().useEnds()==1 && noShot.summary().observedLaunches()==0
                && noShot.summary().unmatchedUseEnds()==1,"no arrow != confirmed shot");
        yes(!noShot.observe(30,UUID.randomUUID(),p(0,0,1),p(0,0,3)),"too late");
        yes(!noShot.noteUseEnd(30,p(Double.NaN,0,0),p(0,0,3)),"nonfinite origin");
        yes(!noShot.noteUseEnd(30,p(0,0,0),p(0,0,0)),"zero speed");
        yes(!noShot.noteUseEnd(30,p(0,0,0),p(0,0,999)),"impossible speed");
        Random rng=new Random(1260);
        for(int i=0;i<12000;i++){
            var each=new ObservedArrowAudit();
            long start=100+i;
            CoreBallistics.Vec origin=p(rng.nextDouble()*8-4,2,rng.nextDouble()*8-4);
            CoreBallistics.Vec launch=p(rng.nextDouble()*1.5-.75,.25,rng.nextDouble()*2+1);
            yes(each.noteUseEnd(start,origin,launch),"random release");
            UUID id=UUID.randomUUID();
            boolean matched=each.observe(start+1,id,origin.add(launch.times(.6)),launch);
            yes(matched,"random match");
            var s=each.summary();
            yes(s.observedLaunches()==1 && s.unmatchedUseEnds()==0,"random counts");
            each.expire(start+100);
            yes(each.summary().active()==0 && each.summary().pending()==0,"random cleanup");
        }
        var capped=new ObservedArrowAudit();
        for(int i=0;i<100;i++)capped.noteUseEnd(i,p(0,0,0),p(0,0,2));
        yes(capped.summary().pending()==12,"pending bounded");
        yes(capped.summary().unmatchedUseEnds()==88,"pending evictions distinct from launch");
        capped.clear();
        yes(capped.summary().useEnds()==0 && capped.summary().observedLaunches()==0,"world reset");
        System.out.println("PASS ObservedArrowAuditTest assertions="+n);
    }
}
