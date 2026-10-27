package dev.leadshot;
public final class ContinuousReleaseGateTest {
    static int assertions;
    static void check(boolean value){if(!value)throw new AssertionError("gate assertion "+assertions);assertions++;}
    public static void main(String[] args){
        ContinuousReleaseGate gate=new ContinuousReleaseGate();
        check(!gate.allow(1,true,true,true,false,true,true,true));
        check(!gate.allow(2,false,true,true,true,true,true,true));
        check(!gate.allow(3,true,false,true,true,true,true,true));
        check(!gate.allow(4,true,true,true,true,false,true,true));
        check(!gate.allow(5,true,true,true,true,true,false,true));
        check(!gate.allow(6,true,true,true,true,true,true,false));
        check(gate.allow(7,true,true,true,true,true,true,true));
        for(int t=8;t<45;t++)check(!gate.allow(t,true,true,true,true,true,true,true));
        // Re-arm on no-longer-charged or bow used ended, never allow early shot.
        check(!gate.allow(45,true,true,true,false,true,true,true));
        check(!gate.allow(46,true,true,true,true,true,true,false));
        check(gate.allow(47,true,true,true,true,true,true,true));
        check(!gate.allow(48,true,true,true,true,true,true,true));
        gate.rearm();
        check(!gate.allow(49,true,true,true,true,true,true,true)); // min time separation
        check(gate.allow(68,true,true,true,true,true,true,true));
        gate.reset();
        check(gate.allow(69,true,true,true,true,true,true,true));
        System.out.println("PASS ContinuousReleaseGateTest "+assertions+" assertions");
    }
}
