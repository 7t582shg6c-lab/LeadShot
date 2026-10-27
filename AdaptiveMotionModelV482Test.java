package dev.leadshot;

import java.util.UUID;
import java.util.Set;
import java.util.Random;

public final class AdaptiveMotionModelV482Test {
    private static long checks;
    private static void ok(boolean value,String message){
        checks++;if(!value)throw new AssertionError(message+" #"+checks);
    }
    private static TimedObservationV48 snapshot(UUID id,long epoch,long tick,
            CoreBallistics.Vec p,CoreBallistics.Vec v){
        return new TimedObservationV48(id,epoch,tick,p,v,new CoreBallistics.Vec(.3,.3,.3),true);
    }
    public static void main(String[] args){
        var adaptive=new AdaptiveMotionModelV482();
        ok(!adaptive.decision(5).trained(),"no held-out samples must remain untrained");
        ok(adaptive.decision(5).model()==MotionForecastV481.Model.CONSTANT_VELOCITY,
                "no future data: CV conservative baseline");
        for(int i=0;i<3;i++)for(var model:MotionForecastV481.Model.values()){
            adaptive.record(model,0,model==MotionForecastV481.Model.DAMPED_TURN ? .02: 2.0);
            ok(!adaptive.decision(5).trained(),"3 observations are not enough");
        }
        for(int i=0;i<9;i++)for(var model:MotionForecastV481.Model.values()){
            adaptive.record(model,0,model==MotionForecastV481.Model.DAMPED_TURN ? .02: 2.0);
            adaptive.record(model,1,model==MotionForecastV481.Model.DAMPED_ACCELERATION ? .05:3.0);
        }
        ok(adaptive.decision(5).trained(),"trained after four walk-forward samples");
        ok(adaptive.decision(5).model()==MotionForecastV481.Model.DAMPED_TURN,
                "horizon5 selects empirically superior turning motion");
        ok(adaptive.decision(10).model()==MotionForecastV481.Model.DAMPED_ACCELERATION,
                "horizon10 selects acceleration without sharing horizon5 error");
        ok(!adaptive.decision(20).trained(),"horizon20 must remain untrained");
        var prior=adaptive.decision(5).model();
        adaptive.record(MotionForecastV481.Model.CONSTANT_VELOCITY,0,.021);
        ok(adaptive.decision(5).model()==prior,"one slightly favorable sample cannot cause jitter");
        adaptive.record(MotionForecastV481.Model.DAMPED_TURN,-1,0);
        adaptive.record(MotionForecastV481.Model.DAMPED_TURN,4,0);
        adaptive.record(MotionForecastV481.Model.DAMPED_TURN,0,Double.NaN);
        ok(adaptive.decision(5).trained(),"invalid measurements ignored");
        // Repository integration: future evaluations must not score at sample time.
        var tracker=new TrackRepositoryV481();
        UUID id=new UUID(123L,456L);
        var zero=new CoreBallistics.Vec(0,0,0);
        var v=new CoreBallistics.Vec(1,0,.4);
        tracker.observe(snapshot(id,4,0,zero,v));
        var cold=tracker.adaptiveForecast(id,5);
        ok(cold!=null&&!cold.trained()&&cold.model()==MotionForecastV481.Model.CONSTANT_VELOCITY,
                "initial model selection always CV");
        ok(tracker.adaptiveForecast(id,21)==null,"adaptive path capped at 20 ticks");
        for(int t=1;t<180;t++){
            tracker.observe(snapshot(id,4,t,v.times(t),v));
            if(t<20)ok(!tracker.adaptiveForecast(id,20).trained(),
                    "20tick predictions must await their future due tick");
        }
        var trained=tracker.adaptiveForecast(id,20);
        ok(trained!=null&&trained.trained(),"20tick prediction matured after future observations");
        ok(trained.model()==MotionForecastV481.Model.CONSTANT_VELOCITY,
                "straight trajectory never requires a complexity upgrade");
        var byModel=tracker.adaptiveCounts(5);
        ok(byModel.get(MotionForecastV481.Model.CONSTANT_VELOCITY)==1,
                "one active contact with validated CV model");
        tracker.retain(4,181,Set.of());
        ok(tracker.adaptiveForecast(id,5)==null,"pruned ID has no model score");
        // End-to-end realistic turn vs acceleration on two separate UUIDs.
        var mixed=new TrackRepositoryV481();
        UUID accelerating=new UUID(11,4),turning=new UUID(12,4);
        CoreBallistics.Vec accelPos=zero,turnPos=zero;
        for(int t=0;t<210;t++){
            var a=new CoreBallistics.Vec(.4+.007*t,0,.1);
            double theta=.03*t;
            var w=new CoreBallistics.Vec(Math.cos(theta),0,Math.sin(theta));
            mixed.observe(snapshot(accelerating,9,t,accelPos,a));
            mixed.observe(snapshot(turning,9,t,turnPos,w));
            accelPos=accelPos.add(a);turnPos=turnPos.add(w);
        }
        var accel=mixed.adaptiveForecast(accelerating,5);
        var turn=mixed.adaptiveForecast(turning,5);
        ok(accel.trained()&&turn.trained(),"both individual contact models trained");
        ok(accel.model()==MotionForecastV481.Model.DAMPED_ACCELERATION,
                "accelerating contact chooses CA");
        ok(turn.model()==MotionForecastV481.Model.DAMPED_TURN,
                "turning contact chooses CT, independently of accelerating contact");
        mixed.observe(snapshot(turning,10,220,zero,v));
        ok(!mixed.adaptiveForecast(turning,5).trained(),"world switch clears model history");
        System.out.println("PASS AdaptiveMotionModelV482Test: "+checks+" checks, model selection per entity and horizon");
    }
}
