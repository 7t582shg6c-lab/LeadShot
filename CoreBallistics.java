package dev.leadshot;

/** Self-contained approximate bow model, deliberately not server-authoritative. */
public final class CoreBallistics {
    private CoreBallistics() {}
    public static final double DRAG=.99, GRAVITY=.05;
    public static final double FULL_BOW_SPEED=3.0;
    public static final int MAX_TICKS=80;
    /** Precomputed displacement/drop coefficients, identical recurrence to the discrete arrow model.
     * A solution searches up to 79 flight times; the constants need not be recomputed per mob.
     */
    private static final double[] FLIGHT_SUM = new double[MAX_TICKS + 1];
    private static final double[] FLIGHT_DROP = new double[MAX_TICKS + 1];
    static {
        for (int n = 1; n <= MAX_TICKS; n++) {
            FLIGHT_DROP[n] = FLIGHT_DROP[n - 1] + GRAVITY * FLIGHT_SUM[n - 1];
            FLIGHT_SUM[n] = 1.0 + DRAG * FLIGHT_SUM[n - 1];
        }
    }
    public record Vec(double x,double y,double z) {
        public Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);}
        public Vec minus(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}
        public Vec times(double a){return new Vec(x*a,y*a,z*a);}
        public double length(){return Math.hypot(Math.hypot(x,y),z);}
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public record Solution(Vec launchVelocity,Vec futureCenter,int ticks,double speedMismatch) {
        public double yaw(){return Math.toDegrees(Math.atan2(-launchVelocity.x(),launchVelocity.z()));}
        public double pitch(){return -Math.toDegrees(Math.atan2(launchVelocity.y(),Math.hypot(launchVelocity.x(),launchVelocity.z())));}
    }
    public static double wrap(double angle) {
        if(!Double.isFinite(angle)) return Double.NaN;
        double x=(angle+180)%360;
        if(x<0)x+=360;
        return x-180;
    }
    public static Vec simulate(Vec origin,Vec velocity,int ticks) {
        if(origin==null||velocity==null||!origin.finite()||!velocity.finite()||ticks<0||ticks>120)return null;
        Vec p=origin,v=velocity;
        for(int i=0;i<ticks;i++){
            p=p.add(v);
            v=new Vec(v.x()*DRAG,v.y()*DRAG-GRAVITY,v.z()*DRAG);
        }
        return p.finite()?p:null;
    }
    /** Returns the best approximate launch vector (one of 2..80 flight ticks). */
    public static Solution solve(Vec eye,Vec center,Vec velocity,Vec shooterVelocity,double speed) {
        if(eye==null||center==null||velocity==null||shooterVelocity==null||
           !eye.finite()||!center.finite()||!velocity.finite()||!shooterVelocity.finite()||
           !Double.isFinite(speed)||speed<=0)return null;
        Solution best=null;
        for(int n=2;n<=MAX_TICKS;n++){
            double s=FLIGHT_SUM[n];
            double fall=FLIGHT_DROP[n];
            Vec future=center.add(velocity.times(n));
            Vec v=future.minus(eye).add(new Vec(0,fall,0)).times(1/s).minus(shooterVelocity);
            double mismatch=Math.abs(v.length()-speed);
            if(v.finite()&&Double.isFinite(mismatch)&&(best==null||mismatch<best.speedMismatch()))
                best=new Solution(v,future,n,mismatch);
        }
        return best;
    }
    /** Sub-tick refinement for the one selected fully-drawn target only.
     * Retains integer-tick solve() for the 64-target warm cache. Fractional flight
     * coefficients are linearly interpolated between vanilla discrete samples;
     * ContactAudit still performs an independent swept collision test. */
    public static Solution solveRefined(Vec eye,Vec center,Vec velocity,Vec shooterVelocity,double speed){
        Solution coarse=solve(eye,center,velocity,shooterVelocity,speed);
        if(coarse==null)return null;
        Solution best=coarse;
        // Search a narrow interval around the integer optimum (33 samples max).
        int central=coarse.ticks();
        for(int k=-16;k<=16;k++){
            double time=central+k/16.0;
            if(time<2||time>MAX_TICKS)continue;
            int low=(int)Math.floor(time),high=Math.min(MAX_TICKS,low+1);
            double fraction=time-low;
            double sum=FLIGHT_SUM[low]+fraction*(FLIGHT_SUM[high]-FLIGHT_SUM[low]);
            double drop=FLIGHT_DROP[low]+fraction*(FLIGHT_DROP[high]-FLIGHT_DROP[low]);
            Vec future=center.add(velocity.times(time));
            Vec launch=future.minus(eye).add(new Vec(0,drop,0)).times(1/sum).minus(shooterVelocity);
            double mismatch=Math.abs(launch.length()-speed);
            if(launch.finite()&&Double.isFinite(mismatch)&&mismatch<best.speedMismatch())
                best=new Solution(launch,future,(int)Math.round(time),mismatch);
        }
        return best;
    }
    public static double vanillaBowPower(int useTicks) {
        double f=Math.max(0,Math.min(40,useTicks))/20.0;
        return Math.min(1.0,(f*f+2*f)/3.0);
    }
}
