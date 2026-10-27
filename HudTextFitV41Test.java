package dev.leadshot;
import java.util.Random;
public final class HudTextFitV41Test {
    private static int checks;
    private static void yes(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    private static int virtualWidth(String s){return s.codePoints().map(cp->cp>127?10:6).sum();}
    public static void main(String[] args){
        String[] samples={"LEADSHOT  // FCS-41", "TRACK: STT LOCK / WIDE SCAN", "敵対モブ: ゾンビ", "INTERCEPT [PRED]", "✅ READY / 発射支援", "", "FIRE CUE ESTIMATED ONLY"};
        for(double scale:new double[]{.5,.75,1.0,1.25,1.5,1.75,2.0}){
            for(int width=0;width<=1200;width+=3){
                for(String value:samples){
                    String fitted=FlightHudLayout.fitText(value,width,scale);
                    yes(virtualWidth(fitted)<=Math.floor(width/scale)+.001,"on-screen width");
                    yes(fitted.codePoints().count()<=value.codePoints().count()+1,"bounded text length");
                    if(virtualWidth(value)<=Math.floor(width/scale))yes(fitted.equals(value),"no needless truncation");
                }
            }
        }
        yes(FlightHudLayout.fitText(null,100,1).isEmpty(),"null safe");
        yes(FlightHudLayout.fitText("abc",100,0).isEmpty(),"zero scale safe");
        yes(FlightHudLayout.fitText("abc",100,Double.NaN).isEmpty(),"NaN safe");
        System.out.println("PASS HudTextFitV41Test: "+checks+" checks");
    }
}
