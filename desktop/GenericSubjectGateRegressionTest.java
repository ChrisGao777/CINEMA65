import com.cinema65.oneclick.GenericSubjectGate;
import java.util.Arrays;
public final class GenericSubjectGateRegressionTest {
 static final int W=48,H=48;
 static float[] blank(){return new float[W*H];}
 static void rectangle(float[] a,int x0,int y0,int x1,int y1){for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)a[y*W+x]=.95f;}
 static void require(boolean x,String why){if(!x)throw new AssertionError(why);}
 public static void main(String[] args){
  float[] bounded=blank();rectangle(bounded,16,8,31,41);
  require(GenericSubjectGate.assess(bounded,W,H).usable,"clear contiguous object must be considered");
  float[] full=blank();Arrays.fill(full,.91f);
  require(!GenericSubjectGate.assess(full,W,H).usable,"whole frame is not a single subject");
  float[] tiny=blank();rectangle(tiny,23,20,26,32);
  require(!GenericSubjectGate.assess(tiny,W,H).usable,"tiny uncertain object is a fallback");
  float[] fragmented=blank();for(int y=4;y<44;y+=5)for(int x=3;x<45;x+=5)rectangle(fragmented,x,y,x+2,y+2);
  require(!GenericSubjectGate.assess(fragmented,W,H).usable,"isolated patches not safe");
  float[] edge=blank();rectangle(edge,0,0,45,20);
  require(!GenericSubjectGate.assess(edge,W,H).usable,"border-heavy is uncertain");
  bounded[0]=Float.NaN;
  require(!GenericSubjectGate.assess(bounded,W,H).usable,"nonfinite tensor must fail closed");
  System.out.println("GenericSubjectGateRegressionTest PASS");
 }
}