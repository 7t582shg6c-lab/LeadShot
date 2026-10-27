package dev.leadshot;

/**
 * Short-horizon approximate Elytra motion model, NO Minecraft dependencies.
 * Adapted conceptually from the historical Java Edition gliding recurrence;
 * NOT verified against Minecraft 26.3, rocket boost, fluid, or collisions.
 * Used only to compare alternative camera rotations (never to infer a server hit).
 */
public final class GlideDynamicsV50 {
    private GlideDynamicsV50() {}
    public record State(CoreBallistics.Vec position,CoreBallistics.Vec velocity) {}
    public static State project(CoreBallistics.Vec p,CoreBallistics.Vec v,
            double yawDegrees,double pitchDegrees,int ticks) {
        if(p==null||v==null||!p.finite()||!v.finite()
                ||!Double.isFinite(yawDegrees)||!Double.isFinite(pitchDegrees)
                ||ticks<0||ticks>8)return null;
        double yaw=Math.toRadians(yawDegrees),pitch=Math.toRadians(pitchDegrees);
        double cos=Math.cos(pitch),sin=Math.sin(pitch),c2=cos*cos;
        // Minecraft rotation convention: positive YRot turns to the left (-X).
        double lookX=-Math.sin(yaw)*cos,lookZ=Math.cos(yaw)*cos;
        CoreBallistics.Vec pos=p,vel=v;
        for(int t=0;t<ticks;t++){
            double x=vel.x(),y=vel.y(),z=vel.z();
            double horizontal=Math.hypot(x,z);
            y += -.08+.06*c2;
            if(y<0 && cos>1e-7){
                double lift=-y*.1*c2;
                y+=lift;
                x+=lookX*lift/cos;
                z+=lookZ*lift/cos;
            }
            if(pitch<0 && cos>1e-7){
                double climb=horizontal*(-sin)*.04;
                y+=climb*3.5;
                x-=lookX*climb/cos;
                z-=lookZ*climb/cos;
            }
            if(cos>1e-7){
                x+=(lookX/cos*horizontal-x)*.1;
                z+=(lookZ/cos*horizontal-z)*.1;
            }
            vel=new CoreBallistics.Vec(x*.99,y*.98,z*.99);
            pos=pos.add(vel);
            if(!pos.finite()||!vel.finite())return null;
        }
        return new State(pos,vel);
    }
}
