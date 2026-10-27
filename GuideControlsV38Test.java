package dev.leadshot;
import java.nio.file.*;
import java.util.Properties;
import java.io.*;
public class GuideControlsV38Test {
    private static int checks;
    private static void ok(boolean yes,String m){checks++;if(!yes)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception {
        Path file=Files.createTempFile("leadshot-v038-config", ".properties");
        try{
            for(int mode=0;mode<4;mode++){
                GuideControls g=GuideControls.defaults().withComputation(mode).withAssist(true,true,3);
                g.save(file);
                GuideControls h=GuideControls.read(file);
                ok(h.computationMode()==mode,"mode save");
                ok(h.autoAim()&&h.assistedFire()&&h.assistSpeed()==3,"assist persistent");
                ok(h.withExtra(2,1,1).computationMode()==mode,"extra preserves compute");
                ok(h.withAssist(false,false,1).computationMode()==mode,"assist preserves compute");
            }
            Properties p=new Properties();
            try(InputStream in=Files.newInputStream(file)){p.load(in);}
            p.remove("computationMode");
            try(OutputStream out=Files.newOutputStream(file)){p.store(out,"old v37");}
            ok(GuideControls.read(file).computationMode()==PrecomputePolicy.DEFAULT,"old settings default");
            p.setProperty("computationMode","999");
            try(OutputStream out=Files.newOutputStream(file)){p.store(out,"invalid");}
            ok(GuideControls.read(file).computationMode()==3,"out-of-range sanitized");
            p.setProperty("computationMode","-7");
            try(OutputStream out=Files.newOutputStream(file)){p.store(out,"invalid");}
            ok(GuideControls.read(file).computationMode()==0,"negative sanitized");
        }finally{Files.deleteIfExists(file);}
        System.out.println("PASS GuideControlsV38Test: "+checks+" assertions");
    }
}
