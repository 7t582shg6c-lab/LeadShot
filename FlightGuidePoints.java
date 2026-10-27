package dev.leadshot;
import java.util.ArrayList;
import java.util.List;

/** Bounded trajectory points, evaluated in the same tick order as contact audit. */
public final class FlightGuidePoints {
    private FlightGuidePoints(){}
    public static List<CoreBallistics.Vec> sample(CoreBallistics.Vec origin,CoreBallistics.Vec initial,int ticks){
        if(origin==null||initial==null||!origin.finite()||!initial.finite()||ticks<1||ticks>120)return List.of();
        int count=Math.min(12,ticks);
        List<CoreBallistics.Vec> result=new ArrayList<>(count);
        for(int i=1;i<=count;i++){
            int n=Math.max(1,(int)Math.round(ticks*i/(double)count));
            CoreBallistics.Vec p=CoreBallistics.simulate(origin,initial,n);
            if(p==null)return List.of();
            result.add(p);
        }
        return List.copyOf(result);
    }
}
