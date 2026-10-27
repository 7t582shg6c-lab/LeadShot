package dev.leadshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only, bounded measurement of one-tick arrow prediction residuals.
 * Observations are NOT server shot acknowledgements and NOT hit confirmation.
 */
public final class ArrowTelemetryV48 {
    public record Summary(long measuredPairs, double rootMeanSquareError,
                          double maximumError, int activeArrows, long skippedGaps) {}
    private record Last(long tick, CoreBallistics.Vec position, CoreBallistics.Vec velocity) {}
    private final Map<UUID,Last> arrows=new HashMap<>();
    private long pairs, gaps;
    private double sumSquares, maxError;
    public void clear(){arrows.clear();pairs=0;gaps=0;sumSquares=0;maxError=0;}
    public void observe(UUID uuid,long tick,CoreBallistics.Vec pos,CoreBallistics.Vec vel){
        if(uuid==null || tick<0 || pos==null || vel==null || !pos.finite() || !vel.finite())return;
        Last before=arrows.get(uuid);
        if(before!=null && tick==before.tick()+1){
            var next=CanonicalArrowV48.air(before.position(),before.velocity(),1);
            if(next!=null){
                double error=next.at(1).position().minus(pos).length();
                if(Double.isFinite(error)){
                    sumSquares+=error*error;maxError=Math.max(maxError,error);pairs++;
                }
            }
        }else if(before!=null && tick>before.tick()+1)gaps++;
        if(!arrows.containsKey(uuid)&&arrows.size()>=32)return;
        if(before==null||tick>before.tick())arrows.put(uuid,new Last(tick,pos,vel));
    }
    public void expire(long currentTick){
        arrows.entrySet().removeIf(e->e.getValue().tick()>currentTick ||
                currentTick-e.getValue().tick()>12);
    }
    public Summary summary(){return new Summary(pairs,pairs==0?Double.NaN:Math.sqrt(sumSquares/pairs),
            pairs==0?Double.NaN:maxError,arrows.size(),gaps);}
}
