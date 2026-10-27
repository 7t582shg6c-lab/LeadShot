package dev.leadshot;

/** Bounded trend compensation for fast, manoeuvring targets (including gliders).
 * Uses the change in filtered velocity as a small angular lead correction.
 * It deliberately models a constant corrected velocity afterwards so that
 * ContactAudit and TrajectoryConsensus use the same trajectory assumptions.
 * A snap turn, rocket boost, server correction or teleport remains unpredictable.
 */
public final class FlightTargetPredictor {
    private FlightTargetPredictor() {}
    public static CoreBallistics.Vec forecast(CoreBallistics.Vec current,CoreBallistics.Vec previous){
        if(current==null||!current.finite())return new CoreBallistics.Vec(0,0,0);
        if(previous==null||!previous.finite())return current;
        double speed=current.length();
        if(speed<.65)return current; // No acceleration compensation on slow mobs.
        var change=current.minus(previous);
        double amount=change.length();
        if(!Double.isFinite(amount)||amount<.045)return current;
        double correction=Math.min(amount*.65,Math.min(.55,speed*.22));
        var predicted=current.add(change.times(correction/amount));
        double newSpeed=predicted.length();
        if(!predicted.finite()||newSpeed>12.5)return current;
        return predicted;
    }
}
