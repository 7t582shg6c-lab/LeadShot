package dev.leadshot;
import java.util.Random;
public final class TrajectoryConsensusTest {
    private static int count;
    private static void ok(boolean pass,String reason){count++;if(!pass)throw new AssertionError(reason);}
    private static final CoreBallistics.Vec ZERO=new CoreBallistics.Vec(0,0,0);
    public static void main(String[] args){
        var launch=ZERO; var vel=new CoreBallistics.Vec(0,0,3);
        var wide=TrajectoryConsensus.evaluate(launch,vel,new CoreBallistics.Vec(0,0,12),ZERO,
                new CoreBallistics.Vec(4,4,4),.12,10);
        ok(wide.allAgree() && wide.supportingModels()==3,"near/large all agree");
        ok(wide.earliestTick()>=0 && wide.earliestTick()<=wide.latestTick(),"interval finite");
        var far=new CoreBallistics.Vec(200,4,200);
        var miss=TrajectoryConsensus.evaluate(launch,vel,far,ZERO,new CoreBallistics.Vec(.5,.5,.5),.12,35);
        ok(miss.state()==TrajectoryConsensus.State.NO_CONTACT && miss.supportingModels()==0,"all miss");
        ok(Double.isNaN(miss.earliestTick()),"no-contact tick is NaN");
        var nominal=CoreBallistics.simulate(ZERO,new CoreBallistics.Vec(0,2.0,3.0),30);
        var narrow=TrajectoryConsensus.evaluate(launch,new CoreBallistics.Vec(0,2,3),nominal,ZERO,
                new CoreBallistics.Vec(.09,.09,.09),.01,35);
        ok(narrow.state()==TrajectoryConsensus.State.DISPUTED,"small target late flight differs");
        ok(narrow.supportingModels()>=1 && narrow.supportingModels()<3,"partial model agreement");
        ok(TrajectoryConsensus.evaluate(null,vel,far,ZERO,new CoreBallistics.Vec(1,1,1),0,10).state()==TrajectoryConsensus.State.INVALID,"null invalid");
        ok(TrajectoryConsensus.evaluate(launch,vel,far,ZERO,new CoreBallistics.Vec(0,1,1),0,10).state()==TrajectoryConsensus.State.INVALID,"zero half invalid");
        ok(TrajectoryConsensus.evaluate(launch,vel,far,ZERO,new CoreBallistics.Vec(1,1,1),0,81).state()==TrajectoryConsensus.State.INVALID,"max ticks");
        Random random=new Random(0x5F99);
        for(int i=0;i<15000;i++){
            CoreBallistics.Vec initial=new CoreBallistics.Vec(random.nextDouble()*3-1.5,
                    random.nextDouble()*2-.3,random.nextDouble()*3+.5);
            int ticks=1+random.nextInt(80);
            var end=CoreBallistics.simulate(ZERO,initial,ticks);
            var box=new CoreBallistics.Vec(.01+random.nextDouble()*2,.01+random.nextDouble()*2,.01+random.nextDouble()*2);
            var tested=TrajectoryConsensus.evaluate(ZERO,initial,end,ZERO,box,.05,ticks);
            ok(tested.testedModels()==3 && tested.supportingModels()>=1 && tested.supportingModels()<=3,"nominal hit retained");
            ok(tested.state()==TrajectoryConsensus.State.CONSISTENT||tested.state()==TrajectoryConsensus.State.DISPUTED,"nominal state valid");
            ok(Double.isFinite(tested.earliestTick())&&tested.latestTick()>=tested.earliestTick(),"finite interval");
        }
        System.out.println("PASS TrajectoryConsensusTest "+count+" assertions");
    }
}
