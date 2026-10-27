package dev.leadshot;
public final class HudDensityV41Test {
    private static int checks;
    private static void yes(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int h=80;h<=1080;h+=5)for(int mode=0;mode<=2;mode++){
            for(double scale:new double[]{.5,.75,1,1.25,1.5,1.75,2})for(boolean audit:new boolean[]{false,true}){
                int effective=FlightHudLayout.effectiveMode(mode,h,scale,audit);
                yes(effective<=mode,"must never increase verbosity");
                int row=Math.max(11,(int)Math.ceil(13*scale));
                int available=Math.max(0,h-12);
                if(effective==1)yes(available>=18+7*row,"middle panel must fit");
                if(effective==2)yes(available>=18+(audit?10:9)*row,"telemetry panel must fit");
                for(int w:new int[]{100,160,320,480,1920}){
                    var layout=FlightHudLayout.calculate(w,h,effective,scale);
                    yes(layout.telemetry().onScreen(w,h),"telemetry bounds");
                    yes(layout.cue().onScreen(w,h),"cue bounds");
                    yes(layout.systems()==null||layout.systems().onScreen(w,h),"system bounds");
                    if(layout.systems()!=null)yes(layout.telemetry().x()+layout.telemetry().width()<layout.systems().x(),"panel overlap");
                }
            }
        }
        System.out.println("PASS HudDensityV41Test: "+checks+" checks");
    }
}
