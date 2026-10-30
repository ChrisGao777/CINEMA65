import com.cinema65.oneclick.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import javax.imageio.ImageIO;
/** Regenerate real-image, same-look, same-input mask A/B; measure INCREMENTAL harm. */
public class RealLightBudgetAudit {
 static double dy(int p){return .2126*((p>>>16)&255)+.7152*((p>>>8)&255)+.0722*(p&255);}
 static double rgb(int p,int q){return (Math.abs((p&255)-(q&255)) + Math.abs(((p>>>8)&255)-((q>>>8)&255)) + Math.abs(((p>>>16)&255)-((q>>>16)&255)))/3.;}
 public static void main(String[] args)throws Exception{
  if(args.length<4 || (args.length-1)%2!=0)throw new IllegalArgumentException("outdir image mask pairs");
  File output=new File(args[0]);output.mkdirs();
  try(PrintWriter csv=new PrintWriter(new File(output,"audit.csv"),"UTF-8")){
   csv.println("photo,gate,exposure_gain,reason,style,model_delta_rgb,subject_delta_luma,background_delta_luma,increment_black,increment_white,increment_hard_edges,pure_new_white,model_new_white");
   for(int i=1;i<args.length;i+=2){
    File f=new File(args[i]),m=new File(args[i+1]);BufferedImage im=RealGenericMaskAudit.scale(ImageIO.read(f),540);
    BufferedImage mask=ImageIO.read(m);int w=im.getWidth(),h=im.getHeight();
    int[] pixels=im.getRGB(0,0,w,h,null,0,w);
    float[] sem=RealGenericMaskAudit.maskAt(mask,48,48);
    GenericSubjectGate.Review gate=GenericSubjectGate.assess(sem,48,48);
    FilmCore.Analysis pure=FilmCore.analyze(pixels,w,h),guided=FilmCore.analyze(pixels,w,h);
    if(gate.usable)FilmCore.applyGuidance(guided,sem,48,48,null);
    ModelRiskAuditor.Decision decision=ModelRiskAuditor.calibrate(guided,pure,pixels,w,h);
    String basename=f.getName().replaceAll("\\.[^.]+$", "");
    for(int style=0;style<3;style++){
     int[] a=pixels.clone(),b=pixels.clone();FilmCore.candidate(a,w,h,pure,style);FilmCore.candidate(b,w,h,guided,style);
     CandidateRanker.Metrics am=new CandidateRanker.Metrics(pixels,a,w,h),bm=new CandidateRanker.Metrics(pixels,b,w,h);
     double diff=0,sub=0,bg=0,ns=0,nb=0;
     for(int y=0;y<h;y++)for(int x=0;x<w;x++){
       int k=y*w+x;double sx=(x+.5)/w,sy=(y+.5)/h;
       double region=sem[Math.min(47,(int)(sy*48))*48+Math.min(47,(int)(sx*48))];
       double l=dy(b[k])-dy(a[k]);double wt=gate.usable?region:0;
       diff+=rgb(b[k],a[k]);sub+=wt*l;ns+=wt;bg+=(1-wt)*l;nb+=1-wt;
     }
     csv.printf(Locale.US,"%s,%s,%.2f,%s,%d,%.4f,%+.4f,%+.4f,%+.5f,%+.5f,%+.6f,%.5f,%.5f%n",
      f.getName(),gate.usable?"ACCEPT":"FALLBACK",decision.gain,decision.reason.replace(',',';'),style,
      diff/pixels.length,sub/Math.max(1,ns),bg/Math.max(1,nb),
      bm.newBlack-am.newBlack,bm.newWhite-am.newWhite,bm.inventedEdgeRate-am.inventedEdgeRate,am.newWhite,bm.newWhite);
     if(style==0){RealGenericMaskAudit.write(pixels,w,h,new File(output,basename+"_original.png"));
       RealGenericMaskAudit.write(a,w,h,new File(output,basename+"_pure.png"));
       RealGenericMaskAudit.write(b,w,h,new File(output,basename+"_guided.png"));}
    }
    System.out.println(f.getName()+" gate="+gate.usable+"; "+decision.summary());
   }
  }
 }
}