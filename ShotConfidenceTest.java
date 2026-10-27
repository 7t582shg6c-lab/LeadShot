package dev.leadshot;
public final class ShotConfidenceTest {
    private static int assertions;
    private static void ok(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
    private static BallisticOcclusion.Result blocks(BallisticOcclusion.State state){return new BallisticOcclusion.Result(state,0,0,Double.NaN);}
    private static TrajectoryConsensus.Result votes(int n){return new TrajectoryConsensus.Result(
        n==3?TrajectoryConsensus.State.CONSISTENT:n==0?TrajectoryConsensus.State.NO_CONTACT:TrajectoryConsensus.State.DISPUTED,n,3,0,0);
    }
    public static void main(String[] args){
        var yes=new ContactAudit.Result(ContactAudit.State.PLAUSIBLE,4);
        var no=new ContactAudit.Result(ContactAudit.State.UNSUPPORTED,Double.NaN);
        for(var block:BallisticOcclusion.State.values())for(int support=0;support<=3;support++){
            var v=votes(support);
            var result=ShotConfidence.classify(yes,v,blocks(block));
            ShotConfidence.Status expected = block==BallisticOcclusion.State.BLOCKED?ShotConfidence.Status.BLOCKED
                 : support==0?ShotConfidence.Status.CONTACT_UNSUPPORTED
                 : support<3?ShotConfidence.Status.MODEL_DISAGREEMENT
                 : block==BallisticOcclusion.State.CLEAR?ShotConfidence.Status.CONSISTENT_ESTIMATE
                 : ShotConfidence.Status.UNVERIFIED;
            ok(result==expected,"block="+block+" support="+support);
            ok(ShotConfidence.japanese(result).length()>6,"nonempty label");
            ok((ShotConfidence.color(result)>>>24)==0xFF,"opaque color");
        }
        ok(ShotConfidence.classify(no,votes(3),blocks(BallisticOcclusion.State.CLEAR))==ShotConfidence.Status.CONTACT_UNSUPPORTED,"no contact is not green");
        ok(ShotConfidence.classify(null,null,null)==ShotConfidence.Status.CONTACT_UNSUPPORTED,"null safe");
        System.out.println("PASS ShotConfidenceTest "+assertions+" assertions");
    }
}
