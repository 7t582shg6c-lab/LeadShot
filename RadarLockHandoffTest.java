package dev.leadshot;
import java.util.List;
import java.util.UUID;
public final class RadarLockHandoffTest {
 static int n;static void ok(boolean b){if(!b)throw new AssertionError(n);n++;}
 public static void main(String[] args){
    UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    ok(RadarLockHandoff.aliveNearby(a,List.of(a,b,c)));
    ok(RadarLockHandoff.aliveNearby(b,List.of(a,b,c)));
    ok(!RadarLockHandoff.aliveNearby(b,List.of(a,c))); // killed: hand off now
    ok(!RadarLockHandoff.aliveNearby(null,List.of(a,c)));
    ok(!RadarLockHandoff.aliveNearby(a,List.of()));
    // A live but occluded target remains in nearby living UUIDs and stays locked.
    for(int t=0;t<100;t++)ok(RadarLockHandoff.aliveNearby(a,List.of(a,c)));
    System.out.println("PASS RadarLockHandoffTest "+n+" assertions");
 }
}
