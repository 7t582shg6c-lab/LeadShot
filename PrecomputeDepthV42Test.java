package dev.leadshot;

import java.util.*;

/** Validate ALL 64 share the same depth policy, with no permanent top-N bias. */
public final class PrecomputeDepthV42Test {
    private static int checks;
    private static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        List<UUID> sixtyFour=new ArrayList<>();
        for(int i=0;i<64;i++)sixtyFour.add(new UUID(0,i+1));
        for(int mode=0;mode<4;mode++){
            PrecomputePolicy.Plan plan=PrecomputePolicy.plan(mode);
            var cache=new StagedPrecomputeCache();
            var roundRobin=new RoundRobinPrecompute();
            var visited=new HashSet<UUID>();
            for(int tick=1;tick<=48;tick++){
                for(int i=0;i<64;i++){
                    UUID id=sixtyFour.get(i);
                    var pos=new CoreBallistics.Vec(i*.25,1.0,15);
                    var vel=new CoreBallistics.Vec(.01,0,0);
                    cache.observe(id,tick,pos,vel,plan.precomputesMotion());
                }
                for(UUID id:roundRobin.next(sixtyFour,plan.heavyUpdatesPerTick(),java.util.function.Function.identity())){
                    visited.add(id);
                    cache.ballistic(id,tick);
                    if(plan.precomputesAudits()){
                        var contact=new ContactAudit.Result(ContactAudit.State.PLAUSIBLE,10);
                        var block=new BallisticOcclusion.Result(BallisticOcclusion.State.CLEAR,10,10,Double.NaN);
                        var consensus=new TrajectoryConsensus.Result(TrajectoryConsensus.State.CONSISTENT,3,3,9,11);
                        cache.audited(id,tick,new StagedPrecomputeCache.Audit(contact,block,consensus));
                    }
                }
                ok(cache.size()==64,"all 64 tracked in mode="+mode);
                ok(cache.countAtStage(PrecomputePolicy.LOW,tick,0)==64,"64 fresh tracking entries");
                if(plan.precomputesMotion()){
                    ok(cache.countAtStage(PrecomputePolicy.DEFAULT,tick,0)==64,"64 motion predictions");
                    for(UUID id:sixtyFour){
                        var future=cache.motion(id);
                        ok(future!=null && Math.abs(future.at20().x()-
                                (sixtyFour.indexOf(id)*.25+.2))<1e-10,"projected all 64");
                    }
                }else ok(cache.countAtStage(PrecomputePolicy.DEFAULT,tick,0)==0,"low never predicts");
                if(!plan.precomputesBallistics())ok(visited.isEmpty(),"no high math in low/default");
                if(!plan.precomputesAudits())ok(cache.countAtStage(PrecomputePolicy.ALL,tick,24)==0,
                        "no unrequested audits");
            }
            if(plan.precomputesBallistics()){
                ok(visited.size()==64,"all 64 eventually receive full-power calculation");
                ok(cache.countAtStage(PrecomputePolicy.HIGH,48,plan.cacheTtlTicks())==64,
                        "all 64 currently warmed at end");
            }
            if(plan.precomputesAudits()){
                ok(cache.countAtStage(PrecomputePolicy.ALL,48,plan.cacheTtlTicks())==64,
                        "all 64 receive full audit stage");
                for(UUID id:sixtyFour)ok(cache.audit(id,48,24)!=null,"audits retained");
            }
            cache.retain(Set.of(sixtyFour.get(0)));
            ok(cache.size()==1,"removed tracked targets evicted");
            cache.clear();ok(cache.size()==0,"world/reset clears");
        }
        var policy=PrecomputePolicy.plan(PrecomputePolicy.ALL);
        var scheduler=new RoundRobinPrecompute();
        ok(scheduler.next(sixtyFour,policy.heavyUpdatesPerTick(),java.util.function.Function.identity()).size()==4,"bounded ALL per tick");
        scheduler.reset();
        ok(scheduler.next(sixtyFour,4,java.util.function.Function.identity()).equals(sixtyFour.subList(0,4)),"reset visit history");
        // Reordering the list every tick must NOT starve any UUID.
        scheduler.reset();
        var reorder=new ArrayList<>(sixtyFour);
        Set<UUID> fair=new HashSet<>();
        for(int step=0;step<16;step++){
            Collections.rotate(reorder,step*3);
            fair.addAll(scheduler.next(reorder,4,java.util.function.Function.identity()));
        }
        ok(fair.size()==64,"64 UUIDs visited despite changing distance order");
        var store=new StagedPrecomputeCache();
        UUID moving=UUID.randomUUID();
        store.observe(moving,100,new CoreBallistics.Vec(0,0,0),new CoreBallistics.Vec(0,0,0),true);
        store.ballistic(moving,100);
        ok(store.countAtStage(PrecomputePolicy.HIGH,100,24)==1,"initial ballistic status");
        store.observe(moving,101,new CoreBallistics.Vec(3,0,0),new CoreBallistics.Vec(0,0,0),true);
        ok(store.countAtStage(PrecomputePolicy.HIGH,101,24)==0,"motion invalidates stale ballistic state");
        ok(store.countAtStage(PrecomputePolicy.HIGH,100,24)==0,"no future cache validity");
        System.out.println("PASS PrecomputeDepthV42Test: "+checks+" assertions");
    }
}
