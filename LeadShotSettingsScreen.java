package dev.leadshot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Two-page, opt-in diagnostics; settings persist immediately. */
public final class LeadShotSettingsScreen extends Screen {
    private int page;
    public LeadShotSettingsScreen(){super(Component.literal("LeadShot Fabric 26.3 alpha"));}
    @Override protected void init(){
        int w=Math.min(234,Math.max(110,width-16));
        int x=(width-w)/2;
        int step=Math.max(18,Math.min(25,(height-45)/7));
        int y=Math.max(24,(height-step*7)/2);
        if(height<160){add(x,y,w,"画面を拡大 / 閉じる",()->onClose());return;}
        LeadShotOptions o=LeadShot.options();
        if(page==0){
            add(x,y,w,"HUD: "+flag(o.hud()),()->LeadShot.mutate("hud"));
            add(x,y+step,w,"予測: "+flag(o.preview()),()->LeadShot.mutate("preview"));
            add(x,y+2*step,w,"ガイド: "+flag(o.guide()),()->LeadShot.mutate("guide"));
            add(x,y+3*step,w,"飛行点: "+flag(o.trajectory()),()->LeadShot.mutate("trajectory"));
            add(x,y+4*step,w,"HUD連動: "+flag(o.linked()),()->LeadShot.mutate("linked"));
        }else if(page==1){
            add(x,y,w,"対象: "+LeadShot.currentCategory(),()->LeadShot.mutate("target"));
            add(x,y+step,w,"HUD表示: "+(o.mode()==0?"MIN":o.mode()==1?"DETAIL":"ANALYSIS"),()->LeadShot.mutate("mode"));
            add(x,y+2*step,w,"画角近似: "+o.fov()+"°",()->LeadShot.mutate("fov"));
            add(x,y+3*step,w,"テスト表示: "+flag(o.calibration()),()->LeadShot.mutate("calibration"));
            add(x,y+4*step,w,"匿名誤差監査: "+flag(o.shadowAudit()),()->LeadShot.mutate("shadow"));
        }else if(page==2){
            add(x,y,w,"ブロック遮蔽検査: "+flag(o.blockCheck()),()->LeadShot.mutate("blockcheck"));
            add(x,y+step,w,"検査上限: 48区間",()->{});
            add(x,y+2*step,w,"未検査は安全と判定しません",()->{});
            add(x,y+3*step,w,"遮蔽越しの索敵: "+(LeadShot.guideControls().lineOfSightOnly()?"禁止":"許可"),()->LeadShot.changeGuide("los"));
        }else if(page==3){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"索敵角: "+g.radiusDegrees()+"°",()->LeadShot.changeGuide("radius"));
            add(x,y+step,w,"距離: "+g.depthBlocks()+"ブロック",()->LeadShot.changeGuide("depth"));
            add(x,y+2*step,w,"太さ: "+g.thickness(),()->LeadShot.changeGuide("thickness"));
            add(x,y+3*step,w,"不透明度: "+g.opacity(),()->LeadShot.changeGuide("opacity"));
            add(x,y+4*step,w,"色: "+g.color(),()->LeadShot.changeGuide("color"));
        }else if(page==4){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"中央ドット: "+flag(g.centerDot()),()->LeadShot.changeGuide("dot"));
            add(x,y+step,w,"AIM/PRED文字: "+flag(g.labels()),()->LeadShot.changeGuide("labels"));
            add(x,y+2*step,w,"ガイドの滑らかさ: "+switch(g.smoothing()){case 0->"OFF";case 1->"FAST";case 2->"BALANCED";default->"SMOOTH";},()->LeadShot.changeGuide("smoothing"));
            add(x,y+3*step,w,"文字倍率: "+(int)(g.fontScale()*100)+"%",()->LeadShot.changeGuide("textsize"));
            add(x,y+4*step,w,"READY通知: "+switch(g.cueMode()){case 0->"OFF";case 1->"VISUAL";default->"SOUND + VISUAL";},()->LeadShot.changeGuide("cue"));
        }
        if(page==5){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"索敵モード: "+(g.searchMode()==0?"CENTER":"RADAR"),()->LeadShot.changeGuide("searchmode"));
            add(x,y+step,w,"ロック: "+LeadShot.lockStatus(),()->{});
            add(x,y+2*step,w,"I: ロック切替 / P: 次の対象",()->{});
            add(x,y+3*step,w,"K: ロック解除",()->{});
            add(x,y+4*step,w,"RADARでも壁越しは原則除外",()->{});
        }
        if(page==6){
            GuideControls g=LeadShot.guideControls();
            add(x,y,w,"ロック照準補助: "+flag(g.autoAim()),()->LeadShot.changeGuide("autoaim"));
            add(x,y+step,w,"追従速度: "+g.assistSpeed()+"°/tick",()->LeadShot.changeGuide("aimspeed"));
            add(x,y+2*step,w,"補助発射: "+flag(g.assistedFire()),()->LeadShot.changeGuide("autofire"));
            add(x,y+3*step,w,"右クリック＋ホイール押下で1射",()->{});
            add(x,y+4*step,w,"手動ロック・敵対モブ・READY必須",()->{});
        }
        add(x,y+5*step,w,"ページ "+(page+1)+"/7",()->{page=(page+1)%7;});
        add(x,y+6*step,w,"閉じる",()->onClose());
    }
    private void add(int x,int y,int w,String title,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(title),b->{action.run();if(b!=null&&!title.equals("閉じる")){
            if(this.minecraft.gui.screen()==this)rebuildWidgets();
        }}).bounds(x,y,w,Math.max(16,Math.min(20,(height-45)/7-2))).build());
    }
    private static String flag(boolean state){return state?"ON":"OFF";}
    @Override public void extractRenderState(GuiGraphicsExtractor gfx,int mouseX,int mouseY,float partial){
        super.extractRenderState(gfx,mouseX,mouseY,partial);
        gfx.text(this.font,"LeadShot 26.3 / 復元試験版",Math.max(5,width/2-100),8,0xFFFFFFFF,true);
    }
    @Override public void onClose(){this.minecraft.gui.setScreen(null);}
}
