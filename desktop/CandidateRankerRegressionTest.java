import com.cinema65.oneclick.CandidateRanker;
import java.util.Arrays;
public class CandidateRankerRegressionTest {
 static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args){
  int[] original=new int[10000];Arrays.fill(original,0xff808080);
  int[] mild=original.clone(),bad=original.clone();Arrays.fill(bad,0xff000000);
  CandidateRanker.Metrics[] safe={new CandidateRanker.Metrics(original,mild),new CandidateRanker.Metrics(original,mild),new CandidateRanker.Metrics(original,mild)};
  CandidateRanker.Choice close=CandidateRanker.select(0,new double[]{5.71,5.76,5.72},new double[]{5.61,5.56,5.56},safe,new double[3]);
  ok(close.selected==0,"Tiny NIMA differences must not force treatment switch");
  CandidateRanker.Choice meaningful=CandidateRanker.select(0,new double[]{5.7,6.5,5.8},new double[]{5.6,6.0,5.6},safe,new double[3]);
  ok(meaningful.selected==1,"Meaningful improvement should switch");
  CandidateRanker.Metrics[] damage={new CandidateRanker.Metrics(original,mild),new CandidateRanker.Metrics(original,bad),new CandidateRanker.Metrics(original,mild)};
  CandidateRanker.Choice crushed=CandidateRanker.select(0,new double[]{5.7,6.5,5.8},new double[]{5.6,6.0,5.6},damage,new double[3]);
  ok(crushed.selected!=1,"Clipped image should be rejected");
  CandidateRanker.Choice missing=CandidateRanker.select(2,new double[]{Double.NaN,Double.NaN,Double.NaN},new double[]{Double.NaN,Double.NaN,Double.NaN},safe,new double[3]);
  ok(missing.selected==2,"NIMA failure must retain baseline");
  System.out.println("PASS: NIMA near-tie abstain, decisive win, clipping rejection, offline fallback");
 }
}