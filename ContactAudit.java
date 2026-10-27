package dev.leadshot;

/** Adapts the standalone moving AABB math to the bow solution without MC classes. */
public final class ContactAudit {
    private ContactAudit(){}
    public enum State{PLAUSIBLE,UNSUPPORTED,INVALID}
    public record Result(State state,double contactTick){public boolean geometricContact(){return state==State.PLAUSIBLE;}}
    public static Result evaluate(CoreBallistics.Vec eye,CoreBallistics.Solution shot,
            CoreBallistics.Vec targetCenter,CoreBallistics.Vec targetVelocity,
            CoreBallistics.Vec halfSize,CoreBallistics.Vec shooterVelocity,double speed,double mismatchLimit){
        if(eye==null||shot==null||targetCenter==null||targetVelocity==null||halfSize==null||shooterVelocity==null||
           !eye.finite()||!targetCenter.finite()||!targetVelocity.finite()||!halfSize.finite()||!shooterVelocity.finite()||
           !Double.isFinite(speed)||speed<=0||!Double.isFinite(mismatchLimit)||mismatchLimit<0)return new Result(State.INVALID,Double.NaN);
        if(shot.speedMismatch()>mismatchLimit)return new Result(State.UNSUPPORTED,Double.NaN);
        ProjectileContactWindow.Contact hit=ProjectileContactWindow.earliest(
            vec(eye),vec(shot.launchVelocity().add(shooterVelocity)),vec(targetCenter),vec(targetVelocity),vec(halfSize),.12,
            Math.min(120,Math.max(1,shot.ticks()+3)));
        return hit.intersects()?new Result(State.PLAUSIBLE,hit.tick()):new Result(State.UNSUPPORTED,Double.NaN);
    }
    private static ProjectileContactWindow.Vec vec(CoreBallistics.Vec v){return new ProjectileContactWindow.Vec(v.x(),v.y(),v.z());}
}
