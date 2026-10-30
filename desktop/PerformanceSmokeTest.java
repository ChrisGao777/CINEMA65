import com.cinema65.oneclick.FilmCore;
import java.util.Locale;
public class PerformanceSmokeTest {
 public static void main(String[] args){
  int w=640,h=480,m=48;int[] source=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int r=40+x*200/w,g=50+y*200/h,b=(r+g)/2;
   source[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
  }
  float[] depth=new float[m*m],sem=new float[m*m];
  for(int y=0;y<m;y++)for(int x=0;x<m;x++){
   int id=y*m+x;depth[id]=x/(float)(m-1);
   if(x>13&&x<33&&y>10&&y<40)sem[id]=1f;
  }
  FilmCore.Analysis baseline=FilmCore.analyze(source,w,h);
  FilmCore.Analysis fusion=FilmCore.analyze(source,w,h);
  FilmCore.applyGuidance(fusion,sem,m,m,depth);
  double[] timings=new double[2];
  for(int run=0;run<5;run++)for(int j=0;j<2;j++){
   int[] arr=source.clone();long start=System.nanoTime();
   FilmCore.applyTile(arr,w,h,0,0,w,h,j==0?baseline:fusion);
   if(run>0)timings[j]+=(System.nanoTime()-start)/1e6;
  }
  System.out.printf(Locale.US,"640x480 mean CPU rendering: baseline %.1f ms; fused %.1f ms; relative %.2fx%n",timings[0]/4,timings[1]/4,timings[1]/Math.max(1,timings[0]));
 }
}