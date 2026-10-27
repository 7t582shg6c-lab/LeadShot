package dev.leadshot;
import java.nio.file.*;
import java.util.*;
public final class FineTuneV40Test {
  private static int n;
  static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason+" #"+n);n++;}
  public static void main(String[] args)throws Exception{
    GuideControls g=GuideControls.defaults().withAssist(true,true,3).withComputation(3).withHudStyle(0);
    for(FineTuneParameter p:FineTuneParameter.values()){
      ok(p.clamp(-10000)==p.minimum(),"low "+p);
      ok(p.clamp(10000)==p.maximum(),"high "+p);
      ok(p.parse("a") == null,"non-number "+p);
      ok(p.parse("123garbage") == null,"junk "+p);
      ok(p.move(p.maximum(),1,false)==p.maximum(),"overstep "+p);
      ok(p.move(p.minimum(),-1,true)==p.minimum(),"understep "+p);
      for(int i=-50;i<=350;i++){
        int clamped=p.clamp(i);
        ok(p.parse(" "+i+" ")!=null&&p.parse(" "+i+" ")==clamped,"parse "+p+" "+i);
        ok(p.ratio(i)>=0&&p.ratio(i)<=1,"ratio "+p);
        if(p!=FineTuneParameter.CAMERA_FOV){
          GuideControls newer=g.withNumeric(p,i);
          ok(newer.autoAim()&&newer.assistedFire()&&newer.computationMode()==3&&newer.hudStyle()==0,"preserve "+p);
          Path file=Files.createTempFile("v040-guide-",".properties");
          try{newer.save(file);ok(newer.equals(GuideControls.read(file)),"save "+p);}finally{Files.deleteIfExists(file);}
        }
      }
    }
    ok(g.fontScale()==1.0,"legacy font");
    ok(g.withNumeric(FineTuneParameter.LABEL_SCALE,135).fontScale()==1.35,"new font");
    GuideControls h=g.withNumeric(FineTuneParameter.AIM_RATE,17);
    ok(h.aimRateTenths()==17&&h.withHudStyle(1).aimRateTenths()==17,"fine rate persists");
    for(int i=0;i<3;i++){
      GuideControls preset=h.visualPreset(i);
      ok(preset.autoAim()&&preset.assistedFire()&&preset.computationMode()==3,"preset safety");
      ok(preset.aimRateTenths()==17&&preset.depthBlocks()==48,"preset range");
    }
    Path legacy=Files.createTempFile("v040-legacy-",".properties");
    try{
      Files.writeString(legacy,"textSize=3\nassistSpeed=4\ncomputationMode=2\n");
      GuideControls migrated=GuideControls.read(legacy);
      ok(migrated.fontPercent()==150&&migrated.aimRateTenths()==40,"legacy migration");
    }finally{Files.deleteIfExists(legacy);}
    System.out.println("PASS FineTuneV40Test "+n+" assertions");
  }
}
