import com.cinema65.oneclick.GenericSubjectGate;
/** A fragmented 320x320 proposal must not become automatically trusted after shrinking. */
public final class HighResMaskGateRegressionTest {
 public static void main(String[] args){
  int w=320,h=320;float[] full=new float[w*h];
  // One coherent central object plus many disconnected bright specks.
  for(int y=73;y<225;y++)for(int x=90;x<210;x++)full[y*w+x]=1;
  for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)
   if((x<83||x>225||y<83||y>206) && x%12<5 && y%12<5)full[y*w+x]=1;
  GenericSubjectGate.Review original=GenericSubjectGate.assess(full,w,h);
  if(original.usable)throw new AssertionError("Fragmented original falsely trusted");
  System.out.println("PASS high-resolution source reject: "+original.summary());
 }
}