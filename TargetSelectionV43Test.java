package dev.leadshot;
public final class TargetSelectionV43Test {
    public static void main(String[] args){
        int tests=0;
        for(int distance=4;distance<100;distance+=3){
            double center=TargetSelectionV43.score(.1,.2,.8,.4,distance);
            double edge=TargetSelectionV43.score(7,5,2,3,4);
            if(!(center<edge))throw new AssertionError("center must dominate nearby peripheral mob");
            tests++;
        }
        if(!(TargetSelectionV43.radarScore(.1,35)<TargetSelectionV43.radarScore(10,2)))
            throw new AssertionError("radar must capture centered contact");tests++;
        if(!TargetSelectionV43.preferNew(4,.6,5,.1))throw new AssertionError("new central mob");tests++;
        if(TargetSelectionV43.preferNew(2,1.9,1.5,1.4))throw new AssertionError("jittering near equal scores");tests++;
        if(!Double.isInfinite(TargetSelectionV43.score(Double.NaN,0,0,0,5)))throw new AssertionError();tests++;
        System.out.println("PASS v0.43 central aiming priority: "+tests);
    }
}
