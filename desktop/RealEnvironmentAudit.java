import com.cinema65.oneclick.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Arrays;
import javax.imageio.ImageIO;
/** Exact same photo, true offline U2NETP mask, style and model gain, environment OFF vs ON. */
public final class RealEnvironmentAudit {
 static double rgb(int p,int q){return (Math.abs((p&255)-(q&255))+Math.abs(((p>>>8)&255)-((q>>>8)&255))+Math.abs(((p>>>16)&255)-((q>>>16)&255)))/3.;}
 static double luma(int p){return .2126*((p>>>16)&255)+.7152*((p>>>8)&255)+.0722*(p&255);}
 public static void main(String[] args)throws Exception {
  if(args.length<4||(args.length-1)%2!=0)throw new IllegalArgumentException("outdir image mask pairs");
  File dir=new File(args[0]);dir.mkdirs();
  try(PrintWriter csv=new PrintWriter(new File(dir,"audit.csv"),"UTF-8")){
   csv.println("photo,gate,style,semantic_trust,model_gain,environment_mean,environment_subject_L,environment_reason,environment_delta_rgb,foreground_delta_luma,background_delta_luma,added_near_black,added_near_white,added_artificial_edge,added_edge_reversal");
   for(int a=1;a<args.length;a+=2){
    File input=new File(args[a]),maskFile=new File(args[a+1]);
    BufferedImage image=RealGenericMaskAudit.scale(ImageIO.read(input),540);
    BufferedImage mask=ImageIO.read(maskFile);int w=image.getWidth(),h=image.getHeight();
    int[] src=image.getRGB(0,0,w,h,null,0,w);
    float[] sem=RealGenericMaskAudit.maskAt(mask,48,48);
    GenericSubjectGate.Review gate=GenericSubjectGate.assess(sem,48,48);
    FilmCore.Analysis pure=FilmCore.analyze(src,w,h),guided=FilmCore.analyze(src,w,h);
    if(gate.usable)FilmCore.applyGuidance(guided,sem,48,48,null);
    ModelRiskAuditor.Decision policy=ModelRiskAuditor.calibrate(guided,pure,src,w,h);
    float[] environment=FilmCore.debugEnvironmentBudget(guided);
    double em=0;for(float v:environment)em+=v;em/=environment.length;
    if(gate.usable){System.out.printf("%s: %s gain=%.2f env=%s%n",input.getName(),gate.summary(),policy.gain,guided.environmentSummary());}
    for(int style=0;style<3;style++){
     FilmCore.setEnvironmentStrength(guided,0);
     int[] old=src.clone();FilmCore.candidate(old,w,h,guided,style);
     FilmCore.setEnvironmentStrength(guided,1);
     int[] newer=src.clone();FilmCore.candidate(newer,w,h,guided,style);
     CandidateRanker.Metrics before=new CandidateRanker.Metrics(src,old,w,h);
     CandidateRanker.Metrics after=new CandidateRanker.Metrics(src,newer,w,h);
     double diff=0,s=0,b=0,ns=0,nb=0;
     for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      int i=y*w+x;double sd=gate.usable?sem[Math.min(47,(int)((y+.5)*48/h))*48+Math.min(47,(int)((x+.5)*48/w))]:0;
      double dr=luma(newer[i])-luma(old[i]);s+=sd*dr;ns+=sd;b+=(1-sd)*dr;nb+=1-sd;diff+=rgb(old[i],newer[i]);
     }
     double sampleDifference=diff/(w*h);
     if(!gate.usable&&sampleDifference>0)throw new AssertionError("Rejected mask changed output: "+input);
     csv.printf(Locale.US,"%s,%s,%d,%.3f,%.2f,%.5f,%s,%s,%.4f,%+.4f,%+.4f,%+.6f,%+.6f,%+.7f,%+.7f%n",
         input.getName(),gate.usable?"ACCEPT":"FALLBACK",style,
         guided.semanticTrust(),policy.gain,em,
         guided.environmentSummary().replaceAll(".*subject L\\* ([0-9.]+).*","$1"),
         policy.reason.replace(',',';'),sampleDifference,s/Math.max(1,ns),b/Math.max(1,nb),
         after.newBlack-before.newBlack,after.newWhite-before.newWhite,
         after.inventedEdgeRate-before.inventedEdgeRate,after.edgeReversalRate-before.edgeReversalRate);
     if(style==2&&gate.usable){String name=input.getName().replaceAll("\\.[^.]+$","");
      RealGenericMaskAudit.write(src,w,h,new File(dir,name+"-source.png"));
      RealGenericMaskAudit.write(old,w,h,new File(dir,name+"-v087.png"));
      RealGenericMaskAudit.write(newer,w,h,new File(dir,name+"-v088.png"));
     }
    }
   }
  }
 }
}