package dev.leadshot;

/**
 * Work DEPTH applies uniformly to all 64 tracked entities, never to a subset
 * selected by performance tier. Heavy operations are round-robin time-sliced
 * to keep each client tick bounded. No cached value can authorize a shot.
 */
public final class PrecomputePolicy {
    private PrecomputePolicy() {}
    public static final int LOW=0, DEFAULT=1, HIGH=2, ALL=3;
    public static final int MAX_TRACKED=64;
    public static final int ACTIVE_CANDIDATES=12; // Independent of performance mode.
    public record Plan(int stage, int heavyUpdatesPerTick, int cacheTtlTicks) {
        public Plan {
            if(stage<0 || stage>3 || heavyUpdatesPerTick<0 || cacheTtlTicks<0)
                throw new IllegalArgumentException("invalid precompute plan");
        }
        public int trackedLimit(){return MAX_TRACKED;}
        public int activeCandidateLimit(){return ACTIVE_CANDIDATES;}
        public boolean precomputesMotion(){return stage>=DEFAULT;}
        public boolean precomputesBallistics(){return stage>=HIGH;}
        public boolean precomputesAudits(){return stage>=ALL;}
        public String detail(){return switch(stage) {
            case LOW -> "位置・速度の追跡";
            case DEFAULT -> "追跡＋未来位置(5/10/20t)";
            case HIGH -> "追跡＋未来位置＋最大チャージ弾道";
            default -> "追跡＋弾道＋衝突・遮蔽・3モデル";
        };}
    }
    public static Plan plan(int mode){return switch(mode){
        case LOW -> new Plan(LOW,0,0);
        case HIGH -> new Plan(HIGH,8,12);
        case ALL -> new Plan(ALL,4,24);
        default -> new Plan(DEFAULT,0,0);
    };}
    public static String name(int mode){return switch(mode){
        case LOW -> "省電力追跡";case HIGH -> "弾道予測";case ALL -> "全段階解析";default -> "動体予測";};}
}
