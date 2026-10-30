import java.util.Arrays;
import com.cinema65.oneclick.CandidateRanker;
public class EdgeSafetyRegressionTest {
 static void test(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  int w=256,h=160;
  int[] original=new int[w*h],smooth=new int[w*h],hard=new int[w*h];
  Arrays.fill(original,0xff777777);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   // Smooth physically plausible light rise from the right.
   int v=(int)(119+19*x/(double)(w-1));
   smooth[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
   // Artificially pasted foreground strip with a hard vertical change.
   int z=x<w/2?115:156;
   hard[y*w+x]=0xff000000|(z<<16)|(z<<8)|z;
  }
  CandidateRanker.Metrics sm=new CandidateRanker.Metrics(original,smooth,w,h);
  CandidateRanker.Metrics ha=new CandidateRanker.Metrics(original,hard,w,h);
  test(sm.inventedEdgeRate==0,"Natural soft gradients must not be labeled a hard seam");
  test(ha.inventedEdgeRate>0.001,"Pasted cut must produce artificial edge warning");
  CandidateRanker.Metrics[] m={sm,ha,sm};
  CandidateRanker.Choice c=CandidateRanker.select(0,new double[]{5.6,6.0,5.65},new double[]{5.5,5.5,5.5},m,new double[3]);
  test(c.selected!=1,"Suspicious pasted edge must not be selected on a small NIMA lead");
  CandidateRanker.Metrics oldApi=new CandidateRanker.Metrics(original,hard);
  test(oldApi.inventedEdgeRate==0,"Legacy dimensionless damage check must safely omit edge metric");
  boolean mismatch=false;
  try {new CandidateRanker.Metrics(original,hard,w+1,h);}catch(IllegalArgumentException ex){mismatch=true;}
  test(mismatch,"Invalid aspect dimensions should be rejected");
  System.out.println("PASS edge safety: soft light preserved; artificial mask seam detected; scorer discount verified");
 }
}