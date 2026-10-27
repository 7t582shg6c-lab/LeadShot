package dev.leadshot;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Random;

public final class TrackRepositoryV481Test {
    private static long checks;
    private static void ok(boolean b, String msg) {
        checks++; if (!b) throw new AssertionError(msg + " check="+checks);
    }
    private static TimedObservationV48 sample(UUID id,long epoch,long tick,
            CoreBallistics.Vec position,CoreBallistics.Vec velocity) {
        return new TimedObservationV48(id,epoch,tick,position,velocity,
                new CoreBallistics.Vec(.3,.3,.3),true);
    }
    public static void main(String[] args) {
        var zero=new CoreBallistics.Vec(0,0,0);
        var velocity=new CoreBallistics.Vec(1,.2,.5);
        for(int horizon=0;horizon<=80;horizon++){
            var est=MotionForecastV481.estimate(zero,velocity,velocity,horizon,
                    MotionForecastV481.Model.CONSTANT_VELOCITY);
            ok(est!=null && est.center().minus(velocity.times(horizon)).length()<1e-9,
                    "CV matches exact constant velocity");
        }
        ok(MotionForecastV481.estimate(zero,velocity,velocity,81,
                MotionForecastV481.Model.CONSTANT_VELOCITY)==null,"bounded horizon");
        ok(MotionForecastV481.estimate(zero,velocity,velocity,-1,
                MotionForecastV481.Model.CONSTANT_VELOCITY)==null,"negative horizon invalid");
        UUID id=UUID.fromString("e2411c77-80e0-42fd-827f-a2a852333333");
        var cvRepo=new TrackRepositoryV481();
        for(int t=0;t<180;t++){
            ok(cvRepo.observe(sample(id,1,t,velocity.times(t),velocity)),"constant track observation");
        }
        var cv=cvRepo.summary();
        ok(cv.active()==1 && cv.evaluated()>300,"multi-horizon walk-forward scored");
        for(var m:MotionForecastV481.Model.values()){
            var ms=cv.models().get(m);
            ok(ms.count()>40&&ms.rootMeanSquare()<1e-8,"zero acceleration is predictable");
            for(long byHorizon:ms.samplesByHorizon())
                ok(byHorizon>0,"all 5/10/20 horizons eventually evaluated");
        }
        var accelRepo=new TrackRepositoryV481();
        var turnRepo=new TrackRepositoryV481();
        CoreBallistics.Vec p=zero;
        for(int t=0;t<210;t++){
            CoreBallistics.Vec v=new CoreBallistics.Vec(.4+.007*t,0,.1);
            accelRepo.observe(sample(id,10,t,p,v));
            p=p.add(v);
        }
        var av=accelRepo.summary();
        var acv=av.models().get(MotionForecastV481.Model.CONSTANT_VELOCITY);
        var aca=av.models().get(MotionForecastV481.Model.DAMPED_ACCELERATION);
        ok(aca.rootMeanSquare()<acv.rootMeanSquare(),"damped acceleration improves acceleration scenario");
        p=zero;
        for(int t=0;t<210;t++){
            double angle=.03*t;
            CoreBallistics.Vec v=new CoreBallistics.Vec(Math.cos(angle),0,Math.sin(angle));
            turnRepo.observe(sample(id,11,t,p,v));
            p=p.add(v);
        }
        var turn=turnRepo.summary();
        var tcv=turn.models().get(MotionForecastV481.Model.CONSTANT_VELOCITY);
        var tct=turn.models().get(MotionForecastV481.Model.DAMPED_TURN);
        ok(tct.rootMeanSquare()<tcv.rootMeanSquare(),"damped turn improves arc scenario");
        for (int i=0;i<3000;i++){
            var random=new Random(i);
            var v=new CoreBallistics.Vec(random.nextDouble()*5-2.5,
                    random.nextDouble()*5-2.5,random.nextDouble()*5-2.5);
            var prior=new CoreBallistics.Vec(random.nextDouble()*5-2.5,
                    random.nextDouble()*5-2.5,random.nextDouble()*5-2.5);
            for(var model:MotionForecastV481.Model.values()){
                var f=MotionForecastV481.estimate(zero,v,prior,i%81,model);
                ok(f!=null&&f.center().finite()&&f.velocity().finite(),"bounded finite forecast");
            }
        }
        var remote=new TrackRepositoryV481();
        UUID remoteId=new UUID(0,3);
        remote.observe(sample(remoteId,14,1,zero,zero));
        remote.observe(sample(remoteId,14,2,new CoreBallistics.Vec(.5,0,0),zero));
        ok(remote.observation(remoteId).velocity().x()==.5,
                "remote interpolated entity derives velocity when reported delta is zero");
        var repository=new TrackRepositoryV481();
        var ids=new HashSet<UUID>();
        for(int i=0;i<96;i++){
            UUID contact=new UUID(0,i+1);
            ids.add(contact);
            ok(repository.observe(sample(contact,20,100,zero,velocity)),"contact accepted");
            ok(repository.summary().active()<=64,"capacity enforced");
        }
        ok(repository.summary().active()==64,"64 maximum contacts");
        ok(repository.summary().evictions()==32,"evictions counted");
        ok(!repository.observe(sample(new UUID(0,96),20,100,zero,velocity)),"duplicates/out-of-order rejected");
        repository.retain(20,100,Set.of(new UUID(0,96)));
        ok(repository.summary().active()==1,"retain excludes out-of-scope contacts");
        repository.observe(sample(new UUID(0,96),20,101,new CoreBallistics.Vec(999,0,0),velocity));
        ok(repository.summary().teleports()==1,"teleport invalidates history");
        repository.observe(sample(new UUID(0,96),20,105,new CoreBallistics.Vec(999,0,0),velocity));
        ok(repository.summary().gaps()==1,"missing ticks counted");
        repository.observe(sample(new UUID(0,96),21,1000,zero,velocity));
        ok(repository.summary().active()==1&&repository.summary().teleports()==0,"world epoch reset");
        ok(repository.forecast(new UUID(0,999),10,
                MotionForecastV481.Model.DAMPED_TURN)==null,"unknown UUID no prediction");
        ok(repository.summary().models().get(MotionForecastV481.Model.CONSTANT_VELOCITY)
                .samplesByHorizon().length==3,"immutable summary duration");
        System.out.println("PASS TrackRepositoryV481Test: "+checks+
                " assertions; accelerated CV/CA RMS="+acv.rootMeanSquare()+"/"+aca.rootMeanSquare()+
                "; turning CV/CT RMS="+tcv.rootMeanSquare()+"/"+tct.rootMeanSquare());
    }
}
