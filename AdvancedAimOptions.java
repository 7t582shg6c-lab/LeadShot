package dev.leadshot;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Properties;

/** Persisted advanced aim options; existing guide files remain backward-compatible. */
public record AdvancedAimOptions(int latencyTenths, boolean allowPlayerAutomation) {
    public static AdvancedAimOptions defaults(){return new AdvancedAimOptions(8,false);}
    public AdvancedAimOptions valid(){return new AdvancedAimOptions(Math.max(0,Math.min(40,latencyTenths)),allowPlayerAutomation);}
    public double latencyTicks(){return valid().latencyTenths()/10.0;}
    public AdvancedAimOptions withLatency(int t){return new AdvancedAimOptions(t,allowPlayerAutomation).valid();}
    public AdvancedAimOptions withPlayers(boolean enabled){return new AdvancedAimOptions(latencyTenths,enabled).valid();}
    public static AdvancedAimOptions read(Path file){
        if(!Files.isRegularFile(file))return defaults();
        try(InputStream in=Files.newInputStream(file)){
            Properties p=new Properties();p.load(in);
            return new AdvancedAimOptions(Integer.parseInt(p.getProperty("latencyTenths","8")),
                    Boolean.parseBoolean(p.getProperty("playerAutomation","false"))).valid();
        }catch(Exception ex){return defaults();}
    }
    public void write(Path file)throws java.io.IOException{
        Path path=file.toAbsolutePath();Files.createDirectories(path.getParent());
        Properties p=new Properties();p.setProperty("latencyTenths",String.valueOf(valid().latencyTenths()));
        p.setProperty("playerAutomation",String.valueOf(allowPlayerAutomation));
        Path tmp=Files.createTempFile(path.getParent(),".leadshot-aim-",".tmp");
        try{
            try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"LeadShot flight aim adjustment");}
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
