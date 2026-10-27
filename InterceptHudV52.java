package dev.leadshot;

import java.util.UUID;

/**
 * Read-only, client-tick HUD snapshot for G2G/G2A/A2G/A2A fire-control telemetry.
 * Never recalculates ballistics, changes targeting, sets READY or releases a bow.
 * Every published state expires on the next tick, and the UUID must still match.
 */
public final class InterceptHudV52 {
    public static final InterceptHudV52 INSTANCE = new InterceptHudV52();
    private Frame latest;
    private InterceptHudV52() {}

    public record Frame(UUID target,long tick,EngagementProfileV511.Theater theater,
                        boolean ready,boolean charging,boolean preReadyGuidance,
                        boolean futureGuidance,boolean clearRoute,
                        boolean timingEnabled,boolean waiting,
                        int waitTicks,int candidates) {}
    public record View(String theaterText,String stateText,String guidanceText,
                       String timingText,int stateColor) {}

    /** Clear before each targeting tick; incomplete scans must not retain old READY. */
    public synchronized void clear(){ latest=null; }
    public synchronized void publish(Frame frame){
        if(frame==null||frame.target()==null||frame.tick()<0||frame.theater()==null||
                frame.waitTicks()<0||frame.waitTicks()>3||frame.candidates()<0){
            latest=null;return;
        }
        latest=frame;
    }
    public synchronized View view(long currentTick,UUID currentTarget){
        Frame f=latest;
        if(f==null||currentTarget==null||currentTick!=f.tick()||
                !currentTarget.equals(f.target()))return null;
        String mode=switch(f.theater()){
            case GROUND_TO_GROUND -> "G2G / 地対地";
            case GROUND_TO_AIR -> "G2A / 地対空";
            case AIR_TO_GROUND -> "A2G / 空対地";
            case AIR_TO_AIR -> "A2A / 空対空";
        };
        String state;
        int color;
        if(!f.clearRoute()) {state="射線: 未確認・遮蔽";color=0xFFFFC879;}
        else if(f.waiting() && f.ready()) {
            state="迎撃待機: "+f.waitTicks()+" tick";color=0xFFFFCD71;
        }else if(f.ready()) {state="READY (推定)";color=0xFF78E5AE;}
        else if(f.charging()) {state="照準収束中";color=0xFF93D7F7;}
        else {state="照準候補を監視中";color=0xFFA1AEC0;}
        String guidance=f.futureGuidance()?"誘導: 未来迎撃角":
                f.preReadyGuidance()?"誘導: READY前先行":"誘導: 通常照準";
        String window=f.timingEnabled()?
                "時間探索: "+f.candidates()+" 候補 / 命中未保証":"時間探索: OFF / 命中未保証";
        return new View("迎撃 "+mode,state,guidance,window,color);
    }
}
