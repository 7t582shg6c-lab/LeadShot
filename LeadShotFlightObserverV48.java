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
    /** Read-only latest targeting sampler frame; no duplicate entity scan. */
    public static ObservationBusV483.Frame latestObservedFrame(){
        return SharedObservationV484.INSTANCE.latest(observedWorld,observedTick,1);
    }
    private static Object observedWorld;
    private static long observedTick;
    private Object lastWorld;
    private long tick;
    private boolean enabled;
    @Override public void onInitializeClient(){
        enabled=readEnabled();
        ClientTickEvents.END_CLIENT_TICK.register(this::sample);
        System.out.println("[LeadShot v0.48.4] 飛行物理・共有観測フレーム・運動モデル適応の診断="+(enabled?"有効":"無効")+
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
            lastWorld=mc.level;measurements.clear();
            tracks.reset(SharedObservationV484.INSTANCE.epoch());
            observedWorld=mc.level;
        }
        observedTick=tick;
        if(!enabled || mc.level==null || mc.player==null)return;
        var shooter=mc.player;
        // LeadShot has already ranked and sampled the same 64 contacts.
        // Its frame is our single input; no second living-entity world scan.
        // END_CLIENT_TICK handler ordering can differ, so at most one-tick age
        // is tolerated for diagnostic scoring, never for firing authorization.
        ObservationBusV483.Frame frame=SharedObservationV484.INSTANCE.latest(mc.level,tick,1);
        if(frame!=null)tracks.observeFrame(frame);
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
                    "[LeadShot v0.48.4] 追跡体数=%d, 予測評価=%d, CV-RMS=%.3f, CA-RMS=%.3f, TURN-RMS=%.3f, 急機動=%d, 補正/転送=%d, 欠測=%d (観測のみ、命中予測には不使用)%n",
                    motion.active(),motion.evaluated(),cv.rootMeanSquare(),
                    ca.rootMeanSquare(),turn.rootMeanSquare(),motion.maneuvers(),
                    motion.teleports(),motion.gaps());
            var model5=tracks.adaptiveCounts(5);
            var model10=tracks.adaptiveCounts(10);
            var model20=tracks.adaptiveCounts(20);
            System.out.printf(Locale.ROOT,
                    "[LeadShot v0.48.4] 学習済みモデル選択 5t=%s, 10t=%s, 20t=%s (CV/CA/CTの実測誤差を対象ごとに比較・発射には不使用)%n",
                    model5,model10,model20);
            System.out.printf(Locale.ROOT,
                    "[LeadShot v0.48] 空中矢の観測ペア=%d RMS誤差=%.5f 最大誤差=%.5f 追跡矢=%d 欠測=%d (クライアント側の推定)%n",
                    state.measuredPairs(),state.rootMeanSquareError(),
                    state.maximumError(),state.activeArrows(),state.skippedGaps());
        }
    }
}
