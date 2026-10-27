package dev.leadshot;
public final class AssistedReleaseGateTest{
 private static int n;
 private static void check(boolean good){if(!good)throw new AssertionError("test "+n);n++;}
 public static void main(String[] a){
  for(int mask=0;mask<256;mask++){
   boolean[] b=new boolean[8];for(int i=0;i<b.length;i++)b[i]=(mask&(1<<i))!=0;
   var g=new AssistedReleaseGate();
   check(g.allow(100,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7])==(mask==255));
  }
  var g=new AssistedReleaseGate();
  check(g.allow(100,true,true,true,true,true,true,true,true));
  check(!g.allow(101,true,true,true,true,true,true,true,true));
  check(!g.allow(111,true,true,true,true,true,true,true,true));
  check(g.allow(112,true,true,true,true,true,true,true,true));
  check(!g.allow(100,true,true,true,true,true,true,true,true));
  g.reset();check(g.allow(101,true,true,true,true,true,true,true,true));
  // A non-READY click must not be queued and a later READY tick without click must not shoot.
  g.reset();check(!g.allow(5,true,true,true,true,true,true,false,true));
  check(!g.allow(6,false,true,true,true,true,true,true,true));
  check(!g.allow(7,true,true,false,true,true,true,true,true));
  check(!g.allow(8,true,true,true,true,true,false,true,true));
  System.out.println("PASS AssistedReleaseGateTest "+n+" assertions");
 }
}
