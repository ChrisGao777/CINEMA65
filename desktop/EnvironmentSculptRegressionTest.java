import com.cinema65.oneclick.*;
import java.util.Arrays;
/** v0.8.8: bright object does not mean a spotlight; only softly sculpt nearby environment. */
public class EnvironmentSculptRegressionTest {
 static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
 static double delta(int a,int b){return (Math.abs((a&255)-(b&255))+Math.abs(((a>>>8)&255)-((b>>>8)&255))+Math.abs(((a>>>16)&255)-((b>>>16)&255)))/3.;}
 static int[] frame(int w,int h,boolean bright,boolean sky){
  int[] out=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   double dx=(x+.5)/(double)w-.50,dy=(y+.5)/(double)h-.51;
   boolean sub=Math.abs(dx)<.12&&Math.abs(dy)<.25;
   int r,g,b;
   if(sub){int v=bright?232:89;r=v;g=v-11;b=v-19;}
   else if(sky){r=70;g=152;b=212;}
   else {int v=159+(int)(5*Math.cos(y/23.0));r=v;g=v-3;b=v-8;}
   out[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
  }return out;
 }
 static float[] rectMask(int mw,int mh){
  float[] m=new float[mw*mh];for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
   double u=(x+.5)/(double)mw-.5,v=(y+.5)/(double)mh-.51;
   m[y*mw+x]=Math.abs(u)<.12&&Math.abs(v)<.25?1:0;
  }return m;
 }
 static class Test {final int[] old,safe;final double mean;Test(int[] old,int[] safe,double mean){this.old=old;this.safe=safe;this.mean=mean;}}
 static Test run(int[] src,int w,int h,float[] m){
  FilmCore.Analysis a=FilmCore.analyze(src,w,h);
  FilmCore.applyGuidance(a,m,48,48,null);
  FilmCore.setEnvironmentStrength(a,0);
  int[] off=src.clone();FilmCore.candidate(off,w,h,a,2);
  FilmCore.setEnvironmentStrength(a,1);
  int[] on=src.clone();FilmCore.candidate(on,w,h,a,2);
  double sum=0;for(int i=0;i<off.length;i++)sum+=delta(off[i],on[i]);
  return new Test(off,on,sum/off.length);
 }
 public static void main(String[] args){
  int w=360,h=240;float[] sem=rectMask(48,48);
  Test lit=run(frame(w,h,true,false),w,h,sem);
  ok(lit.mean>.025,"lit foreground must induce measurable background contextual change: "+lit.mean);
  Test dim=run(frame(w,h,false,false),w,h,sem);
  ok(dim.mean<.007,"dim subject must not trigger environment-first darkening: "+dim.mean);
  int y=h/2;
  double near=0,far=0,subject=0;int n=0,nf=0,ns=0;
  for(int x=0;x<w;x++){
   double d=delta(lit.old[y*w+x],lit.safe[y*w+x]);
   if(x>95&&x<130){near+=d;n++;}
   if(x<30){far+=d;nf++;}
   if(x>159&&x<199){subject+=d;ns++;}
  }
  ok(near/n>far/Math.max(1,nf)+.05,"local ring must outshine far corners in delta");
  ok(subject/ns<near/n,"foreground should not be needlessly relit");
  // The background can be blue sky; contextual edit must not create sky holes.
  Test blue=run(frame(w,h,true,true),w,h,sem);
  ok(blue.mean<lit.mean*.40,"cyan sky must resist context darkening: "+blue.mean+" vs "+lit.mean);
  // Missing semantic support cannot enable environment shaping.
  Test absent=run(frame(w,h,true,false),w,h,new float[48*48]);
  ok(Arrays.equals(absent.old,absent.safe),"no semantics requires byte-exact pure fallback");
  // Tile parity for guided rendering, including EnvironmentSculptor blur map.
  int[] original=frame(w,h,true,false);
  FilmCore.Analysis an=FilmCore.analyze(original,w,h);FilmCore.applyGuidance(an,sem,48,48,null);
  int[] full=original.clone();FilmCore.candidate(full,w,h,an,2);
  an.selectedTreatment=2;int[] tiled=original.clone();
  for(int yy=0;yy<h;yy+=37){int sh=Math.min(37,h-yy);int[] stripe=Arrays.copyOfRange(tiled,yy*w,(yy+sh)*w);
   FilmCore.applyTile(stripe,w,sh,0,yy,w,h,an);System.arraycopy(stripe,0,tiled,yy*w,stripe.length);}
  ok(Arrays.equals(full,tiled),"tiled/full result mismatch");
  System.out.printf("PASS environment: bright %.3f, dark %.4f, sky %.4f, near %.3f, far %.3f; tile parity OK%n",
   lit.mean,dim.mean,blue.mean,near/n,far/nf);
 }
}