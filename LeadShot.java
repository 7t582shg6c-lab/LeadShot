package dev.leadshot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric 26.3 integrated ALPHA. Client-only 2D projection of 3D math.
 * Assist is opt-in. Bow trajectory math runs only at full draw; idle radar uses lightweight acquisition.
 */
public final class LeadShot implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("leadshot", "main"));
    private static final KeyMapping HUD = key("key.leadshot.hud_visibility", InputConstants.KEY_U);
    private static final KeyMapping PREVIEW = key("key.leadshot.preview", InputConstants.KEY_V);
    private static final KeyMapping TARGET = key("key.leadshot.cycle_target", InputConstants.KEY_N);
    private static final KeyMapping MARKERS = key("key.leadshot.markers", InputConstants.KEY_G);
    private static final KeyMapping MODES = key("key.leadshot.hud_mode", InputConstants.KEY_B);
    private static final KeyMapping LOCK_MODE = key("key.leadshot.lock_mode", InputConstants.KEY_I);
    private static final KeyMapping LOCK_NEXT = key("key.leadshot.lock_next", InputConstants.KEY_P);
    private static final KeyMapping LOCK_CLEAR = key("key.leadshot.lock_clear", InputConstants.KEY_K);
    private static final KeyMapping HUD_STYLE = key("key.leadshot.hud_style", InputConstants.KEY_H);
    private static final KeyMapping AIM_ASSIST = key("key.leadshot.aim_assist", InputConstants.KEY_J);
    // Hold ATTACK+USE while fully drawn to request repeated, gated releases.
    private static final ContinuousReleaseGate RELEASE_GATE = new ContinuousReleaseGate();
    private static final KeyMapping SETTINGS = key("key.leadshot.settings", InputConstants.KEY_O);
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("leadshot-26.3.properties");
    private static final Path OVERLAY_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("leadshot-scan-overlay.properties");
    private static int overlayMode = ScanOverlayPreference.VISIBLE;
    private static final Path ADVANCED_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("leadshot-aim-advanced.properties");
    private static AdvancedAimOptions advancedAim = AdvancedAimOptions.defaults();
    private static final MultiplayerAimSync AIM_SYNC = new MultiplayerAimSync();
    private static List<ScanContact> scanContacts = List.of();
    private static long lastUseAttemptTick=Long.MIN_VALUE;
    private static final Path GUIDE_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("leadshot-guides.properties");
    private static GuideControls guideOptions = GuideControls.defaults();
    private static boolean locked;
    private static boolean nextLockRequested;
    private static final Map<UUID, Tracked> observations = new HashMap<>();
    private static final FullDrawWarmCache WARM = new FullDrawWarmCache();
    private static final StagedPrecomputeCache STAGES = new StagedPrecomputeCache();
    private static final RoundRobinPrecompute PRECOMPUTE_ORDER = new RoundRobinPrecompute();
    private static final MotionShadowAudit SHADOW = new MotionShadowAudit();
    private static final ShotReleaseAudit RELEASE_AUDIT = new ShotReleaseAudit();
    private static final ObservedArrowAudit ARROW_AUDIT = new ObservedArrowAudit();
    private static boolean previousBowUse;
    // Immutable geometry prepared on the client tick for a future level-render adapter.
    // Currently NO world-space draw event is registered for this frame.
    private static WorldGuideGeometry.Frame worldGuide = WorldGuideGeometry.Frame.empty();
    private static LeadShotOptions options = LeadShotOptions.defaults();
    private static Object lastLevel;
    private static long tick;
    private static UUID selected;
    private static Preview preview = Preview.empty();
    private static final GuideFrameSmoother SMOOTHER = new GuideFrameSmoother();
    private static final AimReadySignal READY = new AimReadySignal();
    private static AimReadySignal.State readyState = new AimReadySignal.State(false,false,0);
    private static long readyFlashUntil = -1;

    private record Tracked(CoreBallistics.Vec center, long tick, CoreBallistics.Vec filteredVelocity) {}
    private record RadarMark(UUID id, CoreBallistics.Vec center, String name, boolean locked) {}
    private record ScanContact(UUID id, CoreBallistics.Vec previous, CoreBallistics.Vec center, CoreBallistics.Vec half) {}
    private static RadarMark radarMark;
    private static int radarCount;
    private record Candidate(LivingEntity entity, CoreBallistics.Solution shot,
                             CoreBallistics.Vec center, CoreBallistics.Vec velocity,
                             CoreBallistics.Vec half, ContactAudit.Result collision,
                             double score) {}
    private record Preview(String target,String category,double range,
                           CoreBallistics.Vec eye,CoreBallistics.Vec shooterVelocity,
                           CoreBallistics.Solution solution,ContactAudit.Result collision,
                           boolean drawing,CoreBallistics.Vec half, BallisticOcclusion.Result obstruction,
                           TrajectoryConsensus.Result consensus) {
        static Preview empty(){return new Preview("なし","-",0,null,null,null,null,false,null,null,null);}
        static Preview charging(){return charging("最大チャージ待機");}
        static Preview charging(String name){return new Preview(name,"-",0,null,null,null,null,true,null,null,null);}
    }
    private static KeyMapping key(String text,int id){
        return new KeyMapping(text,InputConstants.Type.KEYBOARD,id,CATEGORY);
    }
    @Override public void onInitializeClient(){
        try{options=LeadShotOptions.read(CONFIG);}catch(Exception e){System.err.println("[LeadShot] config read: "+e);}
        try{guideOptions=GuideControls.read(GUIDE_CONFIG);}catch(Exception e){System.err.println("[LeadShot] guide config: "+e);}
        overlayMode=ScanOverlayPreference.read(OVERLAY_CONFIG);
        advancedAim=AdvancedAimOptions.read(ADVANCED_CONFIG);
        for(KeyMapping k:List.of(HUD,PREVIEW,TARGET,MARKERS,MODES,SETTINGS,LOCK_MODE,LOCK_NEXT,LOCK_CLEAR,AIM_ASSIST,HUD_STYLE))
            KeyMappingHelper.registerKeyMapping(k);
        ClientTickEvents.END_CLIENT_TICK.register(LeadShot::onTick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("leadshot", "hud"), (graphics,delta)->drawHud(graphics,delta.getGameTimeDeltaPartialTick(false)));
    }
    private static void onTick(Minecraft mc){
        tick++;
        worldGuide = WorldGuideGeometry.Frame.empty();
        if(mc.level!=lastLevel){lastLevel=mc.level;observations.clear();WARM.clear();STAGES.clear();PRECOMPUTE_ORDER.reset();SHADOW.clear();RELEASE_AUDIT.clear();ARROW_AUDIT.clear();previousBowUse=false;selected=null;locked=false;nextLockRequested=false;preview=Preview.empty();radarMark=null;radarCount=0;SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);readyFlashUntil=-1;RELEASE_GATE.reset();AIM_SYNC.reset();scanContacts=List.of();lastUseAttemptTick=Long.MIN_VALUE;}
        while(HUD.consumeClick())if(mc.gui.screen()==null)mutate("hud");
        while(PREVIEW.consumeClick())if(mc.gui.screen()==null)mutate("preview");
        while(TARGET.consumeClick())if(mc.gui.screen()==null)mutate("target");
        while(MARKERS.consumeClick())if(mc.gui.screen()==null)mutate("guide");
        while(MODES.consumeClick())if(mc.gui.screen()==null)mutate("mode");
        while(LOCK_MODE.consumeClick())if(mc.gui.screen()==null){locked=!locked;selected=null;SMOOTHER.reset();nextLockRequested=false;}
        while(LOCK_NEXT.consumeClick())if(mc.gui.screen()==null){locked=true;nextLockRequested=true;}
        while(LOCK_CLEAR.consumeClick())if(mc.gui.screen()==null){locked=false;selected=null;SMOOTHER.reset();nextLockRequested=false;}
        while(AIM_ASSIST.consumeClick())if(mc.gui.screen()==null)changeGuide("autoaim");
        while(HUD_STYLE.consumeClick())if(mc.gui.screen()==null)changeGuide("hudstyle");
        while(SETTINGS.consumeClick())if(mc.gui.screen()==null)mc.gui.setScreen(new LeadShotSettingsScreen());
        LocalPlayer shooter=mc.player;
        if(shooter==null||mc.level==null){preview=Preview.empty();radarMark=null;radarCount=0;SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);previousBowUse=false;RELEASE_GATE.reset();AIM_SYNC.reset();scanContacts=List.of();return;}
        AABB search=shooter.getBoundingBox().inflate(guideOptions.depthBlocks());
        List<LivingEntity> nearby=mc.level.getEntitiesOfClass(LivingEntity.class,search,
                e->e!=shooter && e.isAlive() && !e.isSpectator() && eligible(e)
                        && shooter.distanceToSqr(e)<=
                        (double)guideOptions.depthBlocks()*guideOptions.depthBlocks());
        nearby.sort(Comparator.comparingDouble(shooter::distanceToSqr));
        // Without pinning, a live manual lock can drop out of a 64-slot tracker
        // solely because nearer mobs entered the scan volume.
        List<LivingEntity> targets=TrackingBudget.choose(nearby,
                e->selected!=null && selected.equals(e.getUUID()),64);
        int count=targets.size();
        // Keep explicit locks through temporary LOS loss, but hand off immediately
        // once the entity is dead, unloaded or has left the configured range.
        if(locked && selected!=null && !RadarLockHandoff.aliveNearby(selected,
                targets.stream().map(LivingEntity::getUUID).toList())){
            selected=null;nextLockRequested=false;SMOOTHER.reset();READY.reset();
        }
        radarCount=count;
        // Lightweight all-mode boxes are independent of bow charge and radar lock.
        // AABB snapshots are captured on the client tick; HUD simply projects them.
        if(overlayMode!=ScanOverlayPreference.OFF && options.guide()){
            List<ScanContact> contacts=new ArrayList<>(count);
            for(LivingEntity e:targets){
                // Skip rear contacts before performing up to 8 camera projections.
                if(Math.abs(currentBearingYaw(shooter,e))>95 ||
                   Math.abs(currentBearingPitch(shooter,e))>85)continue;
                AABB box=e.getBoundingBox();
                // Snapshot the real 3-axis hitbox, not an estimated screen size.
                // The render pass uses Minecraft's live camera and FOV.
                boolean visible=overlayMode==ScanOverlayPreference.ALL || canSeeBlockFree(mc,shooter,e);
                if(visible){
                    CoreBallistics.Vec now=vec(box.getCenter());
                    Tracked last=observations.get(e.getUUID());
                    CoreBallistics.Vec previous=last!=null && tick-last.tick()==1?last.center():now;
                    contacts.add(new ScanContact(e.getUUID(),previous,now,
                            new CoreBallistics.Vec(box.getXsize()*.5,box.getYsize()*.5,box.getZsize()*.5)));
                }
            }
            scanContacts=List.copyOf(contacts);
        }else scanContacts=List.of();
        Map<UUID,CoreBallistics.Vec> speeds=new HashMap<>();
        Map<UUID,CoreBallistics.Vec> ballisticSpeeds=new HashMap<>();
        PrecomputePolicy.Plan depthPlan=PrecomputePolicy.plan(guideOptions.computationMode());
        for(int i=0;i<count;i++){
            LivingEntity entity=targets.get(i);
            CoreBallistics.Vec center=vec(entity.getBoundingBox().getCenter());
            UUID id=entity.getUUID();Tracked old=observations.get(id);
            CoreBallistics.Vec velocity=estimate(center,old,tick);
            speeds.put(id,velocity);
            ballisticSpeeds.put(id,FlightTargetPredictor.forecast(velocity,old==null?null:old.filteredVelocity()));
            observations.put(id,new Tracked(center,tick,velocity));
            STAGES.observe(id,tick,center,velocity,depthPlan.precomputesMotion());
            if(options.shadowAudit()){
                SHADOW.observe(id,tick,center.x(),center.y(),center.z());
                RELEASE_AUDIT.observe(id,tick,center);
            }
        }
        observations.entrySet().removeIf(e->tick-e.getValue().tick()>25);
        // Purge entity caches on death/despawn/category change; never reuse UUID stale data.
        WARM.retain(speeds.keySet());
        STAGES.retain(speeds.keySet());
        // All performance modes run the same 64-slot tracker. Heavy stages are time-sliced.
        advancePrecompute(mc,shooter,targets,ballisticSpeeds,depthPlan);
        if(options.shadowAudit()){
            SHADOW.expire(tick);
            RELEASE_AUDIT.expire(tick);
            if(tick%1200==0){
                System.out.println(SHADOW.fullDiagnosticLine());
                // Aggregated anonymous diagnostics only, no target names or coordinates.
                System.out.println(RELEASE_AUDIT.diagnosticLine());
                var arrowStats = ARROW_AUDIT.summary();
                System.out.printf(Locale.ROOT, "観測矢: 候補%d 矢確認%d 未確認%d 軌道ペア%d (命中未判定)%n",
                        arrowStats.useEnds(), arrowStats.observedLaunches(),
                        arrowStats.unmatchedUseEnds(), arrowStats.trajectoryPairs());
            }
        }
        boolean bow=shooter.isUsingItem()&&shooter.getUseItem().getItem() instanceof BowItem;
        // "Use stopped" is NOT proof that a server accepted a bow shot, or that an
        // arrow spawned. This checks only predicted target position vs observed
        // target position at the future tick. The previous tick's estimate is
        // extrapolated one extra observed-velocity tick to align release time.
        if(previousBowUse && !bow && options.shadowAudit() && options.preview()
                && selected!=null && preview.solution()!=null && speeds.containsKey(selected)) {
            CoreBallistics.Vec future = preview.solution().futureCenter().add(speeds.get(selected));
            RELEASE_AUDIT.recordUseEnd(selected,tick,preview.solution().ticks(),future);
        }
        if(previousBowUse && !bow && options.shadowAudit() && options.preview()
                && preview.solution()!=null && preview.shooterVelocity()!=null) {
            // Bow use cessation is a CANDIDATE; it is not a verified projectile.
            // A release is only counted as observed after an owned arrow appears.
            ARROW_AUDIT.noteUseEnd(tick, vec(shooter.getEyePosition()),
                    preview.solution().launchVelocity().add(preview.shooterVelocity()));
        }
        if(options.shadowAudit()) {
            // Reads client-visible entities only. A null/missing arrow owner is
            // deliberately skipped rather than pretending a launch was observed.
            var arrows = mc.level.getEntitiesOfClass(AbstractArrow.class,
                    shooter.getBoundingBox().inflate(96),
                    arrow -> arrow.getOwner() == shooter && !(arrow instanceof ThrownTrident));
            int arrowLimit = Math.min(arrows.size(), 48);
            for(int i=0; i<arrowLimit; i++) {
                var arrow=arrows.get(i);
                ARROW_AUDIT.observe(tick,arrow.getUUID(),vec(arrow.position()),vec(arrow.getDeltaMovement()));
            }
            ARROW_AUDIT.expire(tick);
        }
        previousBowUse=bow;
        // Both buttons are deliberately held for assisted repeat fire. On some
        // multiplayer clients, holding ATTACK starts block breaking after a
        // release and prevents Minecraft.startUseItem from restarting the bow.
        // Request a regular, prediction-aware vanilla item-use instead of
        // sending a raw packet. Never send more than one retry per six ticks.
        boolean repeatHeld=guideOptions.assistedFire() && options.preview()
                && mc.gui.screen()==null && mc.options.keyAttack.isDown()
                && mc.options.keyUse.isDown();
        if(repeatHeld && !bow && !shooter.isUsingItem() && !shooter.isSpectator()
                && shooter.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BowItem
                && mc.gameMode!=null
                && (lastUseAttemptTick==Long.MIN_VALUE || tick-lastUseAttemptTick>=6)){
            if(mc.gameMode.isDestroying())mc.gameMode.stopDestroyBlock();
            lastUseAttemptTick=tick;
            mc.gameMode.useItem(shooter,InteractionHand.MAIN_HAND);
            // No ballistic solve or shot until a *new* full bow draw completes.
        }
        // A release requires the current draw cycle to finish, and the next
        // shot requires a NEW draw cycle (no release spam in a single charge).
        if(!bow||shooter.getTicksUsingItem()<20)RELEASE_GATE.rearm();
        if(!options.preview()){
            preview=Preview.empty();SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);
            // RADAR display remains usable when only ballistic preview is disabled.
            acquireRadarWithoutBow(mc,shooter,targets,count);
            return;
        }
        if(!bow){
            SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);
            acquireRadarWithoutBow(mc,shooter,targets,count);
            return;
        }
        preview=Preview.charging();
        // Before 20 use-ticks: no ballistic solution, collision ray tracing,
        // consensus model, or READY. Lightweight tracker/radar still runs.
        if(shooter.getTicksUsingItem()<20){
            SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);
            acquireRadarWithoutBow(mc,shooter,targets,count);
            preview=Preview.charging(radarMark==null?"最大チャージ待機":radarMark.name());
            return;
        }
        // Keep warm candidates current while fully drawn as well: retargeting should
        // not require an entire initial prediction sweep at the moment of the switch.
        double power=CoreBallistics.vanillaBowPower(shooter.getTicksUsingItem());
        double speed=CoreBallistics.FULL_BOW_SPEED*power;
        if(speed<.3){SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);return;}
        CoreBallistics.Vec eye=vec(shooter.getEyePosition());
        CoreBallistics.Vec shootVelocity=vec(shooter.getDeltaMovement());
        Candidate best=null, previous=null;
        List<Candidate> candidates=new ArrayList<>();
        // Constrain per-tick work; protect a selected target outside the near-only cap.
        List<LivingEntity> ranked=new ArrayList<>(targets.subList(0,count));
        ranked.sort(Comparator.comparingDouble(e -> preliminaryScore(shooter,e)));
        if(selected!=null)for(int i=0;i<ranked.size();i++)
            if(ranked.get(i).getUUID().equals(selected)){
                LivingEntity keep=ranked.remove(i);ranked.add(0,keep);break;
            }
        // The ACTIVE screening cap does not vary with the precompute stage.
        int candidateCap=PrecomputePolicy.ACTIVE_CANDIDATES;
        for(int i=0;i<Math.min(candidateCap,ranked.size());i++){
            LivingEntity entity=ranked.get(i);
            if(shooter.distanceTo(entity)>guideOptions.depthBlocks())continue;
            if(guideOptions.lineOfSightOnly()&&!canSeeBlockFree(mc,shooter,entity))continue;
            CoreBallistics.Vec center=vec(entity.getBoundingBox().getCenter());
            CoreBallistics.Vec velocity=ballisticSpeeds.get(entity.getUUID());
            CoreBallistics.Vec compensated=TargetVelocityFilter.lead(center,velocity,advancedAim.latencyTicks());
            CoreBallistics.Solution shot=depthPlan.precomputesBallistics()
                    ? WARM.peek(entity.getUUID(),tick,eye,compensated,velocity,
                        shootVelocity,depthPlan.cacheTtlTicks()) : null;
            if(shot==null)shot=CoreBallistics.solve(eye,compensated,velocity,shootVelocity,speed);
            if(shot==null||shot.speedMismatch()>Math.max(.18,speed*.10))continue;
            double yaw=Math.abs(CoreBallistics.wrap(shot.yaw()-shooter.getYRot()));
            double pitch=Math.abs(shot.pitch()-shooter.getXRot());
            if(!Double.isFinite(yaw)||!Double.isFinite(pitch))continue;
            // Search cone describes *visible body location*, not lead angle:
            // a moving body in the centre must not disappear when AIM moves aside.
            // Do not disqualify the actively tracked target when steering moves
            // the crosshair from its body onto the ballistic lead point.
            boolean aimHold=AimTargetRetention.active(guideOptions.autoAim(),bow);
            if(guideOptions.searchMode()==0 &&
                    currentReticleMiss(shooter,entity)>guideOptions.radiusDegrees() &&
                    !AimTargetRetention.bypassCone(aimHold,selected,entity.getUUID()))continue;
            AABB bb=entity.getBoundingBox();
            CoreBallistics.Vec half=new CoreBallistics.Vec(
                    Math.max(.01,bb.getXsize()*.5),Math.max(.01,bb.getYsize()*.5),Math.max(.01,bb.getZsize()*.5));
            // Do not simulate detailed moving-hitbox contact for every candidate.
            // Only the selected target gets the full contact+occlusion+consensus suite.
            double directMiss=currentReticleMiss(shooter,entity);
            double score=guideOptions.searchMode()==1
                    ? TargetSelectionV43.radarScore(directMiss,shooter.distanceTo(entity))
                            +shot.speedMismatch()*8
                    : TargetSelectionV43.score(directMiss,0,yaw,pitch,
                            shooter.distanceTo(entity))+shot.speedMismatch()*8;
            Candidate c=new Candidate(entity,shot,compensated,velocity,half,null,score);
            candidates.add(c);
            if(best==null||c.score()<best.score())best=c;
            if(entity.getUUID().equals(selected))previous=c;
        }
        if(best==null){if(!locked)selected=null;radarMark=null;SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);return;}
        Candidate chosen=best;
        if(locked){
            // If an existing lock goes out of view/range, NEVER silently follow a different mob.
            // Only an explicit next command may change the stored UUID.
            if(previous==null && selected!=null && !nextLockRequested){
                // Preserve UUID, but never leave the last tick's visible marker
                // on a new obstruction / invalid trajectory.
                radarMark=null;
                SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);
                return;
            }
            // A manual lock stays on its target while that target passes all filters.
            // Cycling only considers valid, visible and solution-feasible candidates.
            candidates.sort(Comparator.comparingDouble(Candidate::score));
            if(nextLockRequested){
                int index=-1;
                for(int i=0;i<candidates.size();i++)if(candidates.get(i).entity().getUUID().equals(selected)){index=i;break;}
                chosen=candidates.get((index+1)%candidates.size());
                nextLockRequested=false;
            }else if(previous!=null)chosen=previous;
        }else if(AimTargetRetention.holdPrevious(
                AimTargetRetention.active(guideOptions.autoAim(),bow), previous!=null)){
            // During a drawn-bow assist cycle keep tracking the *same UUID*,
            // even if its body moves away from the crosshair as lead is applied.
            chosen=previous;
        }else if(previous!=null && previous!=best){
            double previousDirect=currentReticleMiss(shooter,previous.entity());
            double bestDirect=currentReticleMiss(shooter,best.entity());
            if(!TargetSelectionV43.preferNew(previous.score(),best.score(),previousDirect,bestDirect))chosen=previous;
        }
        selected=chosen.entity().getUUID();
        // ALWAYS recompute the selected trajectory from CURRENT observation before
        // contact tests, guidance, READY, steering or assisted fire. Cached hints
        // must never authorize a shot or display falsely fresh contact geometry.
        CoreBallistics.Solution preciseShot=CoreBallistics.solveRefined(eye,chosen.center(),chosen.velocity(),
                shootVelocity,CoreBallistics.FULL_BOW_SPEED);
        if(preciseShot==null || preciseShot.speedMismatch()>Math.max(.18,speed*.10)){
            radarMark=null;
            SMOOTHER.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);return;
        }
        ContactAudit.Result preciseContact=ContactAudit.evaluate(eye,preciseShot,chosen.center(),
                chosen.velocity(),chosen.half(),shootVelocity,speed,Math.max(.18,speed*.10));
        chosen=new Candidate(chosen.entity(),preciseShot,chosen.center(),chosen.velocity(),
                chosen.half(),preciseContact,chosen.score());
        WARM.store(selected,tick,eye,chosen.center(),chosen.velocity(),shootVelocity,preciseShot);
        radarMark=new RadarMark(selected,chosen.center(),chosen.entity().getName().getString(),locked);
        // Ray-test only the ONE selected solution, not every candidate. A geometric
        // contact is necessary but never sufficient for an actual successful hit.
        BallisticOcclusion.Result obstruction = options.blockCheck() && chosen.collision().geometricContact()
                ? BallisticOcclusion.evaluate(eye,
                        chosen.shot().launchVelocity().add(shootVelocity),
                        chosen.collision().contactTick(),
                        ClientBlockOcclusion.probe(mc.level, shooter))
                : new BallisticOcclusion.Result(BallisticOcclusion.State.UNCHECKED,0,0,Double.NaN);
        // Only evaluate the selected candidate to keep CPU cost bounded.
        TrajectoryConsensus.Result consensus = TrajectoryConsensus.evaluate(
                eye, chosen.shot().launchVelocity().add(shootVelocity),
                chosen.center(), chosen.velocity(), chosen.half(), .12,
                Math.min(CoreBallistics.MAX_TICKS, chosen.shot().ticks()+3));
        if(options.guide()) {
            worldGuide=WorldGuideGeometry.build(eye, chosen.shot().yaw(), chosen.shot().pitch(),
                    shooter.getYRot(), shooter.getXRot(),chosen.shot().futureCenter(),
                    chosen.half(),chosen.shot().launchVelocity().add(shootVelocity),
                    chosen.shot().ticks(),options.trajectory());
        }
        // The HUD renders at display FPS, not Minecraft's 20-tick sampling rate.
        // Feed WORLD-space values into the filter; render projects them with the
        // CURRENT camera to keep mouse movement immediate. Lock switches snap.
        SMOOTHER.observe(selected,new GuideFrameSmoother.Frame(
                chosen.shot().yaw(),chosen.shot().pitch(),chosen.shot().futureCenter(),
                eye,chosen.shot().launchVelocity().add(shootVelocity),chosen.shot().ticks()));
        ShotConfidence.Status status=ShotConfidence.classify(chosen.collision(),consensus,obstruction);
        // Steering requires a deliberate lock and a clear, checked projectile route.
        // This deliberately does not aim at neutral mobs through unchecked terrain.
        boolean clearRoute=obstruction.state()==BallisticOcclusion.State.CLEAR;
        // Works in CENTER and RADAR; an explicit manual lock is optional.
        // Never steer toward other players or neutral mobs.
        boolean viewSteered=false;
        // Aim guidance needs an unobstructed geometric solution, NOT unanimous
        // three-model support. Otherwise it may never move for multiplayer mobs.
        // READY and firing stay strict; this relaxes only steering eligibility.
        if(AssistEligibilityV43.maySteer(guideOptions.autoAim(),
                (chosen.entity() instanceof Enemy || (chosen.entity() instanceof Player && advancedAim.allowPlayerAutomation())),chosen.collision().geometricContact(),
                clearRoute,guideOptions.lineOfSightOnly(),mc.gui.screen()==null)){
            AimAssistController.Step move=AimAssistController.step(shooter.getYRot(),shooter.getXRot(),
                    chosen.shot().yaw(),chosen.shot().pitch(),guideOptions.aimRateTenths()/10.0,true);
            if(move.moved()){
                // Small continuous lead corrections should not hold FIRE forever.
                // Final rotation is explicitly transmitted before release.
                viewSteered=Math.hypot(CoreBallistics.wrap(move.yaw()-shooter.getYRot()),
                        move.pitch()-shooter.getXRot())>0.35;
                shooter.setYRot((float)move.yaw());
                shooter.setXRot((float)move.pitch());
            }
        }
        AIM_SYNC.observe(selected,tick,viewSteered);
        // A moving, distant or small target gets a narrower READY cone.
        AimReadinessCriteria.Bounds limits=AimReadinessCriteria.bounds(
                shooter.distanceTo(chosen.entity()),chosen.half(),chosen.velocity().length());
        readyState=READY.update(selected,tick,
                CoreBallistics.wrap(chosen.shot().yaw()-shooter.getYRot()),
                chosen.shot().pitch()-shooter.getXRot(),status,power>=0.99,
                limits.yawDegrees(),limits.pitchDegrees());
        if(readyState.pulse() && guideOptions.cueMode()>=1){
            readyFlashUntil=tick+4;
            if(guideOptions.cueMode()==2){
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(),1.7f,0.22f));
            }
        }
        // Continuously held ATTACK+USE is an opt-in request, not a
        // forced shot: every draw cycle must reach full power AND READY again.
        // Vanilla should restart using the bow while USE stays held; this
        // behavior still requires a real 26.3 in-game test.
        if(RELEASE_GATE.allow(tick,guideOptions.assistedFire(),
                mc.gui.screen()==null && mc.options.keyAttack.isDown() && mc.options.keyUse.isDown(),
                bow,shooter.getTicksUsingItem()>=20,(chosen.entity() instanceof Enemy || (chosen.entity() instanceof Player && advancedAim.allowPlayerAutomation())),
                clearRoute && status==ShotConfidence.Status.CONSISTENT_ESTIMATE
                        && guideOptions.lineOfSightOnly() && AIM_SYNC.ready(selected,tick),
                readyState.ready() && shooter.getTicksUsingItem()>=23) && mc.gameMode!=null){
            // Packet order matters on dedicated servers: RELEASE_USE_ITEM does not
            // contain the camera direction. Use an ordinary vanilla rotation update
            // before the ordinary release action, never invent projectile hits.
            if(mc.getConnection()!=null){
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        shooter.getYRot(),shooter.getXRot(),shooter.onGround(),shooter.horizontalCollision));
            }
            mc.gameMode.releaseUsingItem(shooter);
        }
        preview=new Preview(chosen.entity().getName().getString(),
                chosen.entity() instanceof Player?"プレイヤー":chosen.entity() instanceof Enemy?"敵対モブ":"モブ",
                shooter.distanceTo(chosen.entity()),eye,shootVelocity,chosen.shot(),chosen.collision(),true,chosen.half(),obstruction,consensus);
    }
    /** Cheap all-mode entity ranking: avoid repeated 80-tick trajectory simulations. */
    private static double preliminaryScore(LocalPlayer shooter,LivingEntity entity){
        double distance=shooter.distanceTo(entity);
        if(guideOptions.searchMode()==1)
            return TargetSelectionV43.radarScore(currentReticleMiss(shooter,entity),distance);
        Vec3 eye=shooter.getEyePosition(),center=entity.getBoundingBox().getCenter();
        // Rank with the CURRENT visual centre first. A strong predicted lead
        // must not move a mob out of the candidate budget when it is under the reticle.
        double directYaw=currentReticleMiss(shooter,entity);
        double directPitch=0;
        StagedPrecomputeCache.Motion motion=STAGES.motion(entity.getUUID());
        if(motion!=null){
            CoreBallistics.Vec five=motion.at5();
            double dx=five.x()-eye.x,dy=five.y()-eye.y,dz=five.z()-eye.z;
            double yaw=CoreBallistics.wrap(Math.toDegrees(Math.atan2(-dx,dz))-shooter.getYRot());
            double pitch=-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)))-shooter.getXRot();
            return TargetSelectionV43.score(directYaw,directPitch,yaw,pitch,distance);
        }
        return TargetSelectionV43.score(directYaw,directPitch,directYaw,directPitch,distance);
    }
    /** Angular error to the visible hitbox surface, not only to its centre. */
    private static double currentReticleMiss(LocalPlayer shooter,LivingEntity entity){
        AABB bb=entity.getBoundingBox();
        double distance=Math.max(.01,shooter.distanceTo(entity));
        double horizontal=Math.toDegrees(Math.atan2(Math.max(bb.getXsize(),bb.getZsize())*.5,distance));
        double vertical=Math.toDegrees(Math.atan2(bb.getYsize()*.5,distance));
        return Math.hypot(Math.max(0,Math.abs(currentBearingYaw(shooter,entity))-horizontal),
                Math.max(0,Math.abs(currentBearingPitch(shooter,entity))-vertical));
    }
    private static double currentBearingYaw(LocalPlayer shooter,LivingEntity entity){
        Vec3 d=entity.getBoundingBox().getCenter().subtract(shooter.getEyePosition());
        return CoreBallistics.wrap(Math.toDegrees(Math.atan2(-d.x,d.z))-shooter.getYRot());
    }
    private static double currentBearingPitch(LocalPlayer shooter,LivingEntity entity){
        Vec3 d=entity.getBoundingBox().getCenter().subtract(shooter.getEyePosition());
        return -Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))-shooter.getXRot();
    }
    /**
     * Progressive depth on ALL tracked entities, independent of CENTER/RADAR and
     * bow charge. LOW tracks; DEFAULT adds 5/10/20t motion lookahead for all;
     * HIGH incrementally solves full-power bow bearings for all 64;
     * ALL additionally runs provisional moving-AABB/contact, block, and model audits.
     * These are NEVER used as authority for READY or assisted release.
     */
    private static void advancePrecompute(Minecraft mc,LocalPlayer shooter,
            List<LivingEntity> targets,Map<UUID,CoreBallistics.Vec> speeds,PrecomputePolicy.Plan plan){
        if(!plan.precomputesBallistics())return;
        CoreBallistics.Vec eye=vec(shooter.getEyePosition());
        CoreBallistics.Vec shooterVelocity=vec(shooter.getDeltaMovement());
        // Every tracked entity obtains a turn over successive ticks, not just
        // the nearest N entities. The per-tick cap bounds frame-time spikes.
        for(LivingEntity entity:PRECOMPUTE_ORDER.next(targets,plan.heavyUpdatesPerTick(),LivingEntity::getUUID)){
            UUID id=entity.getUUID();
            CoreBallistics.Vec velocity=speeds.get(id);
            if(velocity==null)continue;
            if(guideOptions.lineOfSightOnly()&&!canSeeBlockFree(mc,shooter,entity))continue;
            CoreBallistics.Vec center=TargetVelocityFilter.lead(
                    vec(entity.getBoundingBox().getCenter()),velocity,advancedAim.latencyTicks());
            CoreBallistics.Solution shot=WARM.warm(id,tick,eye,center,velocity,
                    shooterVelocity,plan.cacheTtlTicks());
            if(shot==null)continue;
            STAGES.ballistic(id,tick);
            if(!plan.precomputesAudits())continue;
            AABB box=entity.getBoundingBox();
            CoreBallistics.Vec half=new CoreBallistics.Vec(
                    Math.max(.01,box.getXsize()*.5),Math.max(.01,box.getYsize()*.5),
                    Math.max(.01,box.getZsize()*.5));
            ContactAudit.Result contact=ContactAudit.evaluate(eye,shot,center,velocity,
                    half,shooterVelocity,CoreBallistics.FULL_BOW_SPEED,.30);
            BallisticOcclusion.Result blocks=options.blockCheck()&&contact.geometricContact()
                    ? BallisticOcclusion.evaluate(eye,shot.launchVelocity().add(shooterVelocity),
                            contact.contactTick(),ClientBlockOcclusion.probe(mc.level,shooter))
                    : new BallisticOcclusion.Result(BallisticOcclusion.State.UNCHECKED,0,0,Double.NaN);
            TrajectoryConsensus.Result consensus=TrajectoryConsensus.evaluate(
                    eye,shot.launchVelocity().add(shooterVelocity),center,velocity,half,.12,
                    Math.min(CoreBallistics.MAX_TICKS,shot.ticks()+3));
            STAGES.audited(id,tick,new StagedPrecomputeCache.Audit(contact,blocks,consensus));
        }
    }
    /** Radar mode can select and cycle targets before the player draws a bow.
     * Does not send packets, alter aim, or predict a shot while the bow is idle.
     * Caps scans to keep the per-tick cost bounded on dense mob farms.
     */
    private static void acquireRadarWithoutBow(Minecraft mc,LocalPlayer shooter,
                                                List<LivingEntity> targets,int count){
        // Both BORESIGHT and RADAR benefit from immediate selection at idle.
        // BORESIGHT uses the same screen-centre rank as the drawn-bow branch.
        List<LivingEntity> available=new ArrayList<>();
        for(int i=0;i<count;i++){
            LivingEntity e=targets.get(i);
            if(shooter.distanceTo(e)>guideOptions.depthBlocks())continue;
            if(guideOptions.lineOfSightOnly()&&!canSeeBlockFree(mc,shooter,e))continue;
            available.add(e);
        }
        radarCount=available.size();
        if(guideOptions.searchMode()==0)
            available.removeIf(e->currentReticleMiss(shooter,e)>guideOptions.radiusDegrees());
        // Even the 360-degree RADAR captures the visible target under the reticle.
        available.sort(Comparator.comparingDouble(e->preliminaryScore(shooter,e)));
        if(available.isEmpty()){
            if(!locked)selected=null;
            radarMark=null;preview=Preview.empty();return;
        }
        LivingEntity choice=null;
        if(locked && selected!=null && !nextLockRequested){
            for(LivingEntity e:available)if(e.getUUID().equals(selected)){choice=e;break;}
            if(choice==null){radarMark=null;preview=Preview.empty();return;} // preserve temporarily hidden lock
        }else if(nextLockRequested){
            int index=-1;
            for(int i=0;i<available.size();i++)
                if(available.get(i).getUUID().equals(selected)){index=i;break;}
            choice=available.get((index+1)%available.size());
            locked=true;nextLockRequested=false;
        }else if(!locked || selected==null){
            choice=available.get(0);
        }
        if(choice==null){radarMark=null;preview=Preview.empty();return;}
        selected=choice.getUUID();
        radarMark=new RadarMark(selected,vec(choice.getBoundingBox().getCenter()),
                choice.getName().getString(),locked);
        preview=new Preview(choice.getName().getString(),
                choice instanceof Player?"プレイヤー":choice instanceof Enemy?"敵対モブ":"モブ",
                shooter.distanceTo(choice),null,null,null,null,false,null,null,null);
    }
    /** Conservative client-only raycast: unloaded chunks are NOT transparent. */
    private static boolean canSeeBlockFree(Minecraft mc,LocalPlayer shooter,LivingEntity target){
        Vec3 from=shooter.getEyePosition();
        Vec3 center=target.getBoundingBox().getCenter();
        if(!mc.level.hasChunkAt(BlockPos.containing(from))||
           !mc.level.hasChunkAt(BlockPos.containing(center))||
           !mc.level.hasChunkAt(BlockPos.containing(from.lerp(center,.5))))return false;
        HitResult hit=mc.level.clip(new ClipContext(from,center,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shooter));
        return hit.getType()!=HitResult.Type.BLOCK;
    }
    private static CoreBallistics.Vec estimate(CoreBallistics.Vec now,Tracked prev,long at){
        if(prev==null||at<=prev.tick()||at-prev.tick()>5)return new CoreBallistics.Vec(0,0,0);
        CoreBallistics.Vec raw=now.minus(prev.center()).times(1.0/(at-prev.tick()));
        return TargetVelocityFilter.smooth(prev.filteredVelocity(),raw);
    }
    private static boolean eligible(LivingEntity entity){
        return switch(options.category()){
            case 0->entity instanceof Enemy;
            case 1->entity instanceof Player;
            default->true;
        };
    }
    private static CoreBallistics.Vec vec(Vec3 v){return new CoreBallistics.Vec(v.x,v.y,v.z);}
    private static void drawHud(GuiGraphicsExtractor gfx,float partial){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.gui.screen()!=null)return;
        int w=gfx.guiWidth(),h=gfx.guiHeight();if(w<100||h<80)return;
        if(guideOptions.centerDot()){
            int cx=w/2,cy=h/2;
            gfx.fill(cx-1,cy-1,cx+2,cy+2,guideOptions.tint(0xFFFFFFFF));
        }
        if(options.guide()){
            if(overlayMode!=ScanOverlayPreference.OFF)drawScanContacts(gfx,mc,w,h,partial);
            if(guideOptions.searchMode()==1||!options.linked()||options.hud()){
                drawGuides(gfx,mc,w,h);
                // Selected target remains highlighted when idle in either mode.
                drawRadarMarker(gfx,mc,w,h);
            }
        }
        if(!options.hud())return;
        if(guideOptions.hudStyle()==1 && w>=320 && h>=170){
            drawFlightHud(gfx,mc,w,h);
            return;
        }
        int effectiveMode = h < 145 ? 0 : h < 205 ? Math.min(1, options.mode()) : options.mode();
        int bottom=effectiveMode==0?48:effectiveMode==1?132:209;
        boolean showAssist=guideOptions.autoAim()||guideOptions.assistedFire();
        gfx.fill(4,4,Math.min(w-4,(int)(302*guideOptions.fontScale())),
                Math.min(h-4,bottom+(showAssist?16:0)),0xB0101B28);
        line(gfx,mc,"LeadShot 26.3 / 照準支援",8,8,0xFFD7EEFF);
        line(gfx,mc,"対象: "+categoryName()+" | "+(locked?"手動固定 ":"自動選択 ")
                +(guideOptions.searchMode()==1?"広域索敵 ":"")+trim(preview.target(),17),8,22,0xFFFFFFFF);
        if(guideOptions.searchMode()==1 && preview.solution()==null)
            line(gfx,mc,"広域索敵の候補: "+radarCount+" | "+(selected==null?"未選択":"選択済み"),8,50,0xFF89D9F6);
        if(showAssist){
            line(gfx,mc,"補助:"+(guideOptions.autoAim()?" 自動照準 入":" 自動照準 切")
                    +(guideOptions.assistedFire()?" 連射 入":""),8,Math.min(h-15,bottom),0xFF85CCEA);
        }
        if(effectiveMode==0)return;
        if(preview.solution()==null){
            line(gfx,mc,preview.drawing()?"最大チャージ待機 / 照準候補なし":
                    (guideOptions.searchMode()==1 && selected!=null?"索敵表示中: 最大チャージで予測":"最大チャージで予測"),
                    8,36,0xFFFFC97A);
        }
        else{
            CoreBallistics.Solution shot=preview.solution();
            line(gfx,mc,String.format(Locale.ROOT,"%s | %.1fm | %.0f tick",preview.category(),preview.range(),(double)shot.ticks()),8,36,0xFFBBD9F7);
            line(gfx,mc,String.format(Locale.ROOT,"照準: Yaw %+.1f° / Pitch %+.1f°",CoreBallistics.wrap(shot.yaw()-mc.player.getYRot()),shot.pitch()-mc.player.getXRot()),8,50,0xFFFFD58F);
            line(gfx,mc,String.format(Locale.ROOT,"速度誤差: %.3f | 幾何: %s",shot.speedMismatch(),preview.collision().geometricContact()?"可能":"未成立"),8,64,0xFFFFD8A0);
            BallisticOcclusion.Result block = preview.obstruction();
            String blocker = block == null ? "未判定" : switch(block.state()) {
                case BLOCKED -> "遮蔽あり";
                case CLEAR -> "検査範囲でブロックなし";
                case UNCHECKED -> "未検査・検査上限";
                case INVALID -> "判定不能";
            };
            int blockColor = block != null && block.state() == BallisticOcclusion.State.BLOCKED
                    ? 0xFFFF6666 : block != null && block.state() == BallisticOcclusion.State.CLEAR
                    ? 0xFF94D5BB : 0xFFFFC777;
            line(gfx,mc,"ブロック判定: " + blocker,8,78,blockColor);
            var confidence=ShotConfidence.classify(preview.collision(),preview.consensus(),preview.obstruction());
            int votes=preview.consensus()==null?0:preview.consensus().supportingModels();
            line(gfx,mc,"3モデル支持: "+votes+"/3 | "+ShotConfidence.japanese(confidence),8,92,
                    ShotConfidence.color(confidence));
            if(readyState.ready() && guideOptions.cueMode()>0)
                line(gfx,mc,"射撃可能（推定）・命中保証なし",8,110,0xFF70DEA6);
            if(!readyState.ready() || guideOptions.cueMode()==0)
                line(gfx,mc,"クライアント推定・命中確定ではありません",8,106,0xFFFFA6AA);
        }
        if(effectiveMode==2){
            line(gfx,mc,"事前計算: "+PrecomputePolicy.name(guideOptions.computationMode())
                    +" | 段階 "+STAGES.countAtStage(guideOptions.computationMode(),tick,
                        depthHudTtl())+"/"+STAGES.size()+" | 成功 "+WARM.hits()+" 失敗 "+WARM.misses(),
                    8,193,0xFFA8E3FF);
            line(gfx,mc,options.shadowAudit()?"比較監査: 入 (ログ匿名)":"比較監査: 切",8,120,0xFFA9BACE);
            if(options.shadowAudit()){
                MotionShadowAudit.RecentComparison c=SHADOW.recentComparison(10);
                line(gfx,mc,c.samples()>0?String.format(Locale.ROOT,"10tick 件数=%d 生=%.2f 補正=%.2f",c.samples(),c.rawMae(),c.experimentalMae()):"10t: ペア観測なし",8,134,0xFFC3D5E2);
                ShotReleaseAudit.Summary release=RELEASE_AUDIT.summary();
                line(gfx,mc,release.recentPairs()>0
                        ? String.format(Locale.ROOT,"使用終了の誤差 n=%d 平均%.2f P90%.2f",release.recentPairs(),release.recentMean(),release.recentP90())
                        : "使用終了の誤差: 観測なし (命中未判定)",8,148,0xFFC3D5E2);
                var arrows = ARROW_AUDIT.summary();
                line(gfx,mc,String.format(Locale.ROOT,"矢を実際に観測: %d / 候補: %d (命中未判定)",
                        arrows.observedLaunches(),arrows.useEnds()),8,162,0xFFB5E1BB);
                line(gfx,mc,arrows.trajectoryPairs()>0
                        ? String.format(Locale.ROOT,"矢の軌道差 n=%d 平均%.2f P90%.2f",
                                arrows.trajectoryPairs(),arrows.trajectoryMeanError(),arrows.recentP90())
                        : "矢の軌道差: 観測ペアなし",8,176,0xFFC3D5E2);
            }
        }
    }
    /** Read-only flight-symbology presentation: telemetry already computed on client ticks. */
    private static void drawFlightHud(GuiGraphicsExtractor gfx,Minecraft mc,int w,int h){
        int mode=FlightHudLayout.effectiveMode(options.mode(),h,guideOptions.fontScale(),options.shadowAudit());
        FlightHudLayout.Layout layout=FlightHudLayout.calculate(w,h,mode,guideOptions.fontScale());
        FlightHudLayout.Frame panel=layout.telemetry();
        boolean blocked=preview.obstruction()!=null
                &&preview.obstruction().state()==BallisticOcclusion.State.BLOCKED;
        FlightHudTheme.State state=FlightHudTheme.state(readyState.ready(),blocked,
                preview.solution()!=null,selected!=null,preview.drawing());
        int stateColor=FlightHudTheme.color(state);
        int row=Math.max(11,(int)Math.ceil(13*guideOptions.fontScale()));
        int left=panel.x()+9, y=panel.y()+7;
        flightPanel(gfx,panel,FlightHudTheme.CYAN);
        hudPanelLine(gfx,mc,"LeadShot / 火器管制",left,y,FlightHudTheme.CYAN,panel);
        y+=row;
        hudPanelLine(gfx,mc,"追跡 "+FlightHudTheme.lock(locked)+" / "+FlightHudTheme.radar(guideOptions.searchMode()),
                left,y,FlightHudTheme.WHITE,panel);
        y+=row;
        hudPanelLine(gfx,mc,"対象 "+trim(preview.target(),17),left,y,FlightHudTheme.WHITE,panel);
        if(mode>=1 && preview.solution()!=null){
            CoreBallistics.Solution shot=preview.solution();
            y+=row;
            hudPanelLine(gfx,mc,String.format(Locale.ROOT,"距離 %.1fm  /  到達 %.0ft",preview.range(),(double)shot.ticks()),
                    left,y,FlightHudTheme.WHITE,panel);
            y+=row;
            hudPanelLine(gfx,mc,String.format(Locale.ROOT,"偏差  水平%+.1f  垂直%+.1f",CoreBallistics.wrap(shot.yaw()-mc.player.getYRot()),
                    shot.pitch()-mc.player.getXRot()),left,y,FlightHudTheme.CYAN,panel);
            y+=row;
            int votes=preview.consensus()==null?0:preview.consensus().supportingModels();
            hudPanelLine(gfx,mc,"物理モデル "+votes+"/3   /  "+(blocked?"遮蔽あり":"射線検査"),
                    left,y,blocked?FlightHudTheme.RED:FlightHudTheme.AMBER,panel);
        }else if(mode>=1){
            y+=row;
            hudPanelLine(gfx,mc,"最大チャージで弾道予測",left,y,FlightHudTheme.AMBER,panel);
        }
        if(mode>=2){
            y+=row;
            hudPanelLine(gfx,mc,"事前計算 " + FlightHudTheme.computation(guideOptions.computationMode()),left,y,FlightHudTheme.MUTED,panel);
            y+=row;
            hudPanelLine(gfx,mc,"処理完了 "+STAGES.countAtStage(guideOptions.computationMode(),tick,depthHudTtl())+
                    "/"+STAGES.size()+" / 使用 "+WARM.hits()+" 再計算 "+WARM.misses(),
                    left,y,FlightHudTheme.MUTED,panel);
            if(options.shadowAudit()){
                y+=row;
                var arrow=ARROW_AUDIT.summary();
                hudPanelLine(gfx,mc,"矢の観測 "+arrow.observedLaunches()+" / "+arrow.useEnds(),left,y,FlightHudTheme.MUTED,panel);
            }
        }
        FlightHudLayout.Frame sys=layout.systems();
        if(sys!=null){
            flightPanel(gfx,sys,FlightHudTheme.CYAN);
            int sx=sys.x()+8,sy=sys.y()+7;
            hudPanelLine(gfx,mc,"照準・発射補助",sx,sy,FlightHudTheme.CYAN,sys);
            sy+=row;
            hudPanelLine(gfx,mc,"自動照準: "+(guideOptions.autoAim()?"入":"切"),sx,sy,
                    guideOptions.autoAim()?FlightHudTheme.GREEN:FlightHudTheme.MUTED,sys);
            sy+=row;
            hudPanelLine(gfx,mc,"自動発射: "+(guideOptions.assistedFire()?"入":"切"),sx,sy,
                    guideOptions.assistedFire()?FlightHudTheme.AMBER:FlightHudTheme.MUTED,sys);
            sy+=row;
            hudPanelLine(gfx,mc,"索敵: "+radarCount+" 体",sx,sy,FlightHudTheme.WHITE,sys);
        }
        // A compact cockpit cue below the sight line; center of screen remains uncluttered.
        FlightHudLayout.Frame cue=layout.cue();
        if(readyState.ready()||preview.solution()!=null||preview.drawing()||selected!=null){
            gfx.fill(cue.x(),cue.y(),cue.x()+cue.width(),cue.y()+cue.height(),0xB5091620);
            gfx.fill(cue.x(),cue.y(),cue.x()+3,cue.y()+cue.height(),stateColor);
            String stateText=FlightHudTheme.cue(state);
            String modeText=readyState.ready()?" / 命中未保証":" / "+(locked?"固定":"索敵");
            hudPanelLine(gfx,mc,stateText+modeText,cue.x()+8,cue.y()+4,stateColor,cue);
        }
        // Charge stripe denotes bow draw duration only; NOT a computed hit probability.
        if(mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof BowItem){
            double p=Math.max(0,Math.min(1,CoreBallistics.vanillaBowPower(mc.player.getTicksUsingItem())));
            int barX=panel.x()+4,barY=panel.y()+panel.height()-5,barW=panel.width()-8;
            gfx.fill(barX,barY,barX+barW,barY+2,0xAA284D55);
            gfx.fill(barX,barY,barX+(int)Math.round(barW*p),barY+2,p>=.99?FlightHudTheme.GREEN:FlightHudTheme.CYAN);
        }
    }
    /** Keep aircraft panel labels inside their assigned frame at 50–200% text sizes. */
    private static void hudPanelLine(GuiGraphicsExtractor gfx,Minecraft mc,String text,int x,int y,int color,
                                     FlightHudLayout.Frame frame){
        if(frame==null || y+(int)Math.ceil(10*guideOptions.fontScale())>frame.y()+frame.height()-3)return;
        text=FlightHudLayout.fitText(text,frame.x()+frame.width()-x-5,guideOptions.fontScale());
        if(!text.isEmpty())line(gfx,mc,text,x,y,color);
    }
    private static void flightPanel(GuiGraphicsExtractor gfx,FlightHudLayout.Frame p,int accent){
        int x=p.x(),y=p.y(),w=p.width(),h=p.height();
        gfx.fill(x,y,x+w,y+h,FlightHudTheme.PANEL);
        gfx.fill(x,y,x+w,y+1,FlightHudTheme.BORDER);
        gfx.fill(x,y+1,x+1,y+h,FlightHudTheme.BORDER);
        gfx.fill(x+w-1,y+1,x+w,y+h,FlightHudTheme.BORDER);
        gfx.fill(x,y+h-1,x+w,y+h,FlightHudTheme.BORDER);
        gfx.fill(x,y,x+30,y+2,accent);
        gfx.fill(x,y,x+2,y+10,accent);
        gfx.fill(x+w-30,y+h-2,x+w,y+h,accent);
    }
    private static void drawFlightReticle(GuiGraphicsExtractor gfx,int x,int y,int color){
        // Four corner-coded 'diamond' strokes: crisp at any UI scale, no textures.
        drawApproxLine(gfx,x,y-11,x+6,y-5,color);
        drawApproxLine(gfx,x+6,y-5,x+11,y,color);
        drawApproxLine(gfx,x+11,y,x+6,y+5,color);
        drawApproxLine(gfx,x+6,y+5,x,y+11,color);
        drawApproxLine(gfx,x,y+11,x-6,y+5,color);
        drawApproxLine(gfx,x-6,y+5,x-11,y,color);
        drawApproxLine(gfx,x-11,y,x-6,y-5,color);
        drawApproxLine(gfx,x-6,y-5,x,y-11,color);
        gfx.fill(x-1,y-1,x+2,y+2,color);
    }
    /** Tiny projected contact corners, not a wall-penetrating world-space box. */
    private static void drawScanContacts(GuiGraphicsExtractor gfx,Minecraft mc,int w,int h,float partial){
        if(scanContacts.isEmpty())return;
        for(ScanContact c:scanContacts){
            // projectPointToScreen reads the actual renderer camera (including
            // third-person offset, interpolated view and dynamic FOV). Project all
            // eight corners: a body moving to the side cannot use a fixed width
            // derived from its apparent height.
            ScanBoxGeometry.Bounds box=ScanBoxGeometry.project(
                    ScanBoxGeometry.interpolate(c.previous(),c.center(),partial),c.half(),w,h,
                    p->WorldHudProjection.project(mc,p,w,h));
            if(box==null)continue;
            boolean active=c.id().equals(selected);
            int rgb=active?guideOptions.tint(0xFF73F8C2):0x7249CFEA;
            int t=active?Math.max(1,guideOptions.thickness()):1;
            int l=box.left(),r=box.right(),u=box.top(),d=box.bottom();
            int span=Math.max(3,Math.min(8,Math.min((d-u)/2,(r-l)/2)));
            gfx.fill(l,u,l+span,u+t,rgb);gfx.fill(l,u,l+t,u+span,rgb);
            gfx.fill(r-span,u,r,u+t,rgb);gfx.fill(r-t,u,r,u+span,rgb);
            gfx.fill(l,d-t,l+span,d,rgb);gfx.fill(l,d-span,l+t,d,rgb);
            gfx.fill(r-span,d-t,r,d,rgb);gfx.fill(r-t,d-span,r,d,rgb);
        }
    }
    private static void drawRadarMarker(GuiGraphicsExtractor gfx,Minecraft mc,int w,int h){
        RadarMark mark=radarMark;
        if(mark==null || mc.player==null)return;
        AimProjection.Point p=WorldHudProjection.project(mc,mark.center(),w,h);
        if(p==null)return;
        int color=guideOptions.tint(mark.locked()?0xFF80F7C7:0xFF74C9FF);
        if(!p.inFront()){
            // Behind camera: no misleading front-screen marker.
            line(gfx,mc,mark.locked()?"固定対象: 後方":"追跡対象: 後方",w-142,16,color);
            return;
        }
        int x=(int)Math.max(12,Math.min(w-12,Math.round(p.x())));
        int y=(int)Math.max(16,Math.min(h-16,Math.round(p.y())));
        int t=guideOptions.thickness();
        // Corner bracket distinct from ballistic AIM/PRED.
        for(int side=-1;side<=1;side+=2){
            int px=x+side*13,py=y+side*13;
            gfx.fill(px-5,py,px+6,py+t,color);
            gfx.fill(px,py-5,px+t,py+6,color);
        }
        if(guideOptions.labels()){
            String label=(guideOptions.hudStyle()==1?(mark.locked()?"固定 ":"追跡 "):(mark.locked()?"固定 ":"索敵 "))+trim(mark.name(),14);
            line(gfx,mc,label,Math.max(2,Math.min(w-112,x+16)),Math.max(4,y-13),color);
        }
    }
    private static void drawGuides(GuiGraphicsExtractor gfx,Minecraft mc,int w,int h){
        if(preview.solution()!=null){
            var s=preview.solution();
            GuideFrameSmoother.Frame guide=SMOOTHER.sample(System.nanoTime(),guideOptions.smoothing());
            if(guide==null)return;
            AimProjection.Point aim=AimProjection.angle(guide.yaw(),guide.pitch(),mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov());
            if(aim!=null&&aim.inFront()){
                int x=(int)Math.round(aim.x()),y=(int)Math.round(aim.y());
                // Blocked cannot be painted green; unchecked stays amber.
                var confidence=ShotConfidence.classify(preview.collision(),preview.consensus(),preview.obstruction());
                int guideColor=guideOptions.tint(aim.visible()?ShotConfidence.color(confidence):0xFFFFCB72);
                // While the strict readiness gate holds, the guide stays bright.
                // The short sound still plays only on the false->true edge.
                if(readyState.ready() && guideOptions.cueMode()>0)
                    guideColor=guideOptions.tint(0xFF70FFB0);
                if(guideOptions.hudStyle()==1)drawFlightReticle(gfx,x,y,guideColor);
                else drawCross(gfx,x,y,guideColor);
                if(aim.visible()&&guideOptions.labels())
                    line(gfx,mc,readyState.ready()&&guideOptions.cueMode()>0?"射撃可能": "偏差照準",x+13,y-4,guideColor);
                // World-space rings are projected to the HUD. This is NOT depth-tested.
                for(var edge:ProjectedAimRings.aimRing(guide.eye(),guide.yaw(),guide.pitch(),
                        mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov(),10)) {
                    drawApproxLine(gfx,(int)edge.from().x(),(int)edge.from().y(),
                            (int)edge.to().x(),(int)edge.to().y(),guideColor);
                }
            }
            AimProjection.Point future=AimProjection.world(guide.eye(),guide.future(),mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov());
            if(future!=null&&future.inFront()){
                int x=(int)Math.round(future.x()),y=(int)Math.round(future.y());
                gfx.fill(x-5,y-5,x+6,y-4,guideOptions.tint(0xFF62D9FC));gfx.fill(x-5,y+5,x+6,y+6,guideOptions.tint(0xFF62D9FC));
                gfx.fill(x-5,y-4,x-4,y+5,guideOptions.tint(0xFF62D9FC));gfx.fill(x+5,y-4,x+6,y+5,guideOptions.tint(0xFF62D9FC));
                if(future.visible()&&guideOptions.labels())line(gfx,mc,"予測位置",x+10,y-4,guideOptions.tint(0xFF62D9FC));
                if(preview.half()!=null){
                    double ringRadius=Math.max(.17,Math.min(1.0,
                            Math.max(preview.half().x(),preview.half().y())));
                    for(var edge:ProjectedAimRings.targetRing(guide.eye(),guide.future(),
                            mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov(),ringRadius)){
                        drawApproxLine(gfx,(int)edge.from().x(),(int)edge.from().y(),
                                (int)edge.to().x(),(int)edge.to().y(),guideOptions.tint(0xFF62D9FC));
                    }
                }
            }
            // Future target envelope. Draw only fully visible, front-facing edges.
            if(preview.half()!=null&&preview.half().finite()){
                for(var segment:ProjectedBox.edges(guide.eye(),guide.future(),preview.half(),
                        mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov())){
                    drawApproxLine(gfx,(int)segment.from().x(),(int)segment.from().y(),
                            (int)segment.to().x(),(int)segment.to().y(),guideOptions.tint(0xFF67D9FA));
                }
            }
            if(options.trajectory()){
                for(var point:FlightGuidePoints.sample(guide.eye(),guide.velocity(),guide.ticks())){
                    AimProjection.Point p=AimProjection.world(guide.eye(),point,mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov());
                    if(p!=null&&p.visible())gfx.fill((int)p.x()-1,(int)p.y()-1,(int)p.x()+2,(int)p.y()+2,0xFFFFC37B);
                }
            }
        }else if(options.calibration()&&!preview.drawing()){
            drawCross(gfx,w/2,h/2,0xFF62D9FC);
            line(gfx,mc,"表示テスト",Math.min(w-62,w/2+12),h/2-4,0xFF62D9FC);
        }
    }
    /** Clip to GUI bounds first; horizontal/vertical strokes collapse to a single quad. */
    private static void drawApproxLine(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1,int rgb){
        ScreenLineClipper.Segment seg=ScreenLineClipper.clip(x0,y0,x1,y1,g.guiWidth(),g.guiHeight());
        if(seg==null)return;
        x0=seg.x0();y0=seg.y0();x1=seg.x1();y1=seg.y1();
        int t=guideOptions.thickness();
        if(y0==y1){g.fill(Math.min(x0,x1),y0,Math.max(x0,x1)+t,y0+t,rgb);return;}
        if(x0==x1){g.fill(x0,Math.min(y0,y1),x0+t,Math.max(y0,y1)+t,rgb);return;}
        int segments=Math.min(48,Math.max(1,Math.max(Math.abs(x1-x0),Math.abs(y1-y0))));
        for(int i=0;i<=segments;i++){
            int x=(int)Math.round(x0+(x1-x0)*i/(double)segments);
            int y=(int)Math.round(y0+(y1-y0)*i/(double)segments);
            g.fill(x,y,x+t,y+t,rgb);
        }
    }
    private static void drawCross(GuiGraphicsExtractor g,int x,int y,int color){
        int t=guideOptions.thickness();
        g.fill(x-9,y,x-3,y+t,color);g.fill(x+4,y,x+10,y+t,color);
        g.fill(x,y-9,x+t,y-3,color);g.fill(x,y+4,x+t,y+10,color);
    }
    private static void line(GuiGraphicsExtractor gfx,Minecraft mc,String text,int x,int y,int color){
        double scale=guideOptions.fontScale();
        // Bound projected labels and large-font telemetry to the GUI viewport.
        text=FlightHudLayout.fitText(text,gfx.guiWidth()-Math.max(0,x)-3,scale);
        if(text.isEmpty())return;
        if(scale==1.0){gfx.text(mc.font,text,x,y,color,true);return;}
        gfx.pose().pushMatrix();
        try{
            gfx.pose().translate(x,y);
            gfx.pose().scale((float)scale,(float)scale);
            gfx.text(mc.font,text,0,0,color,true);
        }finally{gfx.pose().popMatrix();}
    }
    private static String trim(String s,int max){return s.length()<=max?s:s.substring(0,max-1)+"…";}
    private static String categoryName(){return switch(options.category()){case 0->"敵対モブ";case 1->"プレイヤー";default->"すべて";};}
    /** Not drawn yet; future 26.3 level renderer may consume this immutable geometry only. */
    public static WorldGuideGeometry.Frame lastWorldGuideFrame(){return worldGuide;}
    public static LeadShotOptions options(){return options;}
    public static GuideControls guideControls(){return guideOptions;}
    public static String lockStatus(){return locked?"手動ロック":"自動選択";}
    public static String scanOverlayStatus(){return ScanOverlayPreference.label(overlayMode);}
    public static void toggleScanOverlay(){
        overlayMode=ScanOverlayPreference.next(overlayMode);
        try{ScanOverlayPreference.write(OVERLAY_CONFIG,overlayMode);}
        catch(Exception ex){System.err.println("[LeadShot] scan overlay save: "+ex);}
    }
    /** Legacy one-click actions, now immutable and guaranteed to preserve unrelated options. */
    public static void changeGuide(String action){
        GuideControls g=guideOptions;
        guideOptions=switch(action){
            case "radius"->g.withNumeric(FineTuneParameter.SCAN_CONE,g.radiusDegrees()+5);
            case "depth"->g.withNumeric(FineTuneParameter.SENSOR_RANGE,g.depthBlocks()+16);
            case "thickness"->g.withNumeric(FineTuneParameter.RETICLE_WEIGHT,g.thickness()+1);
            case "opacity"->g.withNumeric(FineTuneParameter.HUD_ALPHA,g.opacity()+25);
            case "color"->new GuideControls(g.radiusDegrees(),g.depthBlocks(),g.thickness(),g.opacity(),
                    (g.color()+1)%4,g.centerDot(),g.labels(),g.lineOfSightOnly(),g.manualLock(),
                    g.smoothing(),g.textSize(),g.cueMode(),g.searchMode(),g.autoAim(),g.assistedFire(),
                    g.assistSpeed(),g.computationMode(),g.hudStyle(),g.fontPercent(),g.aimRateTenths());
            case "dot"->guideToggle(g,"dot");
            case "labels"->guideToggle(g,"labels");
            case "los"->guideToggle(g,"los");
            case "smoothing"->guideToggle(g,"smoothing");
            case "textsize"->g.withNumeric(FineTuneParameter.LABEL_SCALE,g.fontPercent()+10);
            case "cue"->guideToggle(g,"cue");
            case "searchmode"->guideToggle(g,"searchmode");
            case "autoaim"->g.withAssist(!g.autoAim(),g.assistedFire(),g.assistSpeed());
            case "autofire"->g.withAssist(g.autoAim(),!g.assistedFire(),g.assistSpeed());
            case "aimspeed"->g.withNumeric(FineTuneParameter.AIM_RATE,g.aimRateTenths()+5);
            case "compute"->g.withComputation((g.computationMode()+1)%4);
            case "hudstyle"->g.withHudStyle((g.hudStyle()+1)%2);
            default->g;
        };
        if(action.equals("compute")||action.equals("searchmode")||action.equals("depth")||action.equals("radius")||action.equals("los")){
            WARM.clear();STAGES.clear();PRECOMPUTE_ORDER.reset();
        }
        saveGuides();
    }
    private static GuideControls guideToggle(GuideControls g,String key){
        return new GuideControls(g.radiusDegrees(),g.depthBlocks(),g.thickness(),g.opacity(),g.color(),
                key.equals("dot")?!g.centerDot():g.centerDot(),
                key.equals("labels")?!g.labels():g.labels(),
                key.equals("los")?!g.lineOfSightOnly():g.lineOfSightOnly(),
                g.manualLock(),key.equals("smoothing")?(g.smoothing()+1)%4:g.smoothing(),
                g.textSize(),key.equals("cue")?(g.cueMode()+1)%3:g.cueMode(),
                key.equals("searchmode")?(g.searchMode()+1)%2:g.searchMode(),
                g.autoAim(),g.assistedFire(),g.assistSpeed(),g.computationMode(),g.hudStyle(),
                g.fontPercent(),g.aimRateTenths());
    }
    /** Called only by the dedicated numeric editor after user presses APPLY. */
    public static int fineTuneValue(FineTuneParameter key){
        GuideControls g=guideOptions;
        return switch(key){
            case SCAN_CONE->g.radiusDegrees();
            case SENSOR_RANGE->g.depthBlocks();
            case RETICLE_WEIGHT->g.thickness();
            case HUD_ALPHA->g.opacity();
            case LABEL_SCALE->g.fontPercent();
            case AIM_RATE->g.aimRateTenths();
            case LEAD_LATENCY->advancedAim.latencyTenths();
            case CAMERA_FOV->options.fov();
        };
    }
    public static void setFineTune(FineTuneParameter key,int raw){
        int v=key.clamp(raw);
        if(key==FineTuneParameter.LEAD_LATENCY){
            advancedAim=advancedAim.withLatency(v);
            WARM.clear();STAGES.clear();PRECOMPUTE_ORDER.reset();
            SMOOTHER.reset();READY.reset();
            saveAdvancedAim();
        }else if(key==FineTuneParameter.CAMERA_FOV){
            LeadShotOptions o=options;
            options=new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),
                    o.calibration(),o.shadowAudit(),o.category(),o.mode(),v,o.blockCheck());
            try{options.write(CONFIG);}catch(Exception e){System.err.println("[LeadShot] fov save: "+e);}
        }else{
            guideOptions=guideOptions.withNumeric(key,v);
            if(key==FineTuneParameter.SCAN_CONE||key==FineTuneParameter.SENSOR_RANGE){
                WARM.clear();STAGES.clear();PRECOMPUTE_ORDER.reset();
            }
            saveGuides();
        }
    }
    private static int depthHudTtl(){return PrecomputePolicy.plan(guideOptions.computationMode()).cacheTtlTicks();}
    /** Cosmetic only: never changes automation, target filters or performance budgets. */
    public static void applyVisualPreset(int preset){
        guideOptions=guideOptions.visualPreset(preset);
        saveGuides();
    }
    private static void saveGuides(){
        try{guideOptions.save(GUIDE_CONFIG);}catch(Exception e){System.err.println("[LeadShot] guide write: "+e);}
    }
    public static AdvancedAimOptions advancedAim(){return advancedAim;}
    private static void saveAdvancedAim(){
        try{advancedAim.write(ADVANCED_CONFIG);}catch(Exception e){System.err.println("[LeadShot] advanced aim: "+e);}
    }
    public static void togglePlayerAutomation(){
        advancedAim=advancedAim.withPlayers(!advancedAim.allowPlayerAutomation());
        AIM_SYNC.reset();READY.reset();readyState=new AimReadySignal.State(false,false,0);
        saveAdvancedAim();
    }
    public static String currentCategory(){return categoryName();}
    public static void mutate(String action){
        LeadShotOptions o=options;
        options=switch(action){
            case "hud"->new LeadShotOptions(!o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),o.blockCheck());
            case "preview"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),!o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),o.blockCheck());
            case "guide"->new LeadShotOptions(o.hud(),!o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),o.blockCheck());
            case "trajectory"->new LeadShotOptions(o.hud(),o.guide(),!o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),o.blockCheck());
            case "linked"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),!o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),o.blockCheck());
            case "calibration"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),!o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),o.blockCheck());
            case "shadow"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),!o.shadowAudit(),o.category(),o.mode(),o.fov(),o.blockCheck());
            case "target"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),(o.category()+1)%3,o.mode(),o.fov(),o.blockCheck());
            case "mode"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),(o.mode()+1)%3,o.fov(),o.blockCheck());
            case "fov"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov()>=110?50:o.fov()+1,o.blockCheck());
            case "blockcheck"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),!o.blockCheck());
            default->o;
        };
        if(action.equals("target")){SMOOTHER.reset();selected=null;observations.clear();WARM.clear();STAGES.clear();PRECOMPUTE_ORDER.reset();SHADOW.clear();RELEASE_AUDIT.clear();ARROW_AUDIT.clear();}
        if(action.equals("shadow")){SHADOW.clear();RELEASE_AUDIT.clear();ARROW_AUDIT.clear();}
        try{options.write(CONFIG);}catch(Exception ex){System.err.println("[LeadShot] config save: "+ex);}
    }
}
