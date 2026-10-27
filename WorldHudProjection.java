package dev.leadshot;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Uses Minecraft's real camera/projection to match vanilla entity rendering. */
public final class WorldHudProjection {
    private WorldHudProjection() {}
    public static AimProjection.Point project(Minecraft mc,CoreBallistics.Vec world,int w,int h){
        if(mc==null||world==null||!world.finite())return null;
        // Vanilla returns normalized device coordinates from the current camera,
        // incorporating third person, FOV effects, and partial-tick camera pose.
        Vec3 ndc=mc.gameRenderer.projectPointToScreen(new Vec3(world.x(),world.y(),world.z()));
        if(ndc==null)return null;
        return normalized(ndc.x,ndc.y,ndc.z,w,h);
    }
    public static AimProjection.Point normalized(double x,double y,double z,int w,int h){
        if(w<32||h<32||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return null;
        boolean front=z>=-1 && z<=1;
        // Keep out-of-screen coordinates (unclamped) for accurate projected box extents.
        boolean visible=front && Math.abs(x)<=.98 && Math.abs(y)<=.98;
        return new AimProjection.Point((x+1)*w*.5,(1-y)*h*.5,visible,front);
    }
}
