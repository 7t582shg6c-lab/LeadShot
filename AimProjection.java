package dev.leadshot;

/** Finite and screen-clipped 3D vector projection for 2D HUD markers. */
public final class AimProjection {
    private AimProjection() {}
    public record Point(double x,double y,boolean visible,boolean inFront) {}
    public static CoreBallistics.Vec dir(double yaw,double pitch){
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new CoreBallistics.Vec(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
    }
    public static Point angle(double yaw,double pitch,double cy,double cp,int w,int h,double fov){
        return relative(dir(yaw,pitch),cy,cp,w,h,fov);
    }
    public static Point world(CoreBallistics.Vec origin,CoreBallistics.Vec target,double yaw,double pitch,int w,int h,double fov){
        return origin!=null&&target!=null?relative(target.minus(origin),yaw,pitch,w,h,fov):null;
    }
    public static Point relative(CoreBallistics.Vec v,double yaw,double pitch,int w,int h,double fov){
        if(v==null||!v.finite()||!Double.isFinite(yaw)||!Double.isFinite(pitch)||
           !Double.isFinite(fov)||fov<25||fov>120||w<32||h<32||v.length()<1e-8)return null;
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        CoreBallistics.Vec fw=dir(yaw,pitch);
        CoreBallistics.Vec right=new CoreBallistics.Vec(-Math.cos(y),0,-Math.sin(y));
        CoreBallistics.Vec up=new CoreBallistics.Vec(-Math.sin(y)*Math.sin(p),Math.cos(p),Math.cos(y)*Math.sin(p));
        double depth=dot(v,fw);
        if(depth<=1e-6)return new Point(w/2.0,h/2.0,false,false);
        double vt=Math.tan(Math.toRadians(fov)*.5),ht=vt*w/(double)h;
        double x=dot(v,right)/depth/ht,yScreen=-dot(v,up)/depth/vt;
        if(!Double.isFinite(x)||!Double.isFinite(yScreen))return null;
        boolean inside=Math.abs(x)<=.93&&Math.abs(yScreen)<=.90;
        x=Math.max(-.93,Math.min(.93,x));yScreen=Math.max(-.90,Math.min(.90,yScreen));
        return new Point((x+1)*w*.5,(yScreen+1)*h*.5,inside,true);
    }
    private static double dot(CoreBallistics.Vec a,CoreBallistics.Vec b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
}
