package dev.leadshot;

import java.util.List;

/** Opt-in, bounded angular rescue for a trained curved target track.
 * Never claims a hit unless BOTH the nonlinear moving-AABB prediction and the
 * existing constant-effective-velocity contact model agree. Server hits are not
 * observable here. Only the selected fully-drawn target calls this function.
 */
public final class CurvedAimOptimizerV492 {
    private CurvedAimOptimizerV492() {}
    public record Result(CoreBallistics.Solution shot,boolean optimized,
                         boolean bothModelsHit,int evaluations) {}

    public static Result optimize(CoreBallistics.Vec origin,CoreBallistics.Solution seed,
            CoreBallistics.Vec inherited,CoreBallistics.Vec targetCenter,
            CoreBallistics.Vec effectiveTargetVelocity,CoreBallistics.Vec half,
            List<CoreBallistics.Vec> targetPath,double launchSpeed,double arrowRadius) {
        Result fallback=new Result(seed,false,false,0);
        if(origin==null||seed==null||inherited==null||targetCenter==null
                ||effectiveTargetVelocity==null||half==null||targetPath==null
                ||!origin.finite()||!inherited.finite()||!targetCenter.finite()
                ||!effectiveTargetVelocity.finite()||!half.finite()
                ||half.x()<0||half.y()<0||half.z()<0
                ||!Double.isFinite(launchSpeed)||launchSpeed<=0
                ||!Double.isFinite(arrowRadius)||arrowRadius<0
                ||targetPath.size()<2||targetPath.size()>CoreBallistics.MAX_TICKS+1
                ||seed.ticks()<2||seed.ticks()>20
                ||!Double.isFinite(seed.yaw())||!Double.isFinite(seed.pitch()))return fallback;
        for(var p:targetPath)if(p==null||!p.finite())return fallback;
        // The optimization is deliberately bounded to avoid suddenly swinging
        // the camera around a different entity, especially while gliding.
        double yaw=seed.yaw(),pitch=seed.pitch();
        int attempts=0;
        if(valid(origin,seed,inherited,targetCenter,effectiveTargetVelocity,half,targetPath,
                launchSpeed,arrowRadius))return new Result(seed,false,true,1);
        attempts++;
        double bestShift=Double.POSITIVE_INFINITY;
        CoreBallistics.Solution best=null;
        double[] steps={-1.20,-0.60,0.0,0.60,1.20};
        // Only 24 additional coarse trials. There is no unbounded loop.
        for(double dy:steps)for(double dp:steps){
            if(dy==0&&dp==0)continue;
            CoreBallistics.Solution candidate=angle(seed,yaw+dy,pitch+dp,launchSpeed,targetPath);
            attempts++;
            if(candidate==null||!valid(origin,candidate,inherited,targetCenter,
                    effectiveTargetVelocity,half,targetPath,launchSpeed,arrowRadius))continue;
            double score=dy*dy+dp*dp;
            if(score<bestShift){best=candidate;bestShift=score;}
        }
        if(best==null)return new Result(seed,false,false,attempts);
        // Nine bounded fine-grain trials near the best coarse candidate.
        double[] fine={-.25,0,.25};
        for(double dy:fine)for(double dp:fine){
            if(dy==0&&dp==0)continue;
            double nextYaw=best.yaw()+dy,nextPitch=best.pitch()+dp;
            if(Math.abs(CoreBallistics.wrap(nextYaw-yaw))>1.5||Math.abs(nextPitch-pitch)>1.5)continue;
            CoreBallistics.Solution candidate=angle(seed,nextYaw,nextPitch,launchSpeed,targetPath);
            attempts++;
            if(candidate==null||!valid(origin,candidate,inherited,targetCenter,
                    effectiveTargetVelocity,half,targetPath,launchSpeed,arrowRadius))continue;
            double score=Math.pow(CoreBallistics.wrap(nextYaw-yaw),2)+Math.pow(nextPitch-pitch,2);
            if(score<bestShift){best=candidate;bestShift=score;}
        }
        return new Result(best,true,true,attempts);
    }
    private static CoreBallistics.Solution angle(CoreBallistics.Solution seed,
            double yaw,double pitch,double speed,List<CoreBallistics.Vec> targetPath) {
        if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||Math.abs(pitch)>89.9)return null;
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        double horizontal=Math.cos(p);
        var velocity=new CoreBallistics.Vec(-Math.sin(y)*horizontal*speed,
                -Math.sin(p)*speed,Math.cos(y)*horizontal*speed);
        int n=seed.ticks();
        if(n<0||n>=targetPath.size())return null;
        // speedMismatch remains the seed inverse mismatch; it is NOT reset to
        // zero just because the candidate vector is normalized to full speed.
        return new CoreBallistics.Solution(velocity,targetPath.get(n),n,seed.speedMismatch());
    }
    private static boolean valid(CoreBallistics.Vec origin,CoreBallistics.Solution shot,
            CoreBallistics.Vec inherited,CoreBallistics.Vec center,
            CoreBallistics.Vec targetVelocity,CoreBallistics.Vec half,
            List<CoreBallistics.Vec> path,double speed,double radius) {
        CoreBallistics.Vec actual=ActualBowShot.initialVelocity(shot,inherited,speed);
        if(actual==null)return false;
        var curve=CurvedContactV491.evaluate(origin,actual,path,half,radius);
        if(!curve.hit())return false;
        // Do not permit a curve-only hit through an otherwise disallowed shot.
        var legacy=ContactAudit.evaluate(origin,shot,center,targetVelocity,
                half,inherited,speed,Math.max(.18,speed*.10));
        return legacy.geometricContact();
    }
}
