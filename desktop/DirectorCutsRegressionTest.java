import com.cinema65.oneclick.*;
import java.util.Arrays;

public class DirectorCutsRegressionTest{
 public static void main(String[] args){
  int w=160,h=96;int[] src=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
    int l=(int)Math.round(32+165*(x/(double)(w-1)));src[y*w+x]=0xff000000|(l<<16)|(l<<8)|l;
  }
  float[] m=new float[48*48];for(int y=8;y<40;y++)for(int x=18;x<32;x++)m[y*48+x]=1;
  DirectorCuts.Plan plan=DirectorCuts.analyze(src,w,h,m,48,48,true);
  if(!plan.hasSubject)throw new AssertionError("subject rejected");
  int[] old=src.clone(),off=src.clone(),cut=src.clone();
  DirectorCuts.apply(src,off,w,h,plan,0,0);
  if(!Arrays.equals(old,off))throw new AssertionError("zero gain changed image");
  DirectorCuts.apply(src,cut,w,h,plan,0,1);
  if(Arrays.equals(old,cut))throw new AssertionError("foreground cut has no effect");
  DirectorCuts.Plan none=DirectorCuts.analyze(src,w,h,null,0,0,false);
  int[] fallback=src.clone();DirectorCuts.apply(src,fallback,w,h,none,0,1);
  if(!Arrays.equals(src,fallback))throw new AssertionError("subject cut without subject changed image");
  for(int style=0;style<3;style++){
    int[] actual=src.clone();DirectorCuts.apply(src,actual,w,h,plan,style,1);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      int k=y*w+x;int correct=DirectorCuts.pixel(src[k],src[k],(x+.5)/w,(y+.5)/h,plan,style,1);
      if(actual[k]!=correct)throw new AssertionError("tile mismatch "+style+" "+k);
    }
  }
  System.out.println("DirectorCuts regression PASSED: zero-gain, semantic fallback, visible cut, pixel/tile identity");
 }
}