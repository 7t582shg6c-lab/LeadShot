package dev.leadshot;

import java.util.UUID;

/** Conservative client-side "steady aim" transition detector, NOT a hit guarantee. */
public final class AimReadySignal {
    public record State(boolean ready, boolean pulse, int ticksHeld) {}
    private UUID lastTarget;
    private int held;
    private boolean wasReady;
    private long lastPulseTick=Long.MIN_VALUE;

    public void reset(){lastTarget=null;held=0;wasReady=false;lastPulseTick=Long.MIN_VALUE;}

    public State update(UUID target,long tick,double yawError,double pitchError,
                        ShotConfidence.Status confidence,boolean charged){
        if(target==null||!charged||confidence!=ShotConfidence.Status.CONSISTENT_ESTIMATE
                ||!Double.isFinite(yawError)||!Double.isFinite(pitchError)
                ||Math.abs(yawError)>1.25||Math.abs(pitchError)>1.25){
            held=0;wasReady=false;lastTarget=target;
            return new State(false,false,0);
        }
        if(!target.equals(lastTarget)){held=0;wasReady=false;lastTarget=target;}
        held=Math.min(100,held+1);
        boolean ready=held>=3;
        boolean pulse=ready&&!wasReady && (lastPulseTick==Long.MIN_VALUE || tick-lastPulseTick>=15);
        if(pulse)lastPulseTick=tick;
        wasReady=ready;
        return new State(ready,pulse,held);
    }
}
