package dev.leadshot;
import java.nio.file.*;
public final class GuideControlsV36Test {
 private static int n;
 private static void check(boolean x){if(!x)throw new AssertionError("case "+n);n++;}
 public static void main(String[] a)throws Exception {
  GuideControls d=GuideControls.defaults();
  check(!d.autoAim()&&!d.assistedFire()&&d.assistSpeed()==2);
  for(int speed=-3;speed<=10;speed++)for(boolean aim:new boolean[]{true,false})
   for(boolean fire:new boolean[]{true,false}){
    GuideControls g=d.withAssist(aim,fire,speed);
    check(g.autoAim()==aim&&g.assistedFire()==fire);
    check(g.assistSpeed()==Math.max(1,Math.min(4,speed)));
    Path p=Files.createTempFile("leadshot-v036-settings-",".properties");
    try{g.save(p);GuideControls b=GuideControls.read(p);check(b.equals(g));}
    finally{Files.deleteIfExists(p);}
   }
  var legacy =new GuideControls(24,48,2,210,0,true,true,true,false,2,1,2,0);
  check(!legacy.autoAim()&&!legacy.assistedFire());
  System.out.println("PASS GuideControlsV36Test "+n+" assertions");
 }
}
