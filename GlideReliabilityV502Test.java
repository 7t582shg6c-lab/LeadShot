package dev.leadshot;

import java.util.Random;

/** Counterfactual, independent reliability-policy and control integration tests. */
public final class GlideReliabilityV502Test {
    private static long assertions;
    private static void check(boolean truth,String why){assertions++;if(!truth)throw new AssertionError(why);}
    private static GlideCalibrationV501.RecentFive recent(long tick,int n,double model,double cv,long latest){
        return new GlideCalibrationV501.RecentFive(tick,n,model,cv,latest);
    }
    private static GlideReliabilityV502.State state(GlideCalibrationV501.RecentFive rec,long now){
        return GlideReliabilityV502.evaluate(rec,now).state();
    }
    public static void main(String[] args) throws Exception {
        check(state(null,100)==GlideReliabilityV502.State.NO_DATA,"null");
        check(state(recent(101,30,.1,.3,100),100)==GlideReliabilityV502.State.NO_DATA,"future now");
        check(state(recent(100,30,.1,.3,101),100)==GlideReliabilityV502.State.NO_DATA,"future scored");
        check(state(recent(100,23,.1,.3,100),100)==GlideReliabilityV502.State.NO_DATA,"insufficient");
        check(state(recent(100,24,.1,.3,59),100)==GlideReliabilityV502.State.NO_DATA,"stale");
        check(state(recent(100,24,.1,.3,60),100)==GlideReliabilityV502.State.RELIABLE,"boundary current");
        check(state(recent(100,24,.1,.3,100),100)==GlideReliabilityV502.State.RELIABLE,"accurate");
        check(state(recent(100,24,1.3,1.7,100),100)==GlideReliabilityV502.State.POOR_FIT,"too large abs error");
        check(state(recent(100,24,.8,.4,100),100)==GlideReliabilityV502.State.POOR_FIT,"bad relative");
        check(state(recent(100,24,.7,1.0,100),100)==GlideReliabilityV502.State.INCONCLUSIVE,"high but maybe better");
        check(state(recent(100,24,Double.NaN,.7,100),100)==GlideReliabilityV502.State.NO_DATA,"nonfinite");
        check(state(recent(100,24,-1,.7,100),100)==GlideReliabilityV502.State.NO_DATA,"negative RMS");
        var pos=new CoreBallistics.Vec(0,200,0);
        var vel=new CoreBallistics.Vec(.6,-.08,1.1);
        var guard=new GlideSteeringV50();
        var cfg=java.nio.file.Files.createTempFile("glide-v502-guard", ".properties");
        try {
            java.nio.file.Files.writeString(cfg,"enabled=true\nuseMeasuredConfidence=true\n");
            guard.readConfiguration(cfg);
            check(guard.enabled()&&guard.measuredGuardEnabled(),"config must read explicit opt-ins");
            java.nio.file.Files.writeString(cfg,"enabled=true\n");
            guard.readConfiguration(cfg);
            check(guard.enabled()&&!guard.measuredGuardEnabled(),"old config remains backwards compatible");
        } finally { java.nio.file.Files.deleteIfExists(cfg); }
        guard.setEnabled(true);
        guard.setMeasuredGuardEnabled(true);
        // Missing samples do not create an accidental permanent auto-fire lockout.
        var before=guard.regulate(0,0,.05,.05,pos,vel,true,false);
        check(before.fireStable(),"untrained must preserve old behavior");
        guard.acceptMeasuredConfidence(recent(100,24,.2,.5,100),100);
        check(guard.measuredConfidence().state()==GlideReliabilityV502.State.RELIABLE,"reliable load");
        var good=guard.regulate(0,0,.05,.05,pos,vel,true,false);
        check(good.fireStable(),"reliable small motion");
        // Fail-closed *only for opt-in confirmed large out-of-sample error*.
        guard.acceptMeasuredConfidence(recent(101,24,1.7,.5,101),101);
        check(guard.measuredConfidence().state()==GlideReliabilityV502.State.POOR_FIT,"detect bad model");
        var bad=guard.regulate(0,0,.05,.05,pos,vel,true,false);
        check(bad.active()&&!bad.fireStable()&&bad.reason().equals("measured-poor-fit"),"bad must deny READY");
        var ground=guard.regulate(0,0,90,20,pos,vel,false,false);
        check(ground.fireStable()&&!ground.active()&&ground.yaw()==90,"ground ignored");
        guard.setMeasuredGuardEnabled(false);
        check(guard.regulate(0,0,.05,.05,pos,vel,true,false).fireStable(),"guard opt-out");
        guard.setMeasuredGuardEnabled(true);
        guard.clearMeasuredConfidence();
        check(guard.regulate(0,0,.05,.05,pos,vel,true,false).fireStable(),"world reset");
        guard.acceptMeasuredConfidence(recent(105,24,1.7,.5,105),105);
        guard.acceptMeasuredConfidence(recent(104,24,.1,.6,104),104);
        check(guard.measuredConfidence().state()==GlideReliabilityV502.State.POOR_FIT,"out of order confidence ignored");
        // The main readiness gate is unchanged unless both settings were enabled.
        var rnd=new Random(502);
        for(int i=0;i<15000;i++){
            long t=1000+i;
            double model=rnd.nextDouble()*2.0;
            double cv=rnd.nextDouble()*2.0;
            var r=GlideReliabilityV502.evaluate(recent(t,24,model,cv,t),t);
            check(r.state()!=null&&r.ageTicks()==0,"valid verdict");
            if(r.state()==GlideReliabilityV502.State.RELIABLE)
                check(model<=.65 && model<=cv*1.15+.08,"reliable threshold");
            if(r.state()==GlideReliabilityV502.State.POOR_FIT)
                check(model>1.2 || (model>.35&&model>cv*1.25+.05),"poor threshold");
        }
        // Evaluate a real rolling sequence: model generates synthetic observations.
        var telemetry=new GlideCalibrationV501();telemetry.setEnabled(true);
        var p=pos;var v=vel;
        for(int i=0;i<240;i++){
            telemetry.sample(new GlideCalibrationV501.Sample(i,p,v,25,-12,true,false));
            var step=GlideDynamicsV50.project(p,v,25,-12,1);
            p=step.position();v=step.velocity();
        }
        var r=telemetry.recentFive(239);
        check(r.evaluations()<=64 && r.evaluations()>=24,"rolling size bound");
        check(r.modelPositionRms()<1e-9 && r.baselinePositionRms()>0,"rolling accurate");
        check(state(r,239)==GlideReliabilityV502.State.RELIABLE,"sample-fed confidence");
        telemetry.sample(new GlideCalibrationV501.Sample(240,p,v,25,-12,false,false));
        check(telemetry.recentFive(240).evaluations()==0,"grounded segment invalidates flight evidence");

        telemetry.reset();check(telemetry.recentFive(239).evaluations()==0,"world change empties window");
        var finalStatus=telemetry.recentFive(1000);
        check(finalStatus.evaluations()==0,"no cross world data");
        System.out.println("PASS GlideReliabilityV502Test: "+assertions+" assertions");
    }
}
