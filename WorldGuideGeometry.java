package dev.leadshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable world-coordinate line segments for the eventual Fabric 26.3 level
 * renderer. NO rendering, shaders or world references are held in this class.
 * Consumers must first validate the actual level-extraction/draw API.
 */
public final class WorldGuideGeometry {
    private WorldGuideGeometry() { }
    public enum Role { AIM, FUTURE, TARGET_BOX, PROJECTILE_PATH }
    public record Line(CoreBallistics.Vec a, CoreBallistics.Vec b, Role role) { }
    public record Frame(List<Line> lines) {
        public static Frame empty() { return new Frame(List.of()); }
        public Frame { lines = List.copyOf(lines); }
        public int count(Role role) {
            int n = 0;
            for (Line line : lines) if (line.role() == role) n++;
            return n;
        }
    }
    private static final int RING_SEGMENTS = 16;

    public static Frame build(CoreBallistics.Vec eye, double aimYaw, double aimPitch,
            double cameraYaw, double cameraPitch, CoreBallistics.Vec future,
            CoreBallistics.Vec half, CoreBallistics.Vec velocity, int ticks,
            boolean includeTrajectory) {
        if (!valid(eye) || !valid(future) || !valid(half) || !valid(velocity)
                || !Double.isFinite(aimYaw) || !Double.isFinite(aimPitch)
                || !Double.isFinite(cameraYaw) || !Double.isFinite(cameraPitch)
                || ticks < 1 || ticks > CoreBallistics.MAX_TICKS
                || half.x() <= 0 || half.y() <= 0 || half.z() <= 0
                || half.x() > 16 || half.y() > 16 || half.z() > 16
                || future.minus(eye).length() > 256) return Frame.empty();
        CoreBallistics.Vec aim = AimProjection.dir(aimYaw, aimPitch);
        if (!valid(aim)) return Frame.empty();
        CoreBallistics.Vec aimCenter = eye.add(aim.times(10.0));
        List<Line> lines = new ArrayList<>(64);
        ring(lines,aimCenter,cameraYaw,cameraPitch,.28,Role.AIM);
        double targetRadius = Math.min(2.0,Math.max(.17,Math.max(half.x(),half.y())));
        ring(lines,future,cameraYaw,cameraPitch,targetRadius,Role.FUTURE);
        box(lines,future,half);
        if (includeTrajectory) {
            List<CoreBallistics.Vec> path = FlightGuidePoints.sample(eye,velocity,ticks);
            CoreBallistics.Vec previous = eye;
            for (CoreBallistics.Vec at : path) {
                add(lines,previous,at,Role.PROJECTILE_PATH);
                previous = at;
            }
        }
        return new Frame(lines);
    }
    private static void ring(List<Line> lines, CoreBallistics.Vec center,
                             double cameraYaw,double cameraPitch,double radius,Role role) {
        double yaw=Math.toRadians(cameraYaw),pitch=Math.toRadians(cameraPitch);
        CoreBallistics.Vec right = new CoreBallistics.Vec(-Math.cos(yaw),0,-Math.sin(yaw));
        CoreBallistics.Vec up = new CoreBallistics.Vec(-Math.sin(yaw)*Math.sin(pitch),
                Math.cos(pitch),Math.cos(yaw)*Math.sin(pitch));
        CoreBallistics.Vec previous = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = 2*Math.PI*i/RING_SEGMENTS;
            CoreBallistics.Vec at = center.add(right.times(radius*Math.cos(angle)))
                    .add(up.times(radius*Math.sin(angle)));
            if (previous != null) add(lines,previous,at,role);
            previous = at;
        }
    }
    private static void box(List<Line> lines,CoreBallistics.Vec center,CoreBallistics.Vec half) {
        CoreBallistics.Vec[] corners = new CoreBallistics.Vec[8];
        for (int i=0;i<8;i++) corners[i]=center.add(new CoreBallistics.Vec(
                (i&1)==0?-half.x():half.x(),
                (i&2)==0?-half.y():half.y(),
                (i&4)==0?-half.z():half.z()));
        for (int i=0;i<8;i++) for (int bit=1;bit<=4;bit*=2) if ((i&bit)==0)
            add(lines,corners[i],corners[i|bit],Role.TARGET_BOX);
    }
    private static void add(List<Line> lines,CoreBallistics.Vec a,CoreBallistics.Vec b,Role role) {
        if (valid(a) && valid(b) && b.minus(a).length() <= 256.0)
            lines.add(new Line(a,b,role));
    }
    private static boolean valid(CoreBallistics.Vec v) {
        return v != null && v.finite()
                && Math.max(Math.max(Math.abs(v.x()),Math.abs(v.y())),Math.abs(v.z())) < 30_000_000;
    }
}
