import com.cinema65.oneclick.FilmCore;
/** A saliency/segmentation blob extending beyond a door MUST NOT project a fake
 * bright cloud over smooth cyan sky. This tests MODEL CONTRIBUTION only. */
public class SkyLeakRegressionTest {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static double y(int c){return .2126*((c>>>16)&255)+.7152*((c>>>8)&255)+.0722*(c&255);}
 public static void main(String[] args){
  int w=300,h=200,mw=48,mh=48;
  int[] before=new int[w*h];float[] sem=new float[mw*mh],depth=new float[mw*mh];
  for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){
   double nx=(xx+.5)/w,ny=(yy+.5)/h;
   boolean door=nx>.62&&nx<.81&&ny>.39&&ny<.91;
   int c=ny<.56?0xff4285c0:0xff81765d;
   if(nx>.58 && nx<.86 && ny>.29 && ny<.39)c=0xffbcc4bd; // pale cloud
   if(door)c=0xff74559a;
   before[yy*w+xx]=c;
  }
  for(int yy=0;yy<mh;yy++)for(int xx=0;xx<mw;xx++){
   double nx=(xx+.5)/(double)mw,ny=(yy+.5)/(double)mh;
   sem[yy*mw+xx]=(float)Math.exp(-2.3*(Math.pow((nx-.72)/.20,2)+Math.pow((ny-.61)/.36,2)));
   depth[yy*mw+xx]=(float)(.2+.7*ny);
  }
  FilmCore.Analysis base=FilmCore.analyze(before,w,h),guided=FilmCore.analyze(before,w,h);
  FilmCore.applyGuidance(guided,sem,mw,mh,depth);
  check(guided.hasModelGuidance(),"A segmented door should be credible");
  int[] plain=before.clone(),altered=before.clone();
  FilmCore.candidate(plain,w,h,base,0);FilmCore.candidate(altered,w,h,guided,0);
  double sky=0,cloud=0,door=0,ns=0,nc=0,nd=0;
  for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){
   double nx=(xx+.5)/(double)w,ny=(yy+.5)/(double)h;
   int i=yy*w+xx;double d=y(altered[i])-y(plain[i]);
   if(nx>.66&&nx<.79&&ny>.21&&ny<.28){sky+=d;ns++;}
   if(nx>.65&&nx<.76&&ny>.32&&ny<.37){cloud+=d;nc++;}
   if(nx>.66&&nx<.78&&ny>.52&&ny<.78){door+=d;nd++;}
  }
  sky/=ns;cloud/=nc;door/=nd;
  check(cloud<2,"Pale-cloud halo leak: "+cloud);
  check(sky<2,"False blue-sky highlight leak: "+sky);
  check(door>1.0,"Semantic subject must still become more prominent: "+door);
  System.out.printf(java.util.Locale.US,"PASS sky & cloud spill suppressed (sky %+5.2f / cloud %+5.2f RGB luma), purple subject benefits (%+5.2f)\n",sky,cloud,door);
 }
}