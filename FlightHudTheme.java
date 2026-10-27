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

    public static String name(int style) {return style == 0 ? "CLASSIC" : "TACTICAL";}
    public static String radar(int mode) {return mode == 0 ? "BORESIGHT" : "WIDE SCAN";}
    public static String lock(boolean manual) {return manual ? "STT LOCK" : "AUTO TRACK";}
    public static String computation(int mode) {
        return switch (mode) { case 0 -> "ECO TRACK";case 1 -> "MOTION SCAN";case 2 -> "BALLISTIC SCAN";default -> "FULL ANALYSIS";};
    }
    public static String cue(State state) {
        return switch (state) {
            case STANDBY -> "STANDBY";
            case ACQUIRING -> "ACQUIRING";
            case TRACK -> "TRACKING";
            case READY -> "FIRE CUE";
            case OBSTRUCTED -> "OBSTRUCTED";
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
