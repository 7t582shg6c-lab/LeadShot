package dev.leadshot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;
import java.util.Properties;

/** Opt-in phase-one physical calibration; does not touch LeadShot's target,
 * HUD, key mappings, READY, steering, or release state. Dedicated server safe:
 * the client sees only arrows that have been synchronized to it.
 */
public final class LeadShotFlightObserverV48 implements ClientModInitializer {
    private static final Path CONFIG=FabricLoader.getInstance().getConfigDir()
            .resolve("leadshot-flight-shadow.properties");
    private final ArrowTelemetryV48 measurements=new ArrowTelemetryV48();
    // Independent opt-in diagnostics: mirrors the 64 contact cap but does not
    // change LeadShot's original targeting or self-reported READY state.
    private final TrackRepositoryV481 tracks=new TrackRepositoryV481();
    private long worldEpoch;
    private Object lastWorld;
    private long tick;
    private boolean enabled;
    @Override public void onInitializeClient(){
        enabled=readEnabled();
        ClientTickEvents.END_CLIENT_TICK.register(this::sample);
        System.out.println("[LeadShot v0.48.1] 飛行物理・時刻付き64体追跡の診断="+(enabled?"有効":"無効")+
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
        if(lastWorld!=mc.level){
            lastWorld=mc.level;worldEpoch++;measurements.clear();tracks.reset(worldEpoch);
        }
        if(!enabled || mc.level==null || mc.player==null)return;
        var shooter=mc.player;
        // Time-stamped motion-model comparisons are strictly diagnostic.
        // The nearest 64 living entities form the measured contact set.
        var candidates=mc.level.getEntitiesOfClass(LivingEntity.class,
                shooter.getBoundingBox().inflate(96),
                e->e!=shooter && e.isAlive() && !e.isSpectator());
        candidates.sort(Comparator.comparingDouble(shooter::distanceToSqr));
        int contactCount=Math.min(TrackRepositoryV481.MAX_CONTACTS,candidates.size());
        var active=new HashSet<UUID>(contactCount);
        for(int i=0;i<contactCount;i++){
            var entity=candidates.get(i);
            var box=entity.getBoundingBox();
            var center=box.getCenter();
            var speed=entity.getDeltaMovement();
            UUID id=entity.getUUID();
            active.add(id);
            tracks.observe(new TimedObservationV48(id,worldEpoch,tick,
                    new CoreBallistics.Vec(center.x,center.y,center.z),
                    new CoreBallistics.Vec(speed.x,speed.y,speed.z),
                    new CoreBallistics.Vec(box.getXsize()*.5,box.getYsize()*.5,box.getZsize()*.5),
                    !entity.onGround()));
        }
        tracks.retain(worldEpoch,tick,active);
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
            var motion=tracks.summary();
            var cv=motion.models().get(MotionForecastV481.Model.CONSTANT_VELOCITY);
            var ca=motion.models().get(MotionForecastV481.Model.DAMPED_ACCELERATION);
            var turn=motion.models().get(MotionForecastV481.Model.DAMPED_TURN);
            System.out.printf(Locale.ROOT,
                    "[LeadShot v0.48.1] 追跡体数=%d, 予測評価=%d, CV-RMS=%.3f, CA-RMS=%.3f, TURN-RMS=%.3f, 急機動=%d, 補正/転送=%d, 欠測=%d (観測のみ、命中予測には不使用)%n",
                    motion.active(),motion.evaluated(),cv.rootMeanSquare(),
                    ca.rootMeanSquare(),turn.rootMeanSquare(),motion.maneuvers(),
                    motion.teleports(),motion.gaps());
            System.out.printf(Locale.ROOT,
                    "[LeadShot v0.48] 空中矢の観測ペア=%d RMS誤差=%.5f 最大誤差=%.5f 追跡矢=%d 欠測=%d (クライアント側の推定)%n",
                    state.measuredPairs(),state.rootMeanSquareError(),
                    state.maximumError(),state.activeArrows(),state.skippedGaps());
        }
    }
}
