package dev.leadshot;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

public class GuideFrameSmootherTest {
    private static long assertions;
    private static void ok(boolean cond, String msg) { assertions++; if (!cond) throw new AssertionError(msg); }
    private static void near(double x, double y, double eps, String msg) {
        ok(Double.isFinite(x) && Math.abs(x-y) <= eps, msg+" "+x+" != "+y);
    }
    static CoreBallistics.Vec v(double x){return new CoreBallistics.Vec(x,1,7);}
    static GuideFrameSmoother.Frame f(double yaw,double x) {
        return new GuideFrameSmoother.Frame(yaw,12,v(x),v(x),v(x),12);
    }
    public static void main(String[] args) throws Exception {
        var s=new GuideFrameSmoother();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        ok(s.sample(100L,2)==null,"empty");
        s.observe(a,f(179,0));
        near(s.sample(1_000_000_000L,2).yaw(),179,1e-10,"initial snap");
        s.observe(a,f(-179,5));
        var sm=s.sample(1_016_666_667L,2);
        ok(sm.future().x()>0 && sm.future().x()<5,"world point smooth");
        ok(Math.abs(CoreBallistics.wrap(sm.yaw()-180))<1,"yaw shortest arc");
        var after=s.sample(1_033_333_334L,2);
        ok(after.future().x()>sm.future().x(),"moving toward target");
        s.observe(b,f(15,90));
        near(s.sample(1_050_000_001L,2).future().x(),90,1e-12,"new target no ghost");
        near(s.sample(1_050_000_001L,2).yaw(),15,1e-12,"new target angular snap");
        s.observe(b,f(20,50));
        var off=s.sample(1_066_666_667L,0);
        near(off.future().x(),50,1e-12,"OFF immediate");
        s.observe(b,f(30,120));
        s.sample(1_083_333_333L,2);
        var resume=s.sample(2_000_000_000L,2);
        near(resume.future().x(),120,1e-12,"resume no stale ghost");
        // Teleported mob with the SAME UUID must not leave a streak across the screen.
        s.observe(b,f(40,500));
        near(s.sample(2_010_000_000L,2).future().x(),500,1e-12,"teleport snap");
        s.observe(null,f(10,10));
        ok(s.sample(2_016_000_000L,2)==null,"reset on missing target");
        s.observe(a,new GuideFrameSmoother.Frame(Double.NaN,0,v(0),v(0),v(0),5));
        ok(s.sample(3_000_000_000L,2)==null,"reject invalid");
        s.observe(a,f(10,2));s.sample(3_000_000_000L,2);
        s.observe(a,f(50,17));
        // frame-rate invariance: identical elapsed time and target have similar smoothing
        var fast=new GuideFrameSmoother();fast.observe(a,f(10,2));fast.sample(3_000_000_000L,2);
        fast.observe(a,f(50,17));
        var slow=new GuideFrameSmoother();slow.observe(a,f(10,2));slow.sample(3_000_000_000L,2);
        slow.observe(a,f(50,17));
        for(int i=1;i<=60;i++){fast.sample(3_000_000_000L+i*5_000_000L,2);}
        for(int i=1;i<=15;i++){slow.sample(3_000_000_000L+i*20_000_000L,2);}
        near(fast.sample(3_300_000_000L,2).future().x(),slow.sample(3_300_000_000L,2).future().x(),1e-9,"fps independence");
        // step not exceeding error: monotone and finite at 240fps + high motion
        for(int level=1;level<=3;level++){
            var test=new GuideFrameSmoother();test.observe(a,f(-150,-100));
            test.sample(10_000_000_000L,level);
            test.observe(a,f(150,100));
            double last=-100;
            for(int i=1;i<=3000;i++){
                var frame=test.sample(10_000_000_000L+4_166_667L*i,level);
                ok(frame.finite(),"finite");
                ok(frame.future().x()>=last-1e-9 && frame.future().x()<=100+1e-8,"monotone");
                last=frame.future().x();
            }
        }
        // v0.33 options file without smoothing remains valid; persist & clamp new setting
        Path dir=Files.createTempDirectory("leadshot-smooth-tests-");Path file=dir.resolve("prefs.properties");
        try{
            Files.writeString(file,"radius=28\ndepth=40\nopacity=210\n");
            ok(GuideControls.read(file).smoothing()==2,"old settings compatible");
            for(int n=-30;n<=50;n++){
                var g=new GuideControls(24,48,2,210,0,true,true,true,false,n).valid();
                ok(g.smoothing()>=0&&g.smoothing()<=3,"clamp");
                g.save(file);ok(GuideControls.read(file).equals(g),"save/restore");
            }
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
        System.out.println("PASS GuideFrameSmootherTest: "+assertions+" assertions");
    }
}
