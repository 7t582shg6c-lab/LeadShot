package dev.leadshot;

import java.util.Random;

/** Offline, no Minecraft API, deterministic input sequences. */
public final class GlideCalibrationV501Test {
    private static long checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static GlideCalibrationV501.Sample sample(long tick,CoreBallistics.Vec p,CoreBallistics.Vec v,
            double yaw,double pitch,boolean gliding,boolean collision){
        return new GlideCalibrationV501.Sample(tick,p,v,yaw,pitch,gliding,collision);
    }
    private static void near(double a,double b,double tolerance){
        ok(Double.isFinite(a)&&Math.abs(a-b)<tolerance,"value "+a+" != "+b);
    }
    private static void stableSequence(GlideCalibrationV501 audit,long start,int size,
                                       double yaw,double pitch,CoreBallistics.Vec initialSpeed){
        CoreBallistics.Vec position=new CoreBallistics.Vec(0,130,0),v=initialSpeed;
        for(int i=0;i<size;i++){
            audit.sample(sample(start+i,position,v,yaw,pitch,true,false));
            var next=GlideDynamicsV50.project(position,v,yaw,pitch,1);
            if(next==null)throw new AssertionError();
            position=next.position();v=next.velocity();
        }
    }
    public static void main(String[] args){
        var audit=new GlideCalibrationV501();
        audit.setEnabled(true);
        stableSequence(audit,0,401,37,-14,new CoreBallistics.Vec(.6,-.1,1.5));
        var result=audit.summary();
        for(int i=0;i<3;i++){
            var h=result.horizons()[i];
            ok(h.evaluated()==401-GlideCalibrationV501.HORIZONS[i],"interleaved due ticks "+h.evaluated());
            near(h.modelPositionRms(),0,1e-9);
            near(h.velocityRms(),0,1e-9);
            ok(h.baselinePositionRms()>0,"CV must be separately scored");
        }
        ok(result.skippedTurning()==0,"stable turn");
        ok(result.skippedImpulses()==0,"stable impulse");
        ok(result.observations()==401,"stable count");
        ok(!audit.report().contains("NaN"),"valid all horizons");
        audit.reset();
        ok(audit.summary().observations()==0&&audit.summary().horizons()[0].evaluated()==0,"reset");
        // Camera changes are not treated as physical-model misses.
        var p=new CoreBallistics.Vec(0,120,0);var v=new CoreBallistics.Vec(0,-.2,1);
        for(int i=0;i<30;i++){
            audit.sample(sample(500+i,p,v,i*5,0,true,false));
            var s=GlideDynamicsV50.project(p,v,i*5,0,1);p=s.position();v=s.velocity();
        }
        ok(audit.summary().skippedTurning()>0,"turn must be excluded");
        ok(audit.summary().horizons()[2].evaluated()==0,"5t rotating-view exclusion");
        // Sudden acceleration (e.g. fireworks) invalidates overlapping forecasts.
        audit.reset();p=new CoreBallistics.Vec(0,110,0);v=new CoreBallistics.Vec(.1,-.05,1);
        for(int i=0;i<15;i++){
            if(i==4)v=v.add(new CoreBallistics.Vec(0,0,1.2));
            audit.sample(sample(600+i,p,v,0,0,true,false));
            var s=GlideDynamicsV50.project(p,v,0,0,1);p=s.position();v=s.velocity();
        }
        ok(audit.summary().skippedImpulses()>0,"impulse must be excluded");
        // Gaps discard pending, preventing misleading future-tick comparison.
        audit.reset();audit.sample(sample(0,p,v,0,0,true,false));
        audit.sample(sample(4,p,v,0,0,true,false));
        ok(audit.summary().skippedGaps()==1,"tick gap");
        audit.sample(sample(4,p,v,0,0,true,false));
        ok(audit.summary().observations()==2,"duplicate must be ignored");
        audit.sample(sample(5,p,v,0,0,false,false));
        ok(audit.summary().skippedInactive()==1,"inactive drop");
        audit.sample(sample(6,p,v,0,0,true,true));
        ok(audit.summary().skippedInactive()==2,"collision drop");
        audit.sample(sample(7,new CoreBallistics.Vec(Double.NaN,0,0),v,0,0,true,false));
        ok(audit.summary().skippedInvalid()==1,"nonfinite rejected");
        var rand=new Random(501);
        // In 3000 independent trajectories, the local model is equal to itself
        // even with varied headings, camera angles, and initial velocities.
        for(int trial=0;trial<3000;trial++){
            audit.reset();
            double yaw=rand.nextDouble()*360-180,pitch=rand.nextDouble()*40-20;
            CoreBallistics.Vec initial=new CoreBallistics.Vec(rand.nextDouble()*1.6-.8,
                    rand.nextDouble()*.5-.25,rand.nextDouble()*.8+.7);
            stableSequence(audit,0,10,yaw,pitch,initial);
            var h=audit.summary().horizons();
            for(int i=0;i<3;i++){
                ok(h[i].evaluated()==10-GlideCalibrationV501.HORIZONS[i],"random counts");
                near(h[i].modelPositionRms(),0,1e-9);
            }
        }
        System.out.println("PASS GlideCalibrationV501Test: "+checks+" assertions");
    }
}
