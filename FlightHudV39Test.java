package dev.leadshot;
import java.nio.file.Files;
import java.nio.file.Path;
public final class FlightHudV39Test {
  private static int tests=0;
  private static void yes(boolean condition,String reason){tests++;if(!condition)throw new AssertionError(reason);}
  public static void main(String[] args) throws Exception {
    GuideControls base=GuideControls.defaults();
    yes(base.hudStyle()==1,"new default tactical");
    yes(base.withHudStyle(0).hudStyle()==0,"switch classic");
    yes(base.withHudStyle(2).hudStyle()==1,"sanitize style");
    for(int style=0;style<2;style++){
      GuideControls x=base.withHudStyle(style).withAssist(true,true,4).withComputation(3).withExtra(3,1,1);
      yes(x.hudStyle()==style&&x.autoAim()&&x.assistedFire()&&x.computationMode()==3,"fields preserved");
      Path f=Files.createTempFile("flight-hud-", ".properties");
      try {x.save(f);GuideControls y=GuideControls.read(f);
        yes(y.hudStyle()==style&&y.autoAim()&&y.assistedFire()&&y.computationMode()==3,"roundtrip");
      } finally {Files.deleteIfExists(f);}
    }
    for(int w=100;w<=1920;w+=37){
      for(int h=80;h<=1080;h+=47){
        for(int m=0;m<3;m++){
          for(double scale:new double[]{.75,1.0,1.25,1.5}){
            var l=FlightHudLayout.calculate(w,h,m,scale);
            yes(l.telemetry().onScreen(w,h),"telemetry fits");
            yes(l.cue().onScreen(w,h),"cue fits");
            yes(l.systems()==null||l.systems().onScreen(w,h),"system fits");
            if(l.systems()!=null)yes(l.telemetry().x()+l.telemetry().width()<l.systems().x(),"panels separate");
          }
        }
      }
    }
    yes(FlightHudTheme.state(true,false,true,true,true)==FlightHudTheme.State.READY,"ready");
    yes(FlightHudTheme.state(true,true,true,true,true)==FlightHudTheme.State.OBSTRUCTED,"blocked overrides ready");
    yes(FlightHudTheme.state(false,false,false,false,true)==FlightHudTheme.State.ACQUIRING,"charging");
    yes(FlightHudTheme.color(FlightHudTheme.State.READY)!=FlightHudTheme.color(FlightHudTheme.State.OBSTRUCTED),"distinct color");
    yes(FlightHudTheme.radar(0).equals("BORESIGHT"),"mode name");
    yes(FlightHudTheme.name(1).equals("TACTICAL"),"style name");
    System.out.println("PASS Flight HUD 0.39 tests: "+tests+" assertions");
  }
}
