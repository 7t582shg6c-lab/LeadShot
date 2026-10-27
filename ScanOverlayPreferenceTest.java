package dev.leadshot;
import java.nio.file.*;
public final class ScanOverlayPreferenceTest {
    public static void main(String[] args)throws Exception{
        Path p=Files.createTempFile("leadshot-overlay-test-",".properties");Files.delete(p);
        int tests=0;
        if(ScanOverlayPreference.read(p)!=1)throw new AssertionError();tests++;
        for(int i=0;i<3;i++){
            ScanOverlayPreference.write(p,i);
            if(ScanOverlayPreference.read(p)!=i || ScanOverlayPreference.next(i)!=(i+1)%3)throw new AssertionError();tests++;
        }
        Files.writeString(p,"scanOverlay=bad\n");
        if(ScanOverlayPreference.read(p)!=1)throw new AssertionError();tests++;
        Files.deleteIfExists(p);
        System.out.println("PASS v0.43 overlay persistence: "+tests);
    }
}
