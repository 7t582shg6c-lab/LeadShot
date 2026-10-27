package dev.leadshot;
import java.nio.file.*;
public final class GuideControlsV35Test {
 private static int n;
 private static void check(boolean v){if(!v)throw new AssertionError("assertion "+n);n++;}
 public static void main(String[] args)throws Exception {
   Path dir=Files.createTempDirectory("guide35");Path file=dir.resolve("guide.properties");
   try{
     GuideControls d=GuideControls.defaults();
     check(d.textSize()==1&&d.cueMode()==2&&d.searchMode()==0);
     for(int i=-10;i<20;i++)for(int cue=-1;cue<=3;cue++){
       GuideControls g=new GuideControls(24,48,2,210,0,true,true,true,false,2,i,cue,i).valid();
       g.save(file); check(g.equals(GuideControls.read(file)));
       check(g.fontScale()>=.75&&g.fontScale()<=1.5);
     }
     Files.writeString(file,"smoothing=1\nopacity=123\nradius=25\n");
     GuideControls old=GuideControls.read(file);
     check(old.textSize()==1&&old.cueMode()==2&&old.searchMode()==0);
     check(old.smoothing()==1&&old.opacity()==123&&old.radiusDegrees()==25);
     check(d.withExtra(3,0,1).fontScale()==1.5);
     System.out.println("PASS GuideControlsV35Test "+n+" assertions");
   }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
 }
}
