package dev.leadshot;
import java.util.Random;
public final class ScreenLineClipperTest {
    private static int count;
    private static void yes(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        yes(ScreenLineClipper.clip(-9,-9,-1,-1,100,100)==null,"fully outside");
        yes(ScreenLineClipper.clip(0,0,99,99,100,100)!=null,"diagonal");
        var horizontal=ScreenLineClipper.clip(-100,20,300,20,100,100);
        yes(horizontal!=null&&horizontal.x0()==0&&horizontal.x1()==99,"horizontal clipping");
        var vertical=ScreenLineClipper.clip(20,-99,20,300,100,100);
        yes(vertical!=null&&vertical.y0()==0&&vertical.y1()==99,"vertical clipping");
        Random rng=new Random(2039);
        for(int i=0;i<120000;i++){
            int w=1+rng.nextInt(1024),h=1+rng.nextInt(1024);
            int x0=rng.nextInt(8001)-4000,y0=rng.nextInt(8001)-4000;
            int x1=rng.nextInt(8001)-4000,y1=rng.nextInt(8001)-4000;
            var clip=ScreenLineClipper.clip(x0,y0,x1,y1,w,h);
            if(clip!=null){
                yes(clip.x0()>=0&&clip.y0()>=0&&clip.x0()<w&&clip.y0()<h,"first end in bounds");
                yes(clip.x1()>=0&&clip.y1()>=0&&clip.x1()<w&&clip.y1()<h,"second end in bounds");
            }
            if(x0>=0&&y0>=0&&x0<w&&y0<h&&x1>=0&&y1>=0&&x1<w&&y1<h){
                yes(clip!=null&&clip.x0()==x0&&clip.y0()==y0&&clip.x1()==x1&&clip.y1()==y1,"identity in viewport");
            }
        }
        System.out.println("PASS ScreenLineClipper 0.39: "+count+" assertions");
    }
}
