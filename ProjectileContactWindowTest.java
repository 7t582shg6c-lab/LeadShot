package dev.leadshot;

import java.util.Random;

/** Analytic + random continuous collision checks. Independent of Minecraft APIs. */
public final class ProjectileContactWindowTest {
    private static long checks;
    private static final ProjectileContactWindow.Vec ZERO = v(0,0,0);
    private static ProjectileContactWindow.Vec v(double x,double y,double z) {return new ProjectileContactWindow.Vec(x,y,z);}
    private static void yes(boolean result,String reason) {checks++;if(!result)throw new AssertionError(reason);}
    private static void near(double a,double b,double tol,String reason) {
        yes(Double.isFinite(a)&&Math.abs(a-b)<=tol,reason+" observed="+a+" expected="+b);
    }
    public static void main(String[] args) {
        var h=v(.5,.5,.5);
        var center=ZERO;
        var crossed=ProjectileContactWindow.relativeSegment(v(-2,0,0),v(2,0,0),center,center,h,0);
        yes(crossed.intersects(),"through AABB");
        near(crossed.first(),.375,1e-12,"near face");
        near(crossed.last(),.625,1e-12,"far face");
        yes(!ProjectileContactWindow.relativeSegment(v(-2,1,0),v(2,1,0),center,center,h,0).intersects(),"parallel miss");
        yes(ProjectileContactWindow.relativeSegment(v(-2,.5,0),v(2,.5,0),center,center,h,0).intersects(),"edge touch");
        yes(!ProjectileContactWindow.relativeSegment(v(-2,.51,0),v(2,.51,0),center,center,h,0).intersects(),"above box");
        var stationary=ProjectileContactWindow.relativeSegment(ZERO,ZERO,center,center,h,.1);
        yes(stationary.intersects(),"stationary interior");
        near(stationary.first(),0,1e-12,"stationary entry");
        near(stationary.last(),1,1e-12,"stationary exit");
        // The projectile and the target move equally fast: relative gap stays fixed.
        yes(!ProjectileContactWindow.relativeSegment(v(-2,0,0),v(-1,0,0),center,v(1,0,0),h,0).intersects(),"co-moving miss");
        // Target travels toward the stationary projectile within one tick.
        var moving=ProjectileContactWindow.relativeSegment(ZERO,ZERO,v(2,0,0),v(-2,0,0),h,0);
        yes(moving.intersects(),"target moving across stationary projectile");
        near(moving.first(),.375,1e-12,"moving enters");
        var traced=ProjectileContactWindow.earliest(ZERO,v(0,0,3),v(0,0,6),ZERO,h,.1,5);
        yes(traced.intersects(),"simple projectile contact");
        yes(traced.tick()>1 && traced.tick()<3,"correct contact time");
        yes(!ProjectileContactWindow.earliest(ZERO,v(0,0,3),v(20,0,6),ZERO,h,.1,5).intersects(),"lateral miss");
        yes(!ProjectileContactWindow.earliest(ZERO,ZERO,ZERO,ZERO,h,0,121).intersects(),"max horizon");
        yes(!ProjectileContactWindow.relativeSegment(v(Double.NaN,0,0),ZERO,ZERO,ZERO,h,0).intersects(),"nan");
        yes(!ProjectileContactWindow.relativeSegment(ZERO,ZERO,ZERO,ZERO,v(0,.5,.5),0).intersects(),"invalid size");
        yes(!ProjectileContactWindow.relativeSegment(ZERO,ZERO,ZERO,ZERO,h,-1).intersects(),"invalid radius");
        Random random=new Random(20261027L);
        for(int k=0;k<9000;k++) {
            var a=v(random.nextDouble()*8-4,random.nextDouble()*8-4,random.nextDouble()*8-4);
            var d=v(random.nextDouble()*8-4,random.nextDouble()*8-4,random.nextDouble()*8-4);
            var t=v(random.nextDouble()*8-4,random.nextDouble()*8-4,random.nextDouble()*8-4);
            var tv=v(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3);
            var extents=v(.25+random.nextDouble()*1.1,.25+random.nextDouble()*1.1,.25+random.nextDouble()*1.1);
            var result=ProjectileContactWindow.relativeSegment(a,a.add(d),t,t.add(tv),extents,.10);
            // Each sampled relative position strictly inside expanded AABB must be
            // contained in an analytic interval. Discretization can miss thin hits,
            // but the analytic method must never miss a sampled hit.
            for(int j=0;j<=20;j++) {
                double f=j/20.0;
                var rel=a.add(d.scale(f)).subtract(t.add(tv.scale(f)));
                boolean inside=Math.abs(rel.x())<=extents.x()+.1-1e-10
                        && Math.abs(rel.y())<=extents.y()+.1-1e-10
                        && Math.abs(rel.z())<=extents.z()+.1-1e-10;
                if (inside) {
                    yes(result.intersects(),"sampled collision must not be missed trial="+k);
                    yes(f>=result.first()-1e-10&&f<=result.last()+1e-10,"window contains sampled contact");
                }
            }
            // Relative swap symmetry of point-vs-box for the same-time segment.
            var swapped=ProjectileContactWindow.relativeSegment(t,t.add(tv),a,a.add(d),extents,.1);
            yes(result.intersects()==swapped.intersects(),"symmetry");
            if(result.intersects()){
                near(result.first(),swapped.first(),1e-10,"entry symmetry");
                near(result.last(),swapped.last(),1e-10,"exit symmetry");
            }
        }
        System.out.println("PASS ProjectileContactWindowTest "+checks+" assertions / 9000 randomized trials");
    }
}
