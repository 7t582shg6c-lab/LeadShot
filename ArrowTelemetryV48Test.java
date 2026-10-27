package dev.leadshot;

import java.util.UUID;

public final class ArrowTelemetryV48Test {
    private static void require(boolean c,String name){if(!c)throw new AssertionError(name);}
    public static void main(String[] args){
        var audit=new ArrowTelemetryV48();
        var id=UUID.randomUUID();
        var start=new CoreBallistics.Vec(0,12,0);
        var velocity=new CoreBallistics.Vec(1,.2,2);
        var step=CanonicalArrowV48.air(start,velocity,1);
        audit.observe(id,10,start,velocity);
        audit.observe(id,11,step.at(1).position(),step.at(1).velocity());
        require(audit.summary().measuredPairs()==1,"one known pair");
        require(audit.summary().rootMeanSquareError()==0,"exact vanilla recurrence");
        audit.observe(id,13,start,velocity);
        require(audit.summary().skippedGaps()==1,"tick gaps flagged");
        audit.expire(30);
        require(audit.summary().activeArrows()==0,"expiry bounds map");
        audit.clear();require(audit.summary().measuredPairs()==0,"reset");
        System.out.println("PASS ArrowTelemetryV48Test: 5 assertions");
    }
}
