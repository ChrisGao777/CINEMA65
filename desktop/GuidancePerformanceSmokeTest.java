import com.cinema65.oneclick.*;
import java.util.Locale;
/** Desktop smoke measurement only; NOT a Xiaomi performance claim. */
public class GuidancePerformanceSmokeTest {
 public static void main(String[] args){
  int w=320,h=240,m=48;int[] image=new int[w*h];float[] sem=new float[m*m],depth=new float[m*m];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int v=(int)(48+158.0*x/w+15*Math.sin(y/12.));v=Math.max(0,Math.min(255,v));
   image[y*w+x]=0xff000000|(v<<16)|(Math.max(0,v-4)<<8)|Math.min(255,v+5);
  }
  for(int y=0;y<m;y++)for(int x=0;x<m;x++){
   double u=(x+.5)/m,v=(y+.5)/m;sem[y*m+x]=(float)Math.exp(-5*(Math.pow((u-.51)/.23,2)+Math.pow((v-.49)/.32,2)));
   depth[y*m+x]=(float)(.3+.5*u);
  }
  FilmCore.Analysis base=FilmCore.analyze(image,w,h),guided=FilmCore.analyze(image,w,h);
  FilmCore.applyGuidance(guided,sem,m,m,depth);
  ModelRiskAuditor.calibrate(guided,base,image,w,h);
  long ns=0;int runs=3;for(int i=0;i<runs;i++){
   long start=System.nanoTime();ModelRiskAuditor.calibrate(guided,base,image,w,h);ns+=System.nanoTime()-start;
  }
  System.out.printf(Locale.US,"Research CPU 320x240: safety preflight six-renders mean %.1f ms; NOT phone data%n",ns/(1e6*runs));
 }
}