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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
 * No camera steering, packet writing, guaranteed contacts, or wall occlusion claims.
 */
public final class LeadShot implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("leadshot", "main"));
    private static final KeyMapping HUD = key("key.leadshot.hud_visibility", InputConstants.KEY_U);
    private static final KeyMapping PREVIEW = key("key.leadshot.preview", InputConstants.KEY_V);
    private static final KeyMapping TARGET = key("key.leadshot.cycle_target", InputConstants.KEY_N);
    private static final KeyMapping MARKERS = key("key.leadshot.markers", InputConstants.KEY_G);
    private static final KeyMapping MODES = key("key.leadshot.hud_mode", InputConstants.KEY_B);
    private static final KeyMapping SETTINGS = key("key.leadshot.settings", InputConstants.KEY_O);
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("leadshot-26.3.properties");
    private static final Map<UUID, Tracked> observations = new HashMap<>();
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

    private record Tracked(CoreBallistics.Vec center, long tick) {}
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
        static Preview charging(){return new Preview("検索中","-",0,null,null,null,null,true,null,null,null);}
    }
    private static KeyMapping key(String text,int id){
        return new KeyMapping(text,InputConstants.Type.KEYBOARD,id,CATEGORY);
    }
    @Override public void onInitializeClient(){
        try{options=LeadShotOptions.read(CONFIG);}catch(Exception e){System.err.println("[LeadShot] config read: "+e);}
        for(KeyMapping k:List.of(HUD,PREVIEW,TARGET,MARKERS,MODES,SETTINGS))
            KeyMappingHelper.registerKeyMapping(k);
        ClientTickEvents.END_CLIENT_TICK.register(LeadShot::onTick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("leadshot", "hud"), (graphics,delta)->drawHud(graphics));
    }
    private static void onTick(Minecraft mc){
        tick++;
        worldGuide = WorldGuideGeometry.Frame.empty();
        if(mc.level!=lastLevel){lastLevel=mc.level;observations.clear();SHADOW.clear();RELEASE_AUDIT.clear();ARROW_AUDIT.clear();previousBowUse=false;selected=null;preview=Preview.empty();}
        while(HUD.consumeClick())if(mc.gui.screen()==null)mutate("hud");
        while(PREVIEW.consumeClick())if(mc.gui.screen()==null)mutate("preview");
        while(TARGET.consumeClick())if(mc.gui.screen()==null)mutate("target");
        while(MARKERS.consumeClick())if(mc.gui.screen()==null)mutate("guide");
        while(MODES.consumeClick())if(mc.gui.screen()==null)mutate("mode");
        while(SETTINGS.consumeClick())if(mc.gui.screen()==null)mc.gui.setScreen(new LeadShotSettingsScreen());
        LocalPlayer shooter=mc.player;
        if(shooter==null||mc.level==null){preview=Preview.empty();previousBowUse=false;return;}
        AABB search=shooter.getBoundingBox().inflate(64);
        List<LivingEntity> targets=mc.level.getEntitiesOfClass(LivingEntity.class,search,
                e->e!=shooter&&e.isAlive()&&!e.isSpectator()&&eligible(e));
        targets.sort(Comparator.comparingDouble(shooter::distanceToSqr));
        int count=Math.min(64,targets.size());
        Map<UUID,CoreBallistics.Vec> speeds=new HashMap<>();
        for(int i=0;i<count;i++){
            LivingEntity entity=targets.get(i);
            CoreBallistics.Vec center=vec(entity.getBoundingBox().getCenter());
            UUID id=entity.getUUID();Tracked old=observations.get(id);
            CoreBallistics.Vec velocity=estimate(center,old,tick);
            speeds.put(id,velocity);
            observations.put(id,new Tracked(center,tick));
            if(options.shadowAudit()){
                SHADOW.observe(id,tick,center.x(),center.y(),center.z());
                RELEASE_AUDIT.observe(id,tick,center);
            }
        }
        observations.entrySet().removeIf(e->tick-e.getValue().tick()>25);
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
        if(!bow||!options.preview()){preview=Preview.empty();return;}
        preview=Preview.charging();
        double power=CoreBallistics.vanillaBowPower(shooter.getTicksUsingItem());
        double speed=CoreBallistics.FULL_BOW_SPEED*power;
        if(speed<.3)return;
        CoreBallistics.Vec eye=vec(shooter.getEyePosition());
        CoreBallistics.Vec shootVelocity=vec(shooter.getDeltaMovement());
        Candidate best=null, previous=null;
        List<Candidate> candidates=new ArrayList<>();
        for(int i=0;i<count;i++){
            LivingEntity entity=targets.get(i);
            CoreBallistics.Vec center=vec(entity.getBoundingBox().getCenter());
            CoreBallistics.Vec velocity=speeds.get(entity.getUUID());
            CoreBallistics.Solution shot=CoreBallistics.solve(eye,center,velocity,shootVelocity,speed);
            if(shot==null||shot.speedMismatch()>Math.max(.18,speed*.10))continue;
            double yaw=Math.abs(CoreBallistics.wrap(shot.yaw()-shooter.getYRot()));
            double pitch=Math.abs(shot.pitch()-shooter.getXRot());
            if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||yaw>55||pitch>50)continue;
            AABB bb=entity.getBoundingBox();
            CoreBallistics.Vec half=new CoreBallistics.Vec(
                    Math.max(.01,bb.getXsize()*.5),Math.max(.01,bb.getYsize()*.5),Math.max(.01,bb.getZsize()*.5));
            ContactAudit.Result contact=ContactAudit.evaluate(eye,shot,center,velocity,half,
                    shootVelocity,speed,Math.max(.18,speed*.10));
            double score=Math.hypot(yaw,pitch)+20*shot.speedMismatch();
            Candidate c=new Candidate(entity,shot,center,velocity,half,contact,score);
            candidates.add(c);
            if(best==null||c.score()<best.score())best=c;
            if(entity.getUUID().equals(selected))previous=c;
        }
        if(best==null){selected=null;return;}
        Candidate chosen=best;
        if(previous!=null&&!TargetStability.switchToNew(previous.score(),best.score(),true))chosen=previous;
        selected=chosen.entity().getUUID();
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
        preview=new Preview(chosen.entity().getName().getString(),
                chosen.entity() instanceof Player?"PLAYER":chosen.entity() instanceof Enemy?"HOSTILE":"MOB",
                shooter.distanceTo(chosen.entity()),eye,shootVelocity,chosen.shot(),chosen.collision(),true,chosen.half(),obstruction,consensus);
    }
    private static CoreBallistics.Vec estimate(CoreBallistics.Vec now,Tracked prev,long at){
        if(prev==null||at<=prev.tick()||at-prev.tick()>5)return new CoreBallistics.Vec(0,0,0);
        CoreBallistics.Vec v=now.minus(prev.center()).times(1.0/(at-prev.tick()));
        return v.finite()&&v.length()<3.5?v:new CoreBallistics.Vec(0,0,0);
    }
    private static boolean eligible(LivingEntity entity){
        return switch(options.category()){
            case 0->entity instanceof Enemy;
            case 1->entity instanceof Player;
            default->true;
        };
    }
    private static CoreBallistics.Vec vec(Vec3 v){return new CoreBallistics.Vec(v.x,v.y,v.z);}
    private static void drawHud(GuiGraphicsExtractor gfx){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.gui.screen()!=null)return;
        int w=gfx.guiWidth(),h=gfx.guiHeight();if(w<100||h<80)return;
        if(options.guide()&&(!options.linked()||options.hud()))drawGuides(gfx,mc,w,h);
        if(!options.hud())return;
        int effectiveMode = h < 145 ? 0 : h < 205 ? Math.min(1, options.mode()) : options.mode();
        int bottom=effectiveMode==0?48:effectiveMode==1?132:190;
        gfx.fill(4,4,Math.min(w-4,302),Math.min(h-4,bottom),0xB0101B28);
        line(gfx,mc,"LeadShot 26.3 | INTEGRATED ALPHA",8,8,0xFFD7EEFF);
        line(gfx,mc,"対象: "+categoryName()+" | "+trim(preview.target(),21),8,22,0xFFFFFFFF);
        if(effectiveMode==0)return;
        if(preview.solution()==null){line(gfx,mc,preview.drawing()?"照準候補なし":"弓を引いて観測",8,36,0xFFFFC97A);}
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
            line(gfx,mc,"クライアント推定・命中確定ではありません",8,106,0xFFFFA6AA);
        }
        if(effectiveMode==2){
            line(gfx,mc,options.shadowAudit()?"比較監査: ON (ログ匿名)":"比較監査: OFF",8,120,0xFFA9BACE);
            if(options.shadowAudit()){
                MotionShadowAudit.RecentComparison c=SHADOW.recentComparison(10);
                line(gfx,mc,c.samples()>0?String.format(Locale.ROOT,"10t n=%d raw=%.2f filtered=%.2f",c.samples(),c.rawMae(),c.experimentalMae()):"10t: ペア観測なし",8,134,0xFFC3D5E2);
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
    private static void drawGuides(GuiGraphicsExtractor gfx,Minecraft mc,int w,int h){
        if(preview.solution()!=null){
            var s=preview.solution();
            AimProjection.Point aim=AimProjection.angle(s.yaw(),s.pitch(),mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov());
            if(aim!=null&&aim.inFront()){
                int x=(int)Math.round(aim.x()),y=(int)Math.round(aim.y());
                // Blocked cannot be painted green; unchecked stays amber.
                var confidence=ShotConfidence.classify(preview.collision(),preview.consensus(),preview.obstruction());
                int guideColor=aim.visible()?ShotConfidence.color(confidence):0xFFFFCB72;
                drawCross(gfx,x,y,guideColor);
                if(aim.visible())line(gfx,mc,"AIM",x+10,y-4,guideColor);
                // World-space rings are projected to the HUD. This is NOT depth-tested.
                for(var edge:ProjectedAimRings.aimRing(preview.eye(),s.yaw(),s.pitch(),
                        mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov(),10)) {
                    drawApproxLine(gfx,(int)edge.from().x(),(int)edge.from().y(),
                            (int)edge.to().x(),(int)edge.to().y(),guideColor);
                }
            }
            AimProjection.Point future=AimProjection.world(preview.eye(),s.futureCenter(),mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov());
            if(future!=null&&future.inFront()){
                int x=(int)Math.round(future.x()),y=(int)Math.round(future.y());
                gfx.fill(x-5,y-5,x+6,y-4,0xFF62D9FC);gfx.fill(x-5,y+5,x+6,y+6,0xFF62D9FC);
                gfx.fill(x-5,y-4,x-4,y+5,0xFF62D9FC);gfx.fill(x+5,y-4,x+6,y+5,0xFF62D9FC);
                if(future.visible())line(gfx,mc,"PRED",x+10,y-4,0xFF62D9FC);
                if(preview.half()!=null){
                    double ringRadius=Math.max(.17,Math.min(1.0,
                            Math.max(preview.half().x(),preview.half().y())));
                    for(var edge:ProjectedAimRings.targetRing(preview.eye(),s.futureCenter(),
                            mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov(),ringRadius)){
                        drawApproxLine(gfx,(int)edge.from().x(),(int)edge.from().y(),
                                (int)edge.to().x(),(int)edge.to().y(),0xFF62D9FC);
                    }
                }
            }
            // Future target envelope. Draw only fully visible, front-facing edges.
            if(preview.half()!=null&&preview.half().finite()){
                for(var segment:ProjectedBox.edges(preview.eye(),s.futureCenter(),preview.half(),
                        mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov())){
                    drawApproxLine(gfx,(int)segment.from().x(),(int)segment.from().y(),
                            (int)segment.to().x(),(int)segment.to().y(),0xAA67D9FA);
                }
            }
            if(options.trajectory()){
                for(var point:FlightGuidePoints.sample(preview.eye(),s.launchVelocity().add(preview.shooterVelocity()),s.ticks())){
                    AimProjection.Point p=AimProjection.world(preview.eye(),point,mc.player.getYRot(),mc.player.getXRot(),w,h,options.fov());
                    if(p!=null&&p.visible())gfx.fill((int)p.x()-1,(int)p.y()-1,(int)p.x()+2,(int)p.y()+2,0xFFFFC37B);
                }
            }
        }else if(options.calibration()&&!preview.drawing()){
            drawCross(gfx,w/2,h/2,0xFF62D9FC);
            line(gfx,mc,"TEST ONLY",Math.min(w-62,w/2+12),h/2-4,0xFF62D9FC);
        }
    }
    /** Bounded 2D line extraction; at most 48 rectangles per edge. */
    private static void drawApproxLine(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1,int rgb){
        int segments=Math.min(48,Math.max(1,Math.max(Math.abs(x1-x0),Math.abs(y1-y0))));
        for(int i=0;i<=segments;i++){
            int x=(int)Math.round(x0+(x1-x0)*i/(double)segments);
            int y=(int)Math.round(y0+(y1-y0)*i/(double)segments);
            g.fill(x,y,x+1,y+1,rgb);
        }
    }
    private static void drawCross(GuiGraphicsExtractor g,int x,int y,int color){
        g.fill(x-9,y,x-3,y+1,color);g.fill(x+4,y,x+10,y+1,color);
        g.fill(x,y-9,x+1,y-3,color);g.fill(x,y+4,x+1,y+10,color);
    }
    private static void line(GuiGraphicsExtractor gfx,Minecraft mc,String text,int x,int y,int color){gfx.text(mc.font,text,x,y,color,true);}
    private static String trim(String s,int max){return s.length()<=max?s:s.substring(0,max-1)+"…";}
    private static String categoryName(){return switch(options.category()){case 0->"敵対モブ";case 1->"プレイヤー";default->"すべて";};}
    /** Not drawn yet; future 26.3 level renderer may consume this immutable geometry only. */
    public static WorldGuideGeometry.Frame lastWorldGuideFrame(){return worldGuide;}
    public static LeadShotOptions options(){return options;}
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
            case "fov"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov()>=100?60:o.fov()+10,o.blockCheck());
            case "blockcheck"->new LeadShotOptions(o.hud(),o.guide(),o.trajectory(),o.preview(),o.linked(),o.calibration(),o.shadowAudit(),o.category(),o.mode(),o.fov(),!o.blockCheck());
            default->o;
        };
        if(action.equals("target")){selected=null;observations.clear();SHADOW.clear();RELEASE_AUDIT.clear();ARROW_AUDIT.clear();}
        if(action.equals("shadow")){SHADOW.clear();RELEASE_AUDIT.clear();ARROW_AUDIT.clear();}
        try{options.write(CONFIG);}catch(Exception ex){System.err.println("[LeadShot] config save: "+ex);}
    }
}
