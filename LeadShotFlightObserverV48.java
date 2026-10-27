package dev.leadshot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Opt-in phase-one physical calibration; does not touch LeadShot's target,
 * HUD, key mappings, READY, steering, or release state. Dedicated server safe:
 * the client sees only arrows that have been synchronized to it.
 */
public final class LeadShotFlightObserverV48 implements ClientModInitializer {
    private static final Path CONFIG=FabricLoader.getInstance().getConfigDir()
            .resolve("leadshot-flight-shadow.properties");
    private final ArrowTelemetryV48 measurements=new ArrowTelemetryV48();
    private Object lastWorld;
    private long tick;
    private boolean enabled;
    @Override public void onInitializeClient(){
        enabled=readEnabled();
        ClientTickEvents.END_CLIENT_TICK.register(this::sample);
        System.out.println("[LeadShot v0.48] 飛行物理の診断="+(enabled?"有効":"無効")+
                " (config/leadshot-flight-shadow.properties)");
    }
    private static boolean readEnabled(){
        if(!Files.isRegularFile(CONFIG))return false;
        try(InputStream in=Files.newInputStream(CONFIG)){
            Properties p=new Properties();p.load(in);
            return Boolean.parseBoolean(p.getProperty("enabled","false"));
        }catch(Exception e){return false;}
    }
    private void sample(Minecraft mc){
        tick++;
        if(lastWorld!=mc.level){lastWorld=mc.level;measurements.clear();}
        if(!enabled || mc.level==null || mc.player==null)return;
        var shooter=mc.player;
        var arrows=mc.level.getEntitiesOfClass(AbstractArrow.class,
                shooter.getBoundingBox().inflate(96),
                arrow -> arrow.getOwner()==shooter && !(arrow instanceof ThrownTrident));
        int count=Math.min(32,arrows.size());
        for(int i=0;i<count;i++){
            var arrow=arrows.get(i);
            Vec3 p=arrow.position(),v=arrow.getDeltaMovement();
            measurements.observe(arrow.getUUID(),tick,
                    new CoreBallistics.Vec(p.x,p.y,p.z),
                    new CoreBallistics.Vec(v.x,v.y,v.z));
        }
        measurements.expire(tick);
        if(tick%1200==0){
            var state=measurements.summary();
            System.out.printf(Locale.ROOT,
                    "[LeadShot v0.48] 空中矢の観測ペア=%d RMS誤差=%.5f 最大誤差=%.5f 追跡矢=%d 欠測=%d (クライアント側の推定)%n",
                    state.measuredPairs(),state.rootMeanSquareError(),
                    state.maximumError(),state.activeArrows(),state.skippedGaps());
        }
    }
}
