package dev.leadshot;
import java.util.*;
public final class TrackingBudgetTest {
    private static int checks;
    private static void check(boolean condition,String reason){ checks++;if(!condition)throw new AssertionError(reason); }
    public static void main(String[] args){
        for(int size=0;size<=400;size++){
            List<Integer> ordered=new ArrayList<>();for(int i=0;i<size;i++)ordered.add(i);
            for(int cap: new int[]{0,1,12,32,64,100}){
                for(int pinned: new int[]{-1,0,3,12,63,64,100,399}){
                    final int pin=pinned;
                    var selected=TrackingBudget.choose(ordered,n->n==pin,cap);
                    int len=Math.min(size,cap);
                    check(selected.size()==len,"fixed bound");
                    check(new HashSet<>(selected).size()==selected.size(),"no duplicate");
                    boolean exists=pin>=0 && pin<size && cap>0;
                    if(exists)check(selected.contains(pin),"pinned kept even when crowded: size="+size+", cap="+cap+", pin="+pin);
                    if(!exists){for(int i=0;i<len;i++)check(selected.get(i)==i,"nearest order");}
                    else if(pin>=len){for(int i=0;i<len-1;i++)check(selected.get(i)==i,"nearest n-1");}
                    else {for(int i=0;i<len;i++)check(selected.get(i)==i,"stable when already covered");}
                }
            }
        }
        check(TrackingBudget.choose(null,n->true,64).isEmpty(),"null input");
        check(TrackingBudget.choose(List.of(1),null,64).isEmpty(),"null predicate");
        System.out.println("PASS TrackingBudgetTest: "+checks+" checks");
    }
}
