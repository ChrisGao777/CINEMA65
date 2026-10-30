import com.cinema65.oneclick.FilmCore;
import java.util.*;
/** Make sure the 3 model-guided candidates represent DIFFERENT spatial light theories,
 * but do not change established v0.8.2 color processing when model evidence is absent. */
public class LightHypothesisRegressionTest {
 private static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 private static double delta(int[] x,int[] y){double a=0;for(int j=0;j<x.length;j++){
   a+=Math.abs(((x[j]>>16)&255)-((y[j]>>16)&255));a+=Math.abs(((x[j]>>8)&255)-((y[j]>>8)&255));a+=Math.abs((x[j]&255)-(y[j]&255));
 }return a/(3*x.length);}
 public static void main(String[] args){
  int w=320,h=260,mw=48,mh=48;
  int[] pix=new int[w*h];float[] foreground=new float[mw*mh],distance=new float[mw*mh];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
    double nx=(x+.5)/(double)w,ny=(y+.5)/(double)h;
    boolean portrait=Math.pow((nx-.53)/.13,2)+Math.pow((ny-.48)/.31,2)<1;
    int b=Math.max(0,Math.min(255,(int)(80+82*nx+25*Math.cos(ny*4))));
    int r=portrait?185:b,g=portrait?124:b,bl=portrait?98:b;
    pix[y*w+x]=0xff000000|(r<<16)|(g<<8)|bl;
  }
  for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
    double nx=(x+.5)/(double)mw,ny=(y+.5)/(double)mh;
    foreground[y*mw+x]=(float)Math.exp(-3*(Math.pow((nx-.53)/.17,2)+Math.pow((ny-.48)/.38,2)));
    distance[y*mw+x]=(float)(.22+.71*(1-ny));
  }
  FilmCore.Analysis control=FilmCore.analyze(pix,w,h);
  FilmCore.Analysis analysis=FilmCore.analyze(pix,w,h);
  int orig=analysis.selectedTreatment;
  FilmCore.applyGuidance(analysis,foreground,mw,mh,distance);
  require(analysis.selectedTreatment==orig,"Guidance must not force a grade choice");
  require(analysis.hasModelGuidance(),"Synthetic mask should pass trust gates");
  int[][] candidates=new int[3][];
  for(int t=0;t<3;t++){
    candidates[t]=pix.clone();FilmCore.candidate(candidates[t],w,h,analysis,t);
  }
  double d01=delta(candidates[0],candidates[1]),d12=delta(candidates[1],candidates[2]);
  require(d01>3,"Subject vs depth strategies should visibly diverge");
  require(d12>2,"Depth vs existing light should visibly diverge");
  int[][] baseline=new int[3][];
  for(int t=0;t<3;t++){
    baseline[t]=pix.clone();FilmCore.candidate(baseline[t],w,h,control,t);
  }
  require(delta(candidates[0],baseline[0])>.2,"Model mask should alter pixels");
  System.out.printf(java.util.Locale.US,"PASS distinct model lighting strategies: subject/depth %.3f; depth/existing light %.3f%n",d01,d12);
 }
}