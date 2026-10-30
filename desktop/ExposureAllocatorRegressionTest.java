import com.cinema65.oneclick.FilmCore;
import com.cinema65.oneclick.ModelRiskAuditor;
import java.util.Arrays;
/** Separate model detection from permission to allocate exposure. */
public class ExposureAllocatorRegressionTest {
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static double delta(int[] a,int[] b){double s=0;for(int i=0;i<a.length;i++)s+=Math.abs((a[i]&255)-(b[i]&255));return s/a.length;}
 static int[] frame(int w,int h,boolean bright){int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
  double nx=(x+.5)/w,ny=(y+.5)/h;
  boolean subject=Math.pow((nx-.52)/.19,2)+Math.pow((ny-.53)/.30,2)<1;
  int bg=bright?125:35,fg=bright?222:91;
  int r=subject?fg:bg,g=subject?Math.max(0,fg-17):bg,b=subject?Math.max(0,fg-31):bg;
  p[y*w+x]=0xff000000|r<<16|g<<8|b;
 }return p;}
 public static void main(String[] args){
  int w=300,h=220,mw=48,mh=48;
  float[] sem=new float[mw*mh];for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
    double u=(x+.5)/48.,v=(y+.5)/48.;sem[y*mw+x]=(float)Math.exp(-3*(Math.pow((u-.52)/.22,2)+Math.pow((v-.53)/.34,2)));
  }
  int[] low=frame(w,h,false),high=frame(w,h,true);
  FilmCore.Analysis a=FilmCore.analyze(low,w,h),b=FilmCore.analyze(low,w,h);
  FilmCore.applyGuidance(b,sem,mw,mh,null);
  require(b.hasModelGuidance(),"synthetic object mask must be trusted");
  require(b.exposureSummary().contains("dodge budget"),"missing allocation diagnostic");
  int[] base=low.clone(),newer=low.clone();FilmCore.candidate(base,w,h,a,0);FilmCore.candidate(newer,w,h,b,0);
  require(delta(base,newer)>.15,"dark subject model should affect grade");
  // Weight 0 MUST be pixel-identical to traditional renderer, not close.
  FilmCore.setGuidanceGain(b,0);int[] off=low.clone();FilmCore.candidate(off,w,h,b,0);
  require(Arrays.equals(base,off),"vetoed model must reproduce pure output exactly");
  FilmCore.Analysis hb=FilmCore.analyze(high,w,h),hm=FilmCore.analyze(high,w,h);
  FilmCore.applyGuidance(hm,sem,mw,mh,null);
  int[] pure=high.clone(),guided=high.clone();FilmCore.candidate(pure,w,h,hb,0);FilmCore.candidate(guided,w,h,hm,0);
  ModelRiskAuditor.Decision risk=ModelRiskAuditor.calibrate(hm,hb,high,w,h);
  require(risk.gain>=0&&risk.gain<=1,"invalid guidance gain");
  FilmCore.setGuidanceGain(hm,0);int[] safe=high.clone();FilmCore.candidate(safe,w,h,hm,0);
  require(Arrays.equals(pure,safe),"veto full rollback inaccurate");
  System.out.println("PASS: subject budget allocated; pure-mode byte exact; risk gate="+risk.summary());
 }
}