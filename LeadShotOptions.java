package dev.leadshot;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Client-only local preferences; no entity names, coordinates or network data. */
public record LeadShotOptions(boolean hud,boolean guide,boolean trajectory,boolean preview,
      boolean linked,boolean calibration,boolean shadowAudit,int category,int mode,int fov,boolean blockCheck){
    public static LeadShotOptions defaults(){return new LeadShotOptions(true,true,true,true,true,false,false,0,1,70,true);}
    public LeadShotOptions sanitized(){return new LeadShotOptions(hud,guide,trajectory,preview,linked,
          calibration,shadowAudit,Math.max(0,Math.min(2,category)),Math.max(0,Math.min(2,mode)),
          Math.max(50,Math.min(110,fov)),blockCheck);}
    public static LeadShotOptions read(Path file)throws IOException{
        if(!Files.isRegularFile(file))return defaults();
        Properties p=new Properties();try(InputStream is=Files.newInputStream(file)){p.load(is);}
        LeadShotOptions d=defaults();
        return new LeadShotOptions(bool(p,"hud",d.hud()),bool(p,"guide",d.guide()),
            bool(p,"trajectory",d.trajectory()),bool(p,"preview",d.preview()),
            bool(p,"linked",d.linked()),bool(p,"calibration",d.calibration()),
            bool(p,"shadowAudit",d.shadowAudit()),integer(p,"category",d.category()),
            integer(p,"mode",d.mode()),integer(p,"fov",d.fov()),bool(p,"blockCheck",d.blockCheck())).sanitized();
    }
    public void write(Path file)throws IOException{
        Path absolute=file.toAbsolutePath();Files.createDirectories(absolute.getParent());
        LeadShotOptions o=sanitized();Properties p=new Properties();
        p.setProperty("hud",Boolean.toString(o.hud()));p.setProperty("guide",Boolean.toString(o.guide()));
        p.setProperty("trajectory",Boolean.toString(o.trajectory()));p.setProperty("preview",Boolean.toString(o.preview()));
        p.setProperty("linked",Boolean.toString(o.linked()));p.setProperty("calibration",Boolean.toString(o.calibration()));
        p.setProperty("shadowAudit",Boolean.toString(o.shadowAudit()));
        p.setProperty("blockCheck",Boolean.toString(o.blockCheck()));
        p.setProperty("category",Integer.toString(o.category()));p.setProperty("mode",Integer.toString(o.mode()));
        p.setProperty("fov",Integer.toString(o.fov()));
        Path temp=Files.createTempFile(absolute.getParent(),".leadshot-",".tmp");
        try{
            try(OutputStream os=Files.newOutputStream(temp)){p.store(os,"LeadShot Fabric 26.3 config");}
            try{Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static boolean bool(Properties p,String key,boolean fallback){String v=p.getProperty(key);return "true".equalsIgnoreCase(v)||"false".equalsIgnoreCase(v)?Boolean.parseBoolean(v):fallback;}
    private static int integer(Properties p,String key,int fallback){try{return Integer.parseInt(p.getProperty(key));}catch(Exception e){return fallback;}}
}
