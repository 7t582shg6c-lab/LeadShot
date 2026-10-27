package dev.leadshot;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** Synthetic stub contract only; uses NOT REAL Minecraft classes. */
public final class ClientBlockOcclusionStubTest {
    public static void main(String[] args) {
        ClientLevel fake = new ClientLevel();
        var initial = new CoreBallistics.Vec(0,0,0);
        var end = new CoreBallistics.Vec(0,0,1);
        var probe = ClientBlockOcclusion.probe(fake, new Entity());
        if (probe.check(initial,end)!=BallisticOcclusion.ProbeResult.CLEAR) throw new AssertionError("stub clear");
        fake.blocked = true;
        if (probe.check(initial,end)!=BallisticOcclusion.ProbeResult.BLOCKED) throw new AssertionError("stub blocked");
        fake.loaded = false;
        if (probe.check(initial,end)!=BallisticOcclusion.ProbeResult.UNLOADED) throw new AssertionError("stub unloaded");
        if (ClientBlockOcclusion.probe(null,null).check(initial,end)!=BallisticOcclusion.ProbeResult.UNLOADED)
            throw new AssertionError("null world");
        System.out.println("PASS ClientBlockOcclusionStubTest 4 synthetic cases (NOT Minecraft)");
    }
}
