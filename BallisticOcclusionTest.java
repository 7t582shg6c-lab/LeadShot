package dev.leadshot;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** Executed against production pure Java trajectory code, no game stubs. */
public final class BallisticOcclusionTest {
    private static long assertions;
    private static void check(boolean ok,String message) {
        assertions++;
        if(!ok)throw new AssertionError(message);
    }
    private static final CoreBallistics.Vec ZERO = new CoreBallistics.Vec(0,0,0);
    private static final CoreBallistics.Vec START = new CoreBallistics.Vec(0,1,0);
    private static final CoreBallistics.Vec VELOCITY = new CoreBallistics.Vec(0,0,3);

    public static void main(String[] args) {
        var clear=BallisticOcclusion.evaluate(START,VELOCITY,14,(a,b)->BallisticOcclusion.ProbeResult.CLEAR);
        check(clear.state()==BallisticOcclusion.State.CLEAR && clear.testedSegments()==14,"clear 14");
        var blocked=BallisticOcclusion.evaluate(START,VELOCITY,14,(a,b)->b.z()>=12
                ?BallisticOcclusion.ProbeResult.BLOCKED:BallisticOcclusion.ProbeResult.CLEAR);
        check(blocked.state()==BallisticOcclusion.State.BLOCKED && blocked.testedSegments()==5,"block segment 5");
        var unknown=BallisticOcclusion.evaluate(START,VELOCITY,10,(a,b)->
                b.z()>5?BallisticOcclusion.ProbeResult.UNLOADED:BallisticOcclusion.ProbeResult.CLEAR);
        check(unknown.state()==BallisticOcclusion.State.UNCHECKED,"unloaded");
        var limit=BallisticOcclusion.evaluate(START,VELOCITY,80,(a,b)->BallisticOcclusion.ProbeResult.CLEAR);
        check(limit.state()==BallisticOcclusion.State.UNCHECKED && limit.testedSegments()==48
                && limit.requestedSegments()==80,"budget exhaust");
        var invalid=BallisticOcclusion.evaluate(START,VELOCITY,Double.NaN,
                (a,b)->BallisticOcclusion.ProbeResult.CLEAR);
        check(invalid.state()==BallisticOcclusion.State.INVALID,"invalid time");
        var noProbe=BallisticOcclusion.evaluate(START,VELOCITY,1,null);
        check(noProbe.state()==BallisticOcclusion.State.INVALID,"missing probe");
        var zero=BallisticOcclusion.evaluate(START,VELOCITY,0,(a,b)->BallisticOcclusion.ProbeResult.CLEAR);
        check(zero.state()==BallisticOcclusion.State.CLEAR && zero.testedSegments()==0,"zero time");
        var exception=BallisticOcclusion.evaluate(START,VELOCITY,4,(a,b)->{throw new IllegalStateException();});
        check(exception.state()==BallisticOcclusion.State.UNCHECKED,"exception safe");
        var nullProbe=BallisticOcclusion.evaluate(START,VELOCITY,4,(a,b)->null);
        check(nullProbe.state()==BallisticOcclusion.State.UNCHECKED,"null probe conservative");
        var partial=BallisticOcclusion.evaluate(START,VELOCITY,1.5,(a,b)->BallisticOcclusion.ProbeResult.CLEAR);
        check(partial.testedSegments()==2&&partial.state()==BallisticOcclusion.State.CLEAR,"partial count");
        AtomicInteger calls=new AtomicInteger();
        BallisticOcclusion.evaluate(START,VELOCITY,3.5,(a,b)->{
            int call=calls.incrementAndGet();
            if(call==4)check(Math.abs(b.z()-a.z()-3*Math.pow(.99,3)*.5)<1e-8,"fractional end");
            return BallisticOcclusion.ProbeResult.CLEAR;
        });
        check(calls.get()==4,"partial tick length");
        Random rng=new Random(81723);
        for(int i=0;i<20000;i++){
            int duration=1+rng.nextInt(100);
            int block=1+rng.nextInt(duration);
            int[] n={0};
            var result=BallisticOcclusion.evaluate(START,VELOCITY,duration,(a,b)->
                    ++n[0]==block?BallisticOcclusion.ProbeResult.BLOCKED:BallisticOcclusion.ProbeResult.CLEAR);
            if(block<=BallisticOcclusion.MAX_RAYS) {
                check(result.state()==BallisticOcclusion.State.BLOCKED,"hit within budget");
                check(result.testedSegments()==block,"hit count");
                check(Math.abs(result.firstBlockedTick()-block)<1e-9,"hit t");
            } else {
                check(result.state()==BallisticOcclusion.State.UNCHECKED,"no hidden assumption");
                check(result.testedSegments()==BallisticOcclusion.MAX_RAYS,"limit count");
            }
            check(result.testedSegments()<=BallisticOcclusion.MAX_RAYS,"max rays");
        }
        System.out.println("PASS BallisticOcclusionTest: "+assertions+" assertions");
    }
}
