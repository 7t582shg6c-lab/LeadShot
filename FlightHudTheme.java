package dev.leadshot;

/** Aircraft-inspired presentation vocabulary; no gameplay logic or client API dependencies. */
public final class FlightHudTheme {
    private FlightHudTheme() {}
    public static final int CYAN = 0xFF82E8FF;
    public static final int WHITE = 0xFFE8F6FF;
    public static final int MUTED = 0xFF91B8C6;
    public static final int GREEN = 0xFF6EFBB2;
    public static final int AMBER = 0xFFFFD180;
    public static final int RED = 0xFFFF7777;
    public static final int PANEL = 0xC5091620;
    public static final int BORDER = 0xCC3C8797;

    public enum State { STANDBY, ACQUIRING, TRACK, READY, OBSTRUCTED }

    public static String name(int style) {return style == 0 ? "標準表示" : "戦術表示";}
    public static String radar(int mode) {return mode == 0 ? "中央優先" : "広域索敵";}
    public static String lock(boolean manual) {return manual ? "手動固定" : "自動追跡";}
    public static String computation(int mode) {
        return switch (mode) { case 0 -> "省電力追跡";case 1 -> "動体予測";case 2 -> "弾道予測";default -> "全段階解析";};
    }
    public static String cue(State state) {
        return switch (state) {
            case STANDBY -> "待機中";
            case ACQUIRING -> "索敵中";
            case TRACK -> "追跡中";
            case READY -> "射撃可能（推定）";
            case OBSTRUCTED -> "射線遮蔽";
        };
    }
    public static int color(State state) {
        return switch (state) {
            case READY -> GREEN;
            case OBSTRUCTED -> RED;
            case TRACK, STANDBY -> CYAN;
            case ACQUIRING -> AMBER;
        };
    }
    /** READINESS is a tentative computed cue, not a guaranteed hit. */
    public static State state(boolean ready,boolean blocked,boolean hasSolution,boolean hasTarget,boolean drawing){
        if(blocked)return State.OBSTRUCTED;
        if(ready && hasSolution)return State.READY;
        if(hasSolution || hasTarget)return State.TRACK;
        if(drawing)return State.ACQUIRING;
        return State.STANDBY;
    }
}
