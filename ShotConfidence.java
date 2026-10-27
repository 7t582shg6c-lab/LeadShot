package dev.leadshot;

/** Unified, conservative HUD/marker palette. No status indicates a guaranteed hit. */
public final class ShotConfidence {
    public enum Status { BLOCKED, CONTACT_UNSUPPORTED, MODEL_DISAGREEMENT, UNVERIFIED, CONSISTENT_ESTIMATE }
    private ShotConfidence() {}
    public static Status classify(ContactAudit.Result contact,
           TrajectoryConsensus.Result consensus, BallisticOcclusion.Result blocks) {
        if (blocks != null && blocks.state()==BallisticOcclusion.State.BLOCKED) return Status.BLOCKED;
        if (contact == null || !contact.geometricContact()
                || consensus == null || consensus.state()==TrajectoryConsensus.State.INVALID
                || consensus.state()==TrajectoryConsensus.State.NO_CONTACT) return Status.CONTACT_UNSUPPORTED;
        if (consensus.state()==TrajectoryConsensus.State.DISPUTED) return Status.MODEL_DISAGREEMENT;
        if (blocks == null || blocks.state()!=BallisticOcclusion.State.CLEAR) return Status.UNVERIFIED;
        return Status.CONSISTENT_ESTIMATE;
    }
    public static int color(Status s) {
        return switch(s) {
            case BLOCKED -> 0xFFFF6666;
            case CONTACT_UNSUPPORTED -> 0xFFFF9A65;
            case MODEL_DISAGREEMENT -> 0xFFFFC774;
            case UNVERIFIED -> 0xFFFFC774;
            case CONSISTENT_ESTIMATE -> 0xFF70DEA6;
        };
    }
    public static String japanese(Status s) {
        return switch(s) {
            case BLOCKED -> "BLOCKED / 遮蔽あり";
            case CONTACT_UNSUPPORTED -> "NO CONTACT / 幾何支持なし";
            case MODEL_DISAGREEMENT -> "DISPUTED / 予測不一致";
            case UNVERIFIED -> "UNCHECKED / 経路未確定";
            case CONSISTENT_ESTIMATE -> "CONSISTENT / 推定一致 (命中保証なし)";
        };
    }
}
