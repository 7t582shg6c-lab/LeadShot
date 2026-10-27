package dev.leadshot;
import java.nio.file.*;
public final class PreferencesTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("leadshot-opts");Path file=dir.resolve("config.properties");
        try{
            LeadShotOptions d=LeadShotOptions.read(file);
            if(!d.equals(LeadShotOptions.defaults()))throw new AssertionError("default");
            for(int i=-250;i<=300;i++){
                var v=new LeadShotOptions(false,true,true,false,true,false,true,i,i,i,i%2==0);
                v.write(file);
                var observed=LeadShotOptions.read(file);
                if(observed.category()<0||observed.category()>2||observed.mode()<0||observed.mode()>2||observed.fov()<50||observed.fov()>110)
                    throw new AssertionError("range");
                if(observed.preview()||!observed.shadowAudit()||observed.blockCheck()!=(i%2==0))throw new AssertionError("round trip");
            }
            Files.writeString(file,"hud=nonsense\npreview=false\nfov=garbage\ncategory=9999\n");
            var fallback=LeadShotOptions.read(file);
            if(!fallback.hud()||fallback.preview()||fallback.fov()!=70||fallback.category()!=2)throw new AssertionError("fallback");
            System.out.println("PASS PreferencesTest 1104 checks");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
    }
}
