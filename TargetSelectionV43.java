package dev.leadshot;

/** Screen-centre bias takes precedence over distance. No ballistic work required. */
public final class TargetSelectionV43 {
    private TargetSelectionV43() {}
    /** Current entity bearing is the primary signal; predicted lead is secondary. */
    public static double score(double yawNow,double pitchNow,double yawLead,double pitchLead,
                               double distance){
        if(!Double.isFinite(yawNow)||!Double.isFinite(pitchNow)||
                !Double.isFinite(yawLead)||!Double.isFinite(pitchLead)||
                !Double.isFinite(distance)||distance<0)return Double.POSITIVE_INFINITY;
        double direct=Math.hypot(yawNow,pitchNow);
        double lead=Math.hypot(yawLead,pitchLead);
        // Avoid favoring a very nearby mob at the periphery over one on the crosshair.
        return direct*0.82+lead*0.18+Math.min(150,distance)*0.012;
    }
    /** RADAR keeps full 360-degree acquisition, but pointing at a contact captures it. */
    public static double radarScore(double reticleMiss,double distance){
        if(!Double.isFinite(reticleMiss)||!Double.isFinite(distance))return Double.POSITIVE_INFINITY;
        if(reticleMiss<=0.8)return -100+reticleMiss+distance*.025;
        return distance+Math.min(180,reticleMiss)*.10;
    }
    /** Previous target hysteresis must never steal a visibly centred new contact. */
    public static boolean preferNew(double oldScore,double newScore,double oldDirect,double newDirect){
        if(!Double.isFinite(newScore))return false;
        if(!Double.isFinite(oldScore))return true;
        if(newDirect<1.4 && oldDirect>newDirect+1.2)return true;
        if(oldDirect>4.0 && newDirect+1.5<oldDirect)return true;
        return TargetStability.switchToNew(oldScore,newScore,true);
    }
}
