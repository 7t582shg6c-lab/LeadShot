package dev.leadshot;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 26.3 vanilla block collision adapter. All calls remain on the client tick. */
public final class ClientBlockOcclusion {
    private ClientBlockOcclusion() {}

    public static BallisticOcclusion.SegmentProbe probe(ClientLevel level, Entity shooter) {
        return (a,b) -> {
            if (level == null || shooter == null) return BallisticOcclusion.ProbeResult.UNLOADED;
            Vec3 from = new Vec3(a.x(), a.y(), a.z());
            Vec3 to = new Vec3(b.x(), b.y(), b.z());
            // Never implicitly load another chunk just to paint a 'clear' indicator.
            if (!level.hasChunkAt(BlockPos.containing(from)) ||
                    !level.hasChunkAt(BlockPos.containing(to)) ||
                    !level.hasChunkAt(BlockPos.containing(from.lerp(to, .5))))
                return BallisticOcclusion.ProbeResult.UNLOADED;
            HitResult hit = level.clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            return hit.getType() == HitResult.Type.BLOCK
                    ? BallisticOcclusion.ProbeResult.BLOCKED
                    : BallisticOcclusion.ProbeResult.CLEAR;
        };
    }
}
