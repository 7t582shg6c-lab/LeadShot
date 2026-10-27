package dev.leadshot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dedicated numeric configuration page; only APPLY persists changes. */
public final class LeadShotFineTuneScreen extends Screen {
    private final LeadShotSettingsScreen parent;
    private final FineTuneParameter parameter;
    private EditBox field;
    private String status="";
    public LeadShotFineTuneScreen(LeadShotSettingsScreen parent,FineTuneParameter parameter){
        super(Component.literal("LEADSHOT / PRECISION TUNE"));
        this.parent=parent;this.parameter=parameter;
    }
    @Override protected void init(){
        int cx=width/2,top=contentTop();
        int formWidth=Math.min(304,Math.max(80,width-12));
        int left=(width-formWidth)/2;
        field=new EditBox(font,cx-Math.min(75,formWidth/2),top+33,Math.min(150,formWidth),20,
                Component.literal(parameter.label()));
        field.setMaxLength(8);
        field.setValue(String.valueOf(LeadShot.fineTuneValue(parameter)));
        addRenderableWidget(field);
        int gap=3,adjustWidth=(formWidth-gap*3)/4;
        button(left,top+63,adjustWidth,"-"+parameter.largeStep(),()->shift(-1,true));
        button(left+adjustWidth+gap,top+63,adjustWidth,"-"+parameter.smallStep(),()->shift(-1,false));
        button(left+2*(adjustWidth+gap),top+63,adjustWidth,"+"+parameter.smallStep(),()->shift(1,false));
        button(left+3*(adjustWidth+gap),top+63,adjustWidth,"+"+parameter.largeStep(),()->shift(1,true));
        int actionWidth=(formWidth-gap*2)/3;
        button(left,top+100,actionWidth,"初期値",()->{field.setValue(String.valueOf(parameter.defaults()));status="";});
        button(left+actionWidth+gap,top+100,actionWidth,"適用 / APPLY",this::apply);
        button(left+2*(actionWidth+gap),top+100,actionWidth,"キャンセル",this::onClose);
    }
    private int contentTop(){return Math.max(24,Math.min(height-150,height/2-88));}
    private void button(int x,int y,int width,String title,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(x,y,width,20).build());
    }
    private void shift(int direction,boolean coarse){
        Integer now=parameter.parse(field.getValue());
        if(now==null){status="数字を入力してください";return;}
        field.setValue(String.valueOf(parameter.move(now,direction,coarse)));
        status="";
    }
    private void apply(){
        Integer candidate=parameter.parse(field.getValue());
        if(candidate==null){status="無効な数値です。半角整数で入力してください";return;}
        LeadShot.setFineTune(parameter,candidate);
        minecraft.gui.setScreen(parent);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor gfx,int mouseX,int mouseY,float delta){
        int cx=width/2,top=contentTop();
        int formWidth=Math.min(304,Math.max(80,width-12));
        int left=(width-formWidth)/2;
        gfx.fill(Math.max(0,left-5),Math.max(0,top-22),Math.min(width,left+formWidth+5),Math.min(height,top+137),0xCC071825);
        gfx.text(font,"PRECISION TUNE / "+parameter.label(),left,top-17,0xFF79E6FF,true);
        gfx.text(font,parameter.description(),left,top+7,0xFFE0EDF3,true);
        gfx.text(font,"設定範囲: "+parameter.show(parameter.minimum())+" 〜 "+parameter.show(parameter.maximum()),left,top+22,0xFFAAC8DA,true);
        Integer candidate=field==null?null:parameter.parse(field.getValue());
        if(candidate!=null){
            int topBar=top+91;
            gfx.fill(left,topBar,left+formWidth,topBar+3,0xFF243C49);
            int progress=(int)Math.round(formWidth*parameter.ratio(candidate));
            if(progress>0)gfx.fill(left,topBar,left+progress,topBar+3,0xFF6BE7C9);
        }
        if(!status.isEmpty())gfx.text(font,status,left,top+126,0xFFFFA174,true);
        if(height>=185)gfx.text(font,"キャンセルは元の値を維持します",left,Math.min(height-14,top+146),0xFF91AEB9,true);
        // Render widgets last so the translucent backing never obscures the input box.
        super.extractRenderState(gfx,mouseX,mouseY,delta);
    }
}
