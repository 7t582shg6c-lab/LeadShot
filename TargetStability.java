package dev.leadshot;

/** Prefer current target unless another candidate is significantly better. */
public final class TargetStability {
    private TargetStability(){}
    public static boolean switchToNew(double previous,double replacement,boolean retainedValid){
        if(!Double.isFinite(replacement))return false;
        if(!retainedValid||!Double.isFinite(previous))return true;
        if(previous<=0)return false;
        return previous-replacement>1.2&&replacement<=previous*.88;
    }
}
