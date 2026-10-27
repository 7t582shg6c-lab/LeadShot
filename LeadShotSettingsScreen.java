package dev.leadshot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Nine-page client-side fire-control configuration; changes persist immediately. */
public final class LeadShotSettingsScreen extends Screen {
    private int page;
    public LeadShotSettingsScreen(){super(Component.literal("LEADSHOT / FIRE CONTROL SYSTEM"));}
    @Override protected void init(){
        int w=Math.min(312,Math.max(140,width-24));
        int x=(width-w)/2;
        int step=Math.max(18,Math.min(25,(height-45)/7));
        int y=Math.max(24,(height-step*7)/2);
        if(height<160){add(x,y,w,"画面を拡大 / 閉じる",()->onClose());return;}
        LeadShotOptions o=LeadShot.options();
        if(page==0){
            add(x,y,w,"MAIN HUD / 主表示: "+flag(o.hud()),()->LeadShot.mutate("hud"));
            add(x,y+step,w,"FIRE SOLUTION / 予測: "+flag(o.preview()),()->LeadShot.mutate("preview"));
            add(x,y+2*step,w,"RETICLE / 照準: "+flag(o.guide()),()->LeadShot.mutate("guide"));
            add(x,y+3*step,w,"TRAJECTORY / 弾道: "+flag(o.trajectory()),()->LeadShot.mutate("trajectory"));
            add(x,y+4*step,w,"LINK HUD / 連動: "+flag(o.linked()),()->LeadShot.mutate("linked"));
        }else if(page==1){
            add(x,y,w,"TARGET FILTER / 対象: "+LeadShot.currentCategory(),()->LeadShot.mutate("target"));
            add(x,y+step,w,"DATA LEVEL: "+(o.mode()==0?"BASIC":o.mode()==1?"TACTICAL":"TELEMETRY"),()->LeadShot.mutate("mode"));
            add(x,y+2*step,w,"画角 / FOV: "+o.fov()+"°  [詳細調整]",()->fine(FineTuneParameter.CAMERA_FOV));
            add(x,y+3*step,w,"テスト表示: "+flag(o.calibration()),()->LeadShot.mutate("calibration"));
            add(x,y+4*step,w,"匿名誤差監査: "+flag(o.shadowAudit()),()->LeadShot.mutate("shadow"));
        }else if(page==2){
            add(x,y,w,"ブロック遮蔽検査: "+flag(o.blockCheck()),()->LeadShot.mutate("blockcheck"));
            add(x,y+step,w,"検査上限: 48区間",()->{});
            add(x,y+2*step,w,"未検査は安全と判定しません",()->{});
            add(x,y+3*step,w,"遮蔽越しの索敵: "+(LeadShot.guideControls().lineOfSightOnly()?"禁止":"許可"),()->LeadShot.changeGuide("los"));
        }else if(page==3){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"索敵角: "+g.radiusDegrees()+"°  [詳細調整]",()->fine(FineTuneParameter.SCAN_CONE));
            add(x,y+step,w,"索敵距離: "+g.depthBlocks()+"m  [詳細調整]",()->fine(FineTuneParameter.SENSOR_RANGE));
            add(x,y+2*step,w,"線の太さ: "+g.thickness()+"px  [詳細調整]",()->fine(FineTuneParameter.RETICLE_WEIGHT));
            add(x,y+3*step,w,"透明度: "+g.opacity()+"/255  [詳細調整]",()->fine(FineTuneParameter.HUD_ALPHA));
            add(x,y+4*step,w,"色: "+g.color(),()->LeadShot.changeGuide("color"));
        }else if(page==4){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"中央ドット: "+flag(g.centerDot()),()->LeadShot.changeGuide("dot"));
            add(x,y+step,w,"AIM/PRED文字: "+flag(g.labels()),()->LeadShot.changeGuide("labels"));
            add(x,y+2*step,w,"ガイドの滑らかさ: "+switch(g.smoothing()){case 0->"OFF";case 1->"FAST";case 2->"BALANCED";default->"SMOOTH";},()->LeadShot.changeGuide("smoothing"));
            add(x,y+3*step,w,"文字倍率: "+g.fontPercent()+"%  [詳細調整]",()->fine(FineTuneParameter.LABEL_SCALE));
            add(x,y+4*step,w,"READY通知: "+switch(g.cueMode()){case 0->"OFF";case 1->"VISUAL";default->"SOUND + VISUAL";},()->LeadShot.changeGuide("cue"));
        }
        if(page==5){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"SENSOR: "+FlightHudTheme.radar(g.searchMode()),()->LeadShot.changeGuide("searchmode"));
            add(x,y+step,w,"TRACK MODE: "+LeadShot.lockStatus(),()->{});
            add(x,y+2*step,w,"I: ロック切替 / P: 次の対象",()->{});
            add(x,y+3*step,w,"K: ロック解除",()->{});
            add(x,y+4*step,w,"RADARでも壁越しは原則除外",()->{});
        }
        if(page==6){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"自動照準 (両モード): "+flag(g.autoAim()),()->LeadShot.changeGuide("autoaim"));
            add(x,y+step,w,"追従速度: "+FineTuneParameter.AIM_RATE.show(g.aimRateTenths())+" [詳細調整]",()->fine(FineTuneParameter.AIM_RATE));
            add(x,y+2*step,w,"連続発射補助: "+flag(g.assistedFire()),()->LeadShot.changeGuide("autofire"));
            add(x,y+3*step,w,"右＋左クリック保持で条件成立時に反復",()->{});
            add(x,y+4*step,w,"CENTER/RADAR両対応・敵対モブ・READY必須",()->{});
        }
        if(page==7){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"PROCESS: "+FlightHudTheme.computation(g.computationMode()),()->LeadShot.changeGuide("compute"));
            PrecomputePolicy.Plan plan=PrecomputePolicy.plan(g.computationMode());
            add(x,y+step,w,"共通追跡枠: 最大 "+plan.trackedLimit()+"体 (全モード共通)",()->{});
            add(x,y+2*step,w,"計算深度: "+plan.detail(),()->{});
            add(x,y+3*step,w,"重い事前処理: "+plan.heavyUpdatesPerTick()+"体/tick (全64体を巡回)",()->{});
            add(x,y+4*step,w,"READY・補助発射: 選択対象を最新情報で再検査",()->{});
        }
        if(page==8){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"HUD SKIN: "+FlightHudTheme.name(g.hudStyle()),()->LeadShot.changeGuide("hudstyle"));
            add(x,y+step,w,"プリセット: MINIMAL (非戦闘操作は維持)",()->LeadShot.applyVisualPreset(0));
            add(x,y+2*step,w,"プリセット: TACTICAL / STANDARD",()->LeadShot.applyVisualPreset(1));
            add(x,y+3*step,w,"プリセット: HIGH VISIBILITY",()->LeadShot.applyVisualPreset(2));
            add(x,y+4*step,w,"READYは命中保証ではありません",()->{});
        }
        add(x,y+5*step,(w-6)/2,"◀ 前のページ",()->{page=(page+8)%9;});
        add(x+(w+6)/2,y+5*step,(w-6)/2,"次のページ ▶",()->{page=(page+1)%9;});
        add(x,y+6*step,w,"閉じる",()->onClose());
    }
    private void fine(FineTuneParameter id){minecraft.gui.setScreen(new LeadShotFineTuneScreen(this,id));}
    private void add(int x,int y,int w,String title,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(title),b->{action.run();if(b!=null&&!title.equals("閉じる")){
            if(this.minecraft.gui.screen()==this)rebuildWidgets();
        }}).bounds(x,y,w,Math.max(16,Math.min(20,(height-45)/7-2))).build());
    }
    private static String flag(boolean state){return state?"ON":"OFF";}
    @Override public void extractRenderState(GuiGraphicsExtractor gfx,int mouseX,int mouseY,float partial){
        super.extractRenderState(gfx,mouseX,mouseY,partial);
        // Thin cockpit-style frame only: never paint across vanilla focusable buttons.
        gfx.fill(4,22,6,height-24,0xDD3A94AA);
        gfx.fill(width-6,22,width-4,height-24,0xDD3A94AA);
        gfx.fill(6,height-21,width-6,height-20,0xDD3A94AA);
        String section=switch(page){
            case 0->"DISPLAY";case 1->"TARGET DATA";case 2->"PATH SAFETY";
            case 3->"SENSOR GAIN";case 4->"SYMBOL SET";case 5->"TRACK CONTROL";
            case 6->"WEAPON CTRL";case 7->"PROCESSOR";default->"HUD THEME";
        };
        gfx.text(this.font,"SYS / "+section+"   PAGE "+(page+1)+" / 9",8,height-16,0xFF8CCCDD,true);
        gfx.fill(0,0,width,18,0xDB071720);
        gfx.fill(0,17,width,18,0xFF348CA3);
        gfx.text(this.font,"LEADSHOT   /   FLIGHT CONTROL   /   "+FlightHudTheme.name(LeadShot.guideControls().hudStyle()),
                Math.max(5,width/2-144),5,0xFFB5F6FF,true);
    }
    @Override public void onClose(){this.minecraft.gui.setScreen(null);}
}
