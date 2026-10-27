package dev.leadshot;
public final class AssistEligibilityV43Test {
 public static void main(String[] args){
  int checks=0;
  for(int mask=0;mask<64;mask++){
   boolean[] b=new boolean[6];for(int i=0;i<6;i++)b[i]=(mask&(1<<i))!=0;
   boolean result=AssistEligibilityV43.maySteer(b[0],b[1],b[2],b[3],b[4],b[5]);
   if(result!=(mask==63))throw new AssertionError("unsafe steer: "+mask);
   checks++;
  }
  System.out.println("PASS v0.43 aim assist eligibility: "+checks);
 }
}
