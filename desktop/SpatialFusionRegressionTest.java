import com.cinema65.oneclick.FilmCore;
import com.cinema65.oneclick.SpatialFusion;
import java.util.Arrays;
import java.util.Random;

public class SpatialFusionRegressionTest {
 static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args){
  int w=480,h=320,mw=48,mh=48;
  int[] im=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int c=(int)(84+70.*x/w);
   if(x>135&&x<310&&y>65&&y<260)c=(int)(110+52.*y/h);
   im[y*w+x]=0xff000000|(c<<16)|((c-4)<<8)|(c-9);
  }
  FilmCore.Analysis base=FilmCore.analyze(im,w,h);
  int baseTreatment=base.selectedTreatment;
  float[] semantic=new float[mw*mh],depth=new float[mw*mh];
  for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
   int id=y*mw+x;
   semantic[id]=(x>=14&&x<=29&&y>=10&&y<=39)?1:0;
   depth[id]=(float)(1.0-.9*x/(mw-1));
  }
  FilmCore.Analysis model=FilmCore.analyze(im,w,h);
  FilmCore.applyGuidance(model,semantic,mw,mh,depth);
  ok(baseTreatment==model.selectedTreatment,"Model must not force a candidate");
  ok(model.semanticTrust()>.4,"Semantic map incorrectly rejected: "+model.fusionSummary());
  ok(model.depthTrust()>.4,"Smooth depth incorrectly rejected: "+model.fusionSummary());
  int[] aa=im.clone(),bb=im.clone();
  FilmCore.candidate(aa,w,h,base,1);
  FilmCore.candidate(bb,w,h,model,1);
  long differences=0;for(int i=0;i<aa.length;i++)differences+=Math.abs((aa[i]&255)-(bb[i]&255));
  ok(differences>20000,"Fusion does not change pixels");
  // Black/white and deterministic stripe safety on fused scene.
  int[] full=im.clone(),stripe=im.clone();FilmCore.applyTile(full,w,h,0,0,w,h,model);
  for(int y=0;y<h;y+=37){int n=Math.min(37,h-y);int[] tmp=Arrays.copyOfRange(stripe,y*w,(y+n)*w);
   FilmCore.applyTile(tmp,w,n,0,y,w,h,model);System.arraycopy(tmp,0,stripe,y*w,tmp.length);}
  ok(Arrays.equals(full,stripe),"Fused preview/stripe mismatch");
  float[] uniform=new float[mw*mh];Arrays.fill(uniform,.5f);
  FilmCore.Analysis constant=FilmCore.analyze(im,w,h);FilmCore.applyGuidance(constant,null,mw,mh,uniform);
  ok(constant.depthTrust()<.001,"Constant depth should be ignored");
  int[] untouched=im.clone(),constantPix=im.clone();
  FilmCore.applyTile(untouched,w,h,0,0,w,h,base);
  FilmCore.applyTile(constantPix,w,h,0,0,w,h,constant);
  ok(Arrays.equals(untouched,constantPix),"Invalid depth changed pixels");
  float[] noise=new float[mw*mh];Random random=new Random(25);
  for(int i=0;i<noise.length;i++)noise[i]=random.nextFloat();
  FilmCore.Analysis noisy=FilmCore.analyze(im,w,h);FilmCore.applyGuidance(noisy,null,mw,mh,noise);
  ok(noisy.depthTrust()<.07,"Random depth has unreasonable confidence: "+noisy.fusionSummary());
  float[] empty=new float[mw*mh];FilmCore.Analysis noSubject=FilmCore.analyze(im,w,h);
  FilmCore.applyGuidance(noSubject,empty,mw,mh,null);
  ok(noSubject.semanticTrust()<.001,"Empty subject map counted as model output");
  ok(base.saliencyDistinctiveness<1.0,"Saliency distinction must not appear as 100% probability");
  float[] invalid=depth.clone();invalid[0]=Float.NaN;
  FilmCore.Analysis broken=FilmCore.analyze(im,w,h);
  FilmCore.applyGuidance(broken,null,mw,mh,invalid);
  ok(broken.depthTrust()==0,"NaN depth must be ignored");
  System.out.println("PASS: fusion uses semantic and signed near/far; constant/noisy model rejected; tile exact");
  System.out.println("fusion="+model.fusionSummary()+"; changed channel L1 sum="+differences);
 }
}