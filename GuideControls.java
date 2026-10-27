package dev.leadshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Optional client-side visual and acquisition settings, in their own config. */
public record GuideControls(int radiusDegrees, int depthBlocks, int thickness, int opacity,
                            int color, boolean centerDot, boolean labels, boolean lineOfSightOnly,
                            boolean manualLock, int smoothing, int textSize, int cueMode, int searchMode,
                            boolean autoAim, boolean assistedFire, int assistSpeed, int computationMode, int hudStyle, int fontPercent, int aimRateTenths) {
    /** Keep compatibility with v0.33 construction sites and old regression tests. */
    public GuideControls(int radiusDegrees,int depthBlocks,int thickness,int opacity,int color,
                         boolean centerDot,boolean labels,boolean lineOfSightOnly,boolean manualLock){
        this(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,lineOfSightOnly,manualLock,2,1,2,0,false,false,2,1);
    }
    /** Compatibility with v0.34 and any existing programmatic settings. */
    public GuideControls(int radiusDegrees,int depthBlocks,int thickness,int opacity,int color,
                         boolean centerDot,boolean labels,boolean lineOfSightOnly,boolean manualLock,int smoothing){
        this(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,lineOfSightOnly,manualLock,smoothing,1,2,0,false,false,2,1);
    }
    /** Compatibility for v0.35 callers. New input controls are off by default. */
    public GuideControls(int radiusDegrees, int depthBlocks, int thickness, int opacity,
                         int color, boolean centerDot, boolean labels, boolean lineOfSightOnly,
                         boolean manualLock, int smoothing, int textSize, int cueMode, int searchMode) {
        this(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,lineOfSightOnly,
                manualLock,smoothing,textSize,cueMode,searchMode,false,false,2,1);
    }
    /** Compatibility with v0.36/v0.37 canonical construction sites. */
    public GuideControls(int radiusDegrees,int depthBlocks,int thickness,int opacity,int color,
                         boolean centerDot,boolean labels,boolean lineOfSightOnly,boolean manualLock,
                         int smoothing,int textSize,int cueMode,int searchMode,
                         boolean autoAim,boolean assistedFire,int assistSpeed){
        this(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,lineOfSightOnly,manualLock,
                smoothing,textSize,cueMode,searchMode,autoAim,assistedFire,assistSpeed,1);
    }
    /** Preserve the v0.38 17-argument constructor signature for older callers/tests. */
    public GuideControls(int radiusDegrees,int depthBlocks,int thickness,int opacity,int color,
                         boolean centerDot,boolean labels,boolean lineOfSightOnly,boolean manualLock,
                         int smoothing,int textSize,int cueMode,int searchMode,
                         boolean autoAim,boolean assistedFire,int assistSpeed,int computationMode){
        this(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,lineOfSightOnly,
                manualLock,smoothing,textSize,cueMode,searchMode,autoAim,assistedFire,assistSpeed,computationMode,1);
    }
    /** Compatibility with v0.39 persisted settings and 18-argument callers. */
    public GuideControls(int radiusDegrees,int depthBlocks,int thickness,int opacity,int color,
                         boolean centerDot,boolean labels,boolean lineOfSightOnly,boolean manualLock,
                         int smoothing,int textSize,int cueMode,int searchMode,
                         boolean autoAim,boolean assistedFire,int assistSpeed,int computationMode,int hudStyle){
        this(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,lineOfSightOnly,
                manualLock,smoothing,textSize,cueMode,searchMode,autoAim,assistedFire,assistSpeed,
                computationMode,hudStyle,legacyFontPercent(textSize),assistSpeed*10);
    }
    private static int legacyFontPercent(int text){return switch(text){case 0->75;case 2->125;case 3->150;default->100;};}
    /** DISPLAY: 0 = CLASSIC, 1 = TACTICAL. Independent from ballistic and acquisition logic. */
    public GuideControls withHudStyle(int style){
        return new GuideControls(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,
                lineOfSightOnly,manualLock,smoothing,textSize,cueMode,searchMode,
                autoAim,assistedFire,assistSpeed,computationMode,style,fontPercent,aimRateTenths).valid();
    }
    public GuideControls withComputation(int mode){
        return new GuideControls(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,
                lineOfSightOnly,manualLock,smoothing,textSize,cueMode,searchMode,autoAim,assistedFire,assistSpeed,mode,hudStyle,fontPercent,aimRateTenths).valid();
    }
    public GuideControls withAssist(boolean aim,boolean fire,int speed){
        return new GuideControls(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,
                lineOfSightOnly,manualLock,smoothing,textSize,cueMode,searchMode,aim,fire,speed,computationMode,hudStyle,fontPercent,speed==assistSpeed?aimRateTenths:speed*10).valid();
    }
    public double fontScale(){return fontPercent/100.0;}
    public GuideControls withExtra(int text,int cue,int search){
        return new GuideControls(radiusDegrees,depthBlocks,thickness,opacity,color,centerDot,labels,
                lineOfSightOnly,manualLock,smoothing,text,cue,search,autoAim,assistedFire,assistSpeed,computationMode,hudStyle,
                text==textSize?fontPercent:legacyFontPercent(text),aimRateTenths).valid();
    }
    public static GuideControls defaults() { return new GuideControls(24,48,2,210,0, true,true,true,false,2,1,2,0,false,false,2,1,1); }
    public GuideControls valid() { return new GuideControls(clamp(radiusDegrees,4,85),clamp(depthBlocks,8,128),
            clamp(thickness,1,8),clamp(opacity,0,255),clamp(color,0,3),centerDot,labels,lineOfSightOnly,manualLock,clamp(smoothing,0,3),clamp(textSize,0,3),clamp(cueMode,0,2),clamp(searchMode,0,1),autoAim,assistedFire,clamp(assistSpeed,1,4),clamp(computationMode,0,3),clamp(hudStyle,0,1),clamp(fontPercent,50,200),clamp(aimRateTenths,1,120)); }
    /** Immutable scalar mutation: never resets lock, firing, computation or style flags. */
    public GuideControls withNumeric(FineTuneParameter parameter,int raw){
        int v=parameter.clamp(raw);
        return new GuideControls(
                parameter==FineTuneParameter.SCAN_CONE?v:radiusDegrees,
                parameter==FineTuneParameter.SENSOR_RANGE?v:depthBlocks,
                parameter==FineTuneParameter.RETICLE_WEIGHT?v:thickness,
                parameter==FineTuneParameter.HUD_ALPHA?v:opacity,
                color,centerDot,labels,lineOfSightOnly,manualLock,smoothing,textSize,cueMode,searchMode,
                autoAim,assistedFire,assistSpeed,computationMode,hudStyle,
                parameter==FineTuneParameter.LABEL_SCALE?v:fontPercent,
                parameter==FineTuneParameter.AIM_RATE?v:aimRateTenths).valid();
    }
    /** Cosmetic presets are deliberately incapable of enabling firing or aim automation. */
    public GuideControls visualPreset(int preset){
        int weight=switch(preset){case 0->1;case 2->4;default->2;};
        int alpha=switch(preset){case 0->125;case 2->255;default->210;};
        int scale=switch(preset){case 0->85;case 2->140;default->100;};
        int style=preset==0?0:1;
        return new GuideControls(radiusDegrees,depthBlocks,weight,alpha,0,true,preset!=0,
                lineOfSightOnly,manualLock,preset==2?1:2,textSize,cueMode,searchMode,
                autoAim,assistedFire,assistSpeed,computationMode,style,scale,aimRateTenths).valid();
    }
    private static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }
    public int tint(int argb) {
        int rgb=switch(color) {case 1->0xFF67D9FF;case 2->0xFFFFD080;case 3->0xFFFFFFFF;default->argb;};
        return (opacity<<24)|(rgb&0xFFFFFF);
    }
    public static GuideControls read(Path file) throws IOException {
        if(!Files.isRegularFile(file))return defaults();
        Properties p=new Properties();try(InputStream in=Files.newInputStream(file)){p.load(in);}
        GuideControls d=defaults();
        return new GuideControls(number(p,"radius",d.radiusDegrees()),number(p,"depth",d.depthBlocks()),
                number(p,"thickness",d.thickness()),number(p,"opacity",d.opacity()),number(p,"color",d.color()),
                bool(p,"centerDot",d.centerDot()),bool(p,"labels",d.labels()),
                bool(p,"lineOfSightOnly",d.lineOfSightOnly()),bool(p,"manualLock",d.manualLock()),
                number(p,"smoothing",d.smoothing()),number(p,"textSize",d.textSize()),
                number(p,"cueMode",d.cueMode()),number(p,"searchMode",d.searchMode()),
                bool(p,"autoAim",d.autoAim()),bool(p,"assistedFire",d.assistedFire()),
                number(p,"assistSpeed",d.assistSpeed()),number(p,"computationMode",d.computationMode()),
                number(p,"hudStyle",d.hudStyle()),
                p.containsKey("fontPercent")?number(p,"fontPercent",d.fontPercent()):legacyFontPercent(number(p,"textSize",d.textSize())),
                number(p,"aimRateTenths",number(p,"assistSpeed",d.assistSpeed())*10)).valid();
    }
    public void save(Path file) throws IOException {
        Path pth=file.toAbsolutePath();Files.createDirectories(pth.getParent());Properties p=new Properties();
        GuideControls v=valid();
        p.setProperty("radius",String.valueOf(v.radiusDegrees()));p.setProperty("depth",String.valueOf(v.depthBlocks()));
        p.setProperty("thickness",String.valueOf(v.thickness()));p.setProperty("opacity",String.valueOf(v.opacity()));
        p.setProperty("color",String.valueOf(v.color()));p.setProperty("centerDot",String.valueOf(v.centerDot()));
        p.setProperty("labels",String.valueOf(v.labels()));p.setProperty("lineOfSightOnly",String.valueOf(v.lineOfSightOnly()));
        p.setProperty("manualLock",String.valueOf(v.manualLock()));
        p.setProperty("smoothing",String.valueOf(v.smoothing()));
        p.setProperty("textSize",String.valueOf(v.textSize()));
        p.setProperty("cueMode",String.valueOf(v.cueMode()));
        p.setProperty("searchMode",String.valueOf(v.searchMode()));
        p.setProperty("autoAim",String.valueOf(v.autoAim()));
        p.setProperty("assistedFire",String.valueOf(v.assistedFire()));
        p.setProperty("assistSpeed",String.valueOf(v.assistSpeed()));
        p.setProperty("computationMode",String.valueOf(v.computationMode()));
        p.setProperty("hudStyle",String.valueOf(v.hudStyle()));
        p.setProperty("fontPercent",String.valueOf(v.fontPercent()));
        p.setProperty("aimRateTenths",String.valueOf(v.aimRateTenths()));
        Path temp=Files.createTempFile(pth.getParent(),".leadshot-guides-",".tmp");
        try{try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"LeadShot guide options");}
            try {
                Files.move(temp,pth,java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp,pth,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{Files.deleteIfExists(temp);}
    }
    private static int number(Properties p,String k,int d){try{return Integer.parseInt(p.getProperty(k));}catch(Exception e){return d;}}
    private static boolean bool(Properties p,String k,boolean d){String v=p.getProperty(k);return "true".equalsIgnoreCase(v)?true:"false".equalsIgnoreCase(v)?false:d;}
}
