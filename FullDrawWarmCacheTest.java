package dev.leadshot;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FullDrawWarmCacheTest {
    private static int checks;
    private static void ok(boolean x,String why){checks++;if(!x)throw new AssertionError(why);}
    private static final CoreBallistics.Vec EYE=new CoreBallistics.Vec(0,1.6,0);
    private static final CoreBallistics.Vec V0=new CoreBallistics.Vec(0,0,0);
    private static final CoreBallistics.Vec T=new CoreBallistics.Vec(0,1,17);
    public static void main(String[] args){
        for(int mode=0;mode<4;mode++){
            var p=PrecomputePolicy.plan(mode);
            ok(p.stage()==mode,"mode changes depth, not count");
            ok(p.trackedLimit()==64,"all modes track same 64");
            ok(p.activeCandidateLimit()==12,"final scan same cap in all modes");
            ok(p.heavyUpdatesPerTick()>=0&&p.heavyUpdatesPerTick()<=8,"bounded heavy work");
        }
        ok(PrecomputePolicy.plan(0).heavyUpdatesPerTick()==0,"low no heavy work");
        ok(!PrecomputePolicy.plan(1).precomputesBallistics(),"default motion only");
        ok(PrecomputePolicy.plan(2).precomputesBallistics()&&
           !PrecomputePolicy.plan(2).precomputesAudits(),"high ballistics");
        ok(PrecomputePolicy.plan(3).precomputesAudits(),"all audits");
        var cache=new FullDrawWarmCache();
        UUID id=UUID.randomUUID();
        ok(cache.peek(id,10,EYE,T,V0,V0,5)==null,"initial miss");
        var result=cache.warm(id,10,EYE,T,V0,V0,5);
        ok(result!=null,"prewarm gets solution");
        ok(Math.abs(result.launchVelocity().length()-CoreBallistics.FULL_BOW_SPEED)<0.2,"full draw 3.0 model");
        ok(cache.size()==1&&cache.solves()==1,"one cache entry and solve");
        for(int i=0;i<1000;i++)ok(cache.warm(id,11,EYE,T,V0,V0,5)==result,"static reuse");
        ok(cache.solves()==1,"one solve with repeated lookups");
        ok(cache.peek(id,12,EYE,new CoreBallistics.Vec(.23,1,17),V0,V0,5)==null,"moving invalidates");
        ok(cache.peek(id,12,new CoreBallistics.Vec(.3,1.6,0),T,V0,V0,5)==null,"camera moved invalidates");
        ok(cache.peek(id,12,EYE,T,new CoreBallistics.Vec(.05,0,0),V0,5)==null,"velocity invalidates");
        ok(cache.peek(id,12,EYE,T,V0,new CoreBallistics.Vec(.09,0,0),5)==null,"shooter movement invalidates");
        ok(cache.peek(id,16,EYE,T,V0,V0,5)==null,"expired");
        ok(cache.peek(id,9,EYE,T,V0,V0,5)==null,"no future entries");
        var newer=cache.warm(id,16,EYE,T,V0,V0,5);
        ok(newer!=null&&cache.solves()==2,"stale recomputed");
        cache.retain(Set.of());ok(cache.size()==0,"evict death");
        for(int i=0;i<100;i++){
            UUID key=UUID.randomUUID();
            var target=new CoreBallistics.Vec(i%8,1,14+i/8);
            cache.warm(key,20,EYE,target,V0,V0,2);
        }
        ok(cache.size()<=64,"64 hard memory cap");
        cache.clear();ok(cache.size()==0&&cache.hits()==0&&cache.misses()==0&&cache.solves()==0,"reset");
        System.out.println("PASS FullDrawWarmCacheTest: "+checks+" assertions");
    }
}
