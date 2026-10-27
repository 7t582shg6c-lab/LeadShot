package dev.leadshot;
/** Tracking may steer when a visible geometric solution exists.
 * Full three-model agreement is intentionally reserved for READY/release.
 */
public final class AssistEligibilityV43 {
    private AssistEligibilityV43() {}
    public static boolean maySteer(boolean enabled,boolean enemy,boolean geometricContact,
                                   boolean checkedClear,boolean lineOfSightOnly,boolean screenClosed){
        return enabled&&enemy&&geometricContact&&checkedClear&&lineOfSightOnly&&screenClosed;
    }
}
