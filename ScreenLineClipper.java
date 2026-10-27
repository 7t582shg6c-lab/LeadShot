package dev.leadshot;

/** Allocation-light viewport culling before expensive projected-HUD line rasterization. */
public final class ScreenLineClipper {
    private ScreenLineClipper() {}
    public record Segment(int x0,int y0,int x1,int y1) {}
    private static int code(double x,double y,int w,int h){
        int c=0;
        if(x<0)c|=1;else if(x>w-1)c|=2;
        if(y<0)c|=4;else if(y>h-1)c|=8;
        return c;
    }
    public static Segment clip(int x0,int y0,int x1,int y1,int width,int height){
        if(width<=0||height<=0)return null;
        double ax=x0,ay=y0,bx=x1,by=y1;
        int ca=code(ax,ay,width,height),cb=code(bx,by,width,height);
        for(int tries=0;tries<12;tries++){
            if((ca|cb)==0)return new Segment((int)Math.round(ax),(int)Math.round(ay),
                    (int)Math.round(bx),(int)Math.round(by));
            if((ca&cb)!=0)return null;
            int outside=ca!=0?ca:cb;
            double x,y;
            if((outside&8)!=0){
                if(by==ay)return null;
                x=ax+(bx-ax)*((height-1-ay)/(by-ay));y=height-1;
            }else if((outside&4)!=0){
                if(by==ay)return null;
                x=ax+(bx-ax)*((0-ay)/(by-ay));y=0;
            }else if((outside&2)!=0){
                if(bx==ax)return null;
                x=width-1;y=ay+(by-ay)*((width-1-ax)/(bx-ax));
            }else{
                if(bx==ax)return null;
                x=0;y=ay+(by-ay)*((0-ax)/(bx-ax));
            }
            if(!Double.isFinite(x)||!Double.isFinite(y))return null;
            if(outside==ca){ax=x;ay=y;ca=code(ax,ay,width,height);}
            else{bx=x;by=y;cb=code(bx,by,width,height);}
        }
        return null;
    }
}
