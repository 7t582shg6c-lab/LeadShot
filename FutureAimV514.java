package dev.leadshot;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * Opt-in, one-tick future-bearing guidance for an ALREADY HELD shot.
 * The hint is derived from JointIntercept's geometrically verified future
 * candidates, but is advisory only. It cannot unlock a target, authorize
 * READY, bypass LOS, or release the bow. The ordinary live-shot pipeline is
 * still the sole source of the current intercept geometry and firing gates.
 */
public final class FutureAimV514 {
    public static final FutureAimV514 INSTANCE=new FutureAimV514();
    public record Goal(double yaw,double pitch,boolean applied,String reason) {}
    private boolean enabled;
    private FutureAimV514() {}
    public synchronized boolean enabled(){return enabled;}
    public synchronized void setEnabled(boolean value){enabled=value;}
    public synchronized void readConfiguration(Path file){
        enabled=false;
        if(file==null||!Files.isRegularFile(file))return;
        try(InputStream in=Files.newInputStream(file)){
            Properties p=new Properties();p.load(in);
            enabled=Boolean.parseBoolean(p.getProperty("enabled","false"));
        }catch(Exception ignored){enabled=false;}
    }
    /**
     * Restrict the *goal* change relative to this tick's newly recomputed shot.
     * The standard AimAssistController and GlideSteering governor remain after
     * this method; manual mouse input always takes priority.
     */
    public synchronized Goal guide(UUID selected,long tick,boolean heldIntent,
            boolean manualRetargeting,boolean gliding,
            CoreBallistics.Solution currentShot,
            InterceptWindowV51.FutureGuide hint){
        if(currentShot==null||!Double.isFinite(currentShot.yaw())||
                !Double.isFinite(currentShot.pitch()))return new Goal(0,0,false,"no-current-shot");
        double presentYaw=currentShot.yaw(),presentPitch=currentShot.pitch();
        Goal original=new Goal(presentYaw,presentPitch,false,"legacy");
        if(!enabled || !InterceptWindowV51.INSTANCE.enabled())return original;
        if(!heldIntent||manualRetargeting||selected==null||hint==null)return original;
        if(tick<0||tick!=hint.validOnTick()||!selected.equals(hint.target())||
                hint.waitTicks()<1||hint.waitTicks()>3||hint.theater()==null||
                !Double.isFinite(hint.yaw())||!Double.isFinite(hint.pitch()))return original;
        boolean airTheater=hint.theater()==EngagementProfileV511.Theater.AIR_TO_GROUND
                ||hint.theater()==EngagementProfileV511.Theater.AIR_TO_AIR;
        // Never treat a stale ground/air policy as if it were current.
        if(airTheater!=gliding)return original;
        double dy=CoreBallistics.wrap(hint.yaw()-presentYaw),dp=hint.pitch()-presentPitch;
        double distance=Math.hypot(dy,dp);
        double maxSeparation=gliding?.9:1.6;
        if(!Double.isFinite(distance)||distance<.03||distance>maxSeparation)return original;
        // One short bounded steering hint, not a snap to predicted future angles.
        double gain=gliding?.20:.30;
        double maxOffset=gliding?.22:.45;
        double fraction=Math.min(gain,maxOffset/distance);
        double yaw=CoreBallistics.wrap(presentYaw+dy*fraction);
        double pitch=presentPitch+dp*fraction;
        if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||pitch < -89.9||pitch>89.9)
            return original;
        return new Goal(yaw,pitch,true,"one-tick-future-window");
    }
}
