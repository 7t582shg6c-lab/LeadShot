package dev.leadshot;
import java.nio.file.*;
public final class GuideControlsTest {
  public static void main(String[] args)throws Exception{
    Path file=Files.createTempDirectory("guide-controls").resolve("c.properties");
    int n=0;
    try{
      if(!GuideControls.read(file).equals(GuideControls.defaults()))throw new AssertionError("default");n++;
      for(int i=-100;i<500;i++){
        GuideControls options=new GuideControls(i,i,i,i,i,i%2==0,i%3==0,true,i%5==0).valid();
        options.save(file);
        if(!GuideControls.read(file).equals(options))throw new AssertionError("roundtrip "+i);n++;
        if((options.tint(0xFF22CC55)>>>24)!=options.opacity())throw new AssertionError("alpha "+i);n++;
      }
      Files.writeString(file,"radius=invalid\ndepth=-2\nopacity=99999\n");
      GuideControls got=GuideControls.read(file);
      if(got.radiusDegrees()!=24||got.depthBlocks()!=8||got.opacity()!=255)throw new AssertionError("bad values");n++;
      System.out.println("PASS GuideControlsTest "+n+" assertions");
    }finally{Files.deleteIfExists(file);Files.deleteIfExists(file.getParent());}
  }
}
