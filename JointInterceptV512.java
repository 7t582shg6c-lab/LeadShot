package dev.leadshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Time x bearing interception search for one ALREADY READY shot.
 * G2G/G2A/A2G/A2A share the identical projectile and moving-AABB checks.
 * Hypothetical candidates are NEVER firing authorization: only the existing
 * current-tick READY/LOS/obstruction/release gate can authorize release.
 *
 * Estimates for future player movement are speculative; the gliding model is
 * approximate and excludes fireworks, terrain and server correction.
 */
public final class JointInterceptV512 {
    private JointInterceptV512() {}
    private static final double SPEED=CoreBallistics.FULL_BOW_SPEED;
    private static final double RADIUS=.12;
    private static final int MAX_DELAY=3;
    private static final int MAX_FLIGHT=20;

    public record Evaluation(int bestWaitTicks,int candidatesChecked,
            double presentScore,double bestFutureScore,String reason) {}

    /** Lower scores represent easier, less grazing, less speculative shots. */
    public static Evaluation evaluate(EngagementProfileV511.Policy policy,
            boolean shooterGliding,double cameraYaw,double cameraPitch,
            CoreBallistics.Vec currentOrigin,CoreBallistics.Vec inheritedVelocity,
            CoreBallistics.Solution presentShot,CoreBallistics.Vec targetCenter,
            CoreBallistics.Vec targetVelocity,CoreBallistics.Vec targetHalf,
            List<CoreBallistics.Vec> knownTargetPath,int requestedMaxDelay){
        Evaluation unknown=new Evaluation(0,0,Double.NaN,Double.NaN,"invalid");
        if(policy==null||currentOrigin==null||inheritedVelocity==null||presentShot==null||
                targetCenter==null||targetVelocity==null||targetHalf==null||
                !currentOrigin.finite()||!inheritedVelocity.finite()||
                !targetCenter.finite()||!targetVelocity.finite()||!targetHalf.finite()||
                targetHalf.x()<=0||targetHalf.y()<=0||targetHalf.z()<=0||
                !Double.isFinite(cameraYaw)||!Double.isFinite(cameraPitch)||
                presentShot.ticks()<2||presentShot.ticks()>MAX_FLIGHT||
                requestedMaxDelay<1||requestedMaxDelay>MAX_DELAY || !policy.allowsWaiting())return unknown;
        if(policy.curvedRequired() && knownTargetPath==null)
            return new Evaluation(0,0,Double.NaN,Double.NaN,"curved-path-required");
        // The existing adaptive predictor yields points through roughly seed ticks+7.
        // Every future candidate must have enough trajectory for its OWN duration.
        int horizon=Math.min(CoreBallistics.MAX_TICKS,
                Math.max(presentShot.ticks()+requestedMaxDelay+6,30));
        List<CoreBallistics.Vec> targetPath=targetPath(knownTargetPath,targetCenter,targetVelocity,horizon);
        if(targetPath==null)return new Evaluation(0,0,Double.NaN,Double.NaN,"missing-path");
        // Score the ALREADY verified current shot, without making it a new
        // authority for release. Unknown present geometry means do not wait.
        Score now=score(currentOrigin,inheritedVelocity,presentShot,targetPath,targetHalf,
                targetCenter,targetVelocity,cameraYaw,cameraPitch,0,policy);
        if(now==null)return new Evaluation(0,1,Double.NaN,Double.NaN,"present-not-comparable");
        double best=Double.POSITIVE_INFINITY;
        int delayChosen=0,checks=1;
        for(int delay=1;delay<=requestedMaxDelay;delay++){
            ShooterFrame launch=futureShooter(currentOrigin,inheritedVelocity,
                    cameraYaw,cameraPitch,delay,shooterGliding);
            if(launch==null)continue;
            List<CoreBallistics.Vec> remaining=targetPath.subList(delay,targetPath.size());
            CoreBallistics.Vec center=remaining.get(0);
            CoreBallistics.Vec velocity=remaining.get(1).minus(center);
            CoreBallistics.Solution seed=CoreBallistics.solveRefined(launch.origin(),
                    center,velocity,launch.inherited(),SPEED);
            checks++;
            if(seed==null||seed.ticks()<2||seed.ticks()>MAX_FLIGHT||
                    !Double.isFinite(seed.speedMismatch())||seed.speedMismatch()>.30 ||
                    seed.ticks()+1>=remaining.size())continue;
            // Bounded search jointly over release tick and local yaw/pitch.
            // The existing optimizer requires both curved and legacy contacts.
            CurvedAimOptimizerV492.Result fitted=CurvedAimOptimizerV492.optimize(
                    launch.origin(),seed,launch.inherited(),center,velocity,targetHalf,
                    remaining,SPEED,RADIUS);
            checks+=fitted.evaluations();
            if(!fitted.bothModelsHit())continue;
            Score candidate=score(launch.origin(),launch.inherited(),fitted.shot(),
                    remaining,targetHalf,center,velocity,cameraYaw,cameraPitch,
                    delay,policy);
            if(candidate!=null && candidate.value()<best){
                best=candidate.value();delayChosen=delay;
            }
        }
        if(delayChosen==0)return new Evaluation(0,checks,now.value(),Double.NaN,"no-future-contact");
        return new Evaluation(delayChosen,checks,now.value(),best,"compared");
    }

    private record Score(double value,double clearance) {}
    private record ShooterFrame(CoreBallistics.Vec origin,CoreBallistics.Vec inherited) {}
    private static ShooterFrame futureShooter(CoreBallistics.Vec origin,
            CoreBallistics.Vec inherited,double yaw,double pitch,int delay,boolean gliding){
        if(delay<1||delay>MAX_DELAY)return null;
        // The v0.51.1 caller marks ordinary jumps as airborne as well.
        // Do not apply Elytra-only dynamics to a slow jump or a mild fall.
        if(!gliding || inherited.length()<.65){
            CoreBallistics.Vec future=origin.add(inherited.times(delay));
            return future.finite()?new ShooterFrame(future,inherited):null;
        }
        GlideDynamicsV50.State projected=GlideDynamicsV50.project(origin,inherited,yaw,pitch,delay);
        if(projected==null||!projected.position().finite()||!projected.velocity().finite())return null;
        // Unmodelled very fast movement/rocket boost: skip speculative waits.
        if(projected.velocity().length()>3)return null;
        return new ShooterFrame(projected.position(),projected.velocity());
    }

    private static List<CoreBallistics.Vec> targetPath(List<CoreBallistics.Vec> curve,
            CoreBallistics.Vec center,CoreBallistics.Vec velocity,int horizon){
        if(curve!=null){
            if(curve.size()<2||curve.size()>CoreBallistics.MAX_TICKS+1)return null;
            for(CoreBallistics.Vec p:curve)if(p==null||!p.finite())return null;
            return curve;
        }
        var linear=new ArrayList<CoreBallistics.Vec>(horizon+1);
        for(int t=0;t<=horizon;t++){
            var p=center.add(velocity.times(t));
            if(!p.finite())return null;
            linear.add(p);
        }
        return linear;
    }

    /** Evaluates the same discrete arrow at fixed launch speed, and uses
     * sampled interior hitbox clearance as an approximate robustness signal.
     * The true path intersection is FIRST verified using CurvedContactV491.
     */
    private static Score score(CoreBallistics.Vec origin,CoreBallistics.Vec inherited,
            CoreBallistics.Solution shot,List<CoreBallistics.Vec> path,
            CoreBallistics.Vec half,CoreBallistics.Vec center,CoreBallistics.Vec velocity,
            double yaw,double pitch,int delay,EngagementProfileV511.Policy policy){
        CoreBallistics.Vec launched=ActualBowShot.initialVelocity(shot,inherited,SPEED);
        if(launched==null)return null;
        CurvedContactV491.Contact collision=CurvedContactV491.evaluate(origin,launched,path,half,RADIUS);
        if(!collision.hit())return null;
        if(!ContactAudit.evaluate(origin,shot,center,velocity,half,inherited,
                SPEED,Math.max(.18,SPEED*.1)).geometricContact())return null;
        CanonicalArrowV48.Trace trace=CanonicalArrowV48.air(origin,launched,path.size()-1);
        if(trace==null)return null;
        double clearance=0;
        // No second coarse inverse search: evaluate at most 20 flight ticks
        // around the actual contact and sample five positions per tick.
        int max=Math.min(MAX_FLIGHT+2,trace.duration());
        for(int tick=0;tick<max;tick++){
            var a=trace.at(tick).position();var b=trace.at(tick+1).position();
            var ta=path.get(tick);var tb=path.get(tick+1);
            for(int k=0;k<=4;k++){
                double f=k*.25;
                var d=a.add(b.minus(a).times(f)).minus(ta.add(tb.minus(ta).times(f)));
                double norm=Math.max(Math.abs(d.x())/(half.x()+RADIUS),
                        Math.max(Math.abs(d.y())/(half.y()+RADIUS),
                                Math.abs(d.z())/(half.z()+RADIUS)));
                clearance=Math.max(clearance,Math.max(0,1-norm));
            }
        }
        double angle=Math.hypot(CoreBallistics.wrap(shot.yaw()-yaw),shot.pitch()-pitch);
        if(!Double.isFinite(angle))return null;
        // Angle costs, delay costs and a bounded geometric interior margin.
        // Airborne target/launcher cases incur an uncertainty surcharge.
        double airPenalty=(policy.theater()==EngagementProfileV511.Theater.AIR_TO_AIR ? .30 :
                policy.theater()==EngagementProfileV511.Theater.AIR_TO_GROUND ? .24 :
                policy.theater()==EngagementProfileV511.Theater.GROUND_TO_AIR ? .16 :.08);
        double value=.20*angle-3.0*clearance+delay*(.22+airPenalty)+
                delay*.06*policy.relativeSpeed()+Math.min(1.5,shot.speedMismatch()*2);
        return Double.isFinite(value)?new Score(value,clearance):null;
    }
}
