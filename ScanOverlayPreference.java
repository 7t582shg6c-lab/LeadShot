package dev.leadshot;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Properties;

/** Independent cosmetic option: OFF, visible contacts, all loaded contacts. */
public final class ScanOverlayPreference {
    public static final int OFF=0, VISIBLE=1, ALL=2;
    private ScanOverlayPreference() {}
    public static int next(int mode){return (clamp(mode)+1)%3;}
    public static int clamp(int mode){return Math.max(OFF,Math.min(ALL,mode));}
    public static String label(int mode){return switch(clamp(mode)){
        case OFF->"非表示";case VISIBLE->"視認可能のみ";default->"追跡対象すべて";};}
    public static int read(Path path){
        if(!Files.isRegularFile(path))return VISIBLE;
        try(InputStream in=Files.newInputStream(path)){
            Properties p=new Properties();p.load(in);
            return clamp(Integer.parseInt(p.getProperty("scanOverlay", "1")));
        }catch(Exception e){return VISIBLE;}
    }
    public static void write(Path path,int mode)throws java.io.IOException{
        Files.createDirectories(path.toAbsolutePath().getParent());
        Properties p=new Properties();p.setProperty("scanOverlay",String.valueOf(clamp(mode)));
        Path tmp=Files.createTempFile(path.toAbsolutePath().getParent(),".leadshot-scan-",".tmp");
        try{
            try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"LeadShot visual scan contacts");}
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
