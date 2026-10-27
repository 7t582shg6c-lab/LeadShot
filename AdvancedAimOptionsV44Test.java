package dev.leadshot;
import java.nio.file.*;
public final class AdvancedAimOptionsV44Test {
  public static void main(String[]args)throws Exception {
    int count=0;
    var base=AdvancedAimOptions.defaults();
    if(base.allowPlayerAutomation()||base.latencyTenths()!=8)throw new AssertionError("default safety");count++;
    var path=Files.createTempFile("leadshot-aim44-",".properties");
    try{
      for(int i=-30;i<=90;i++){
        var a=base.withLatency(i).withPlayers(true);
        a.write(path);var b=AdvancedAimOptions.read(path);
        if(!a.equals(b)||b.latencyTenths()<0||b.latencyTenths()>40)throw new AssertionError("roundtrip "+i);count++;
      }
      Files.writeString(path,"latencyTenths=broken\nplayerAutomation=true\n");
      if(AdvancedAimOptions.read(path).allowPlayerAutomation())throw new AssertionError("invalid input must disable player automation");count++;
    }finally{Files.deleteIfExists(path);}
    System.out.println("PASS AdvancedAimOptionsV44Test "+count);
  }
}
