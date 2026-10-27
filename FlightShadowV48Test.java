package dev.leadshot;

import java.util.Random;
import java.util.UUID;

public final class FlightShadowV48Test {
    private static long count;
    private static void require(boolean value,String reason){count++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args) {
        Random r=new Random(48048);
        for(int i=0;i<15000;i++) {
            var start=new CoreBallistics.Vec(r.nextDouble()*100-50,r.nextDouble()*80,r.nextDouble()*100-50);
            var speed=new CoreBallistics.Vec(r.nextDouble()*6-3,r.nextDouble()*3-1,r.nextDouble()*6-3);
            int t=1+r.nextInt(CoreBallistics.MAX_TICKS);
            var sim=CanonicalArrowV48.air(start,speed,t);
            var old=CoreBallistics.simulate(start,speed,t);
            require(sim!=null && old!=null,"trajectory exists");
            require(sim.at(t).position().minus(old).length()<1e-9,"same discrete recurrence");
            require(sim.states().size()==t+1,"all full-tick snapshots available");
        }
        var origin=new CoreBallistics.Vec(0,1.6,0);
        var vel=new CoreBallistics.Vec(0,0,3);
        var trace=CanonicalArrowV48.air(origin,vel,30);
        var idle=new CoreBallistics.Vec(0,0,0);
        var half=new CoreBallistics.Vec(.3,.9,.3);
        var stationary=CanonicalArrowV48.sweptAabb(trace,new CoreBallistics.Vec(0,.2,23),idle,half,.12);
        require(stationary.hit() && stationary.localFlightTick()>6,"static contact");
        var far=CanonicalArrowV48.sweptAabb(trace,new CoreBallistics.Vec(50,.2,23),idle,half,.12);
        require(!far.hit() && far.minimumSeparation()>40,"off-axis miss");
        var future=CanonicalArrowV48.sweptAabb(trace,new CoreBallistics.Vec(-8,.2,23),
                new CoreBallistics.Vec(1,0,0),half,.12);
        require(future.hit(),"moving AABB swept contact");
        var target=new TimedObservationV48(UUID.randomUUID(),5,100,
                new CoreBallistics.Vec(0,.2,23),idle,half,false);
        require(!new TimedObservationV48(target.entityId(),5,100,target.center(),idle,half,false)
                .current(9,100,2),"world epoch protects reuse");
        require(target.localAge(103)==3,"local age");
        require(target.localAge(99)==Long.MAX_VALUE,"out-of-order time invalid");
        var valid=FlightShadowV48.compare(origin,vel,target,5,101,30);
        require(valid.usable() && valid.intersects(),"shadow contact");
        require(valid.legacyEndDeviation()<1e-9,"canonical & existing terminal positions agree");
        require(!FlightShadowV48.compare(origin,vel,target,6,101,30).usable(),"wrong epoch invalid");
        require(!FlightShadowV48.compare(origin,vel,target,5,104,30).usable(),"stale invalid");
        require(CanonicalArrowV48.air(origin,vel,81)==null,"bounded work");
        System.out.println("PASS FlightShadowV48Test: "+count+" assertions (15k random air trajectories)");
    }
}
