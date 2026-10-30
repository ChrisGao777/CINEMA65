import com.cinema65.oneclick.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import javax.imageio.ImageIO;
/** v0.8.9 real-image A/B. No user assets go to GitHub; derived CSV only. */
public final class RealStrategyAudit {
 static double Y(int a){return .2126*((a>>>16)&255)+.7152*((a>>>8)&255)+.0722*(a&255);}
 static double delta(int a,int b){return (Math.abs(((a>>>16)&255)-((b>>>16)&255))+Math.abs(((a>>>8)&255)-((b>>>8)&255))+Math.abs((a&255)-(b&255)))/3.0;}
 static double chr(int rgb){int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;return Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));}
 static float[] rawMask(BufferedImage m,int w,int h){
  float[] out=new float[w*h];boolean gray=m.getRaster().getNumBands()==1;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
    int sx=Math.min(m.getWidth()-1,(int)((x+.5)*m.getWidth()/w));
    int sy=Math.min(m.getHeight()-1,(int)((y+.5)*m.getHeight()/h));
    out[y*w+x]=(gray?m.getRaster().getSample(sx,sy,0):(m.getRGB(sx,sy)&255))/255f;
  }
  return out;
 }
 static final String HEADER="photo,accepted,style,model_gain,color_budget,coord_RGB_delta,color_RGB_delta,total_RGB_delta,subject_luma_delta,background_luma_delta,background_chroma_delta,delta_new_black,delta_new_white,delta_new_hard_edge,policy,gate";
 public static void main(String[] args)throws Exception {
  if(args.length<4||(args.length-1)%2!=0)throw new IllegalArgumentException("outdir (image mask)+");
  File out=new File(args[0]);out.mkdirs();
  try(PrintWriter csv=new PrintWriter(new File(out,"audit.csv"),"UTF-8")){
   csv.println(HEADER);
   for(int j=1;j<args.length;j+=2){
    File file=new File(args[j]);BufferedImage im=RealGenericMaskAudit.scale(ImageIO.read(file),520);
    BufferedImage mask=ImageIO.read(new File(args[j+1]));
    int w=im.getWidth(),h=im.getHeight();int[] src=im.getRGB(0,0,w,h,null,0,w);
    float[] m=rawMask(mask,48,48);
    GenericSubjectGate.Review coarse=GenericSubjectGate.assess(m,48,48);
    File rawMask=new File(args[j+1].replace("_mask.png","_mask320.png"));
    BufferedImage fullImage=rawMask.isFile()?ImageIO.read(rawMask):mask;
    GenericSubjectGate.Review full=GenericSubjectGate.assess(rawMask(fullImage,320,320),320,320);
    GenericSubjectGate.Review gate=full.usable?coarse:full;
    FilmCore.Analysis pure=FilmCore.analyze(src,w,h),guided=FilmCore.analyze(src,w,h);
    if(gate.usable)FilmCore.applyGuidance(guided,m,48,48,null);
    ModelRiskAuditor.Decision risk=ModelRiskAuditor.calibrate(guided,pure,src,w,h);
    for(int style=0;style<3;style++){
     FilmCore.setOrchestratorStrength(guided,0);FilmCore.setColorStrength(guided,0);
     int[] old=src.clone();FilmCore.candidate(old,w,h,guided,style);
     FilmCore.setOrchestratorStrength(guided,1);
     int[] coordinated=src.clone();FilmCore.candidate(coordinated,w,h,guided,style);
     FilmCore.setColorStrength(guided,1);
     int[] finalVersion=src.clone();FilmCore.candidate(finalVersion,w,h,guided,style);
     double c=0,col=0,sum=0,sl=0,bl=0,bc=0,sw=0,bw=0;
     for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      int k=y*w+x;double subj=gate.usable?m[Math.min(47,(int)((y+.5)*48/h))*48+Math.min(47,(int)((x+.5)*48/w))]:0;
      c+=delta(old[k],coordinated[k]);col+=delta(coordinated[k],finalVersion[k]);sum+=delta(old[k],finalVersion[k]);
      sl+=subj*(Y(finalVersion[k])-Y(old[k]));sw+=subj;
      bl+=(1-subj)*(Y(finalVersion[k])-Y(old[k]));
      bc+=(1-subj)*(chr(finalVersion[k])-chr(coordinated[k]));bw+=1-subj;
     }
     CandidateRanker.Metrics m1=new CandidateRanker.Metrics(src,old,w,h),m2=new CandidateRanker.Metrics(src,finalVersion,w,h);
     if(!gate.usable && sum!=0)throw new AssertionError("Rejected mask changed pixels: "+file.getName());
     csv.printf(Locale.US,"%s,%s,%d,%.3f,%s,%.4f,%.4f,%.4f,%+.4f,%+.4f,%+.4f,%+.7f,%+.7f,%+.7f,%s,%s%n",
       file.getName(),gate.usable?"ACCEPT":"FALLBACK",style,risk.gain,
       guided.colorSummary().replaceAll(".*budget ([0-9.]+).*","$1"),
       c/src.length,col/src.length,sum/src.length,sl/Math.max(1,sw),bl/Math.max(1,bw),bc/Math.max(1,bw),
       m2.newBlack-m1.newBlack,m2.newWhite-m1.newWhite,m2.inventedEdgeRate-m1.inventedEdgeRate,
       risk.reason.replace(',',';'),gate.usable?"accepted":"rejected");
     if(style==2 && gate.usable){String name=file.getName().replaceAll("\\.[^.]+$","");
      RealGenericMaskAudit.write(src,w,h,new File(out,name+"-original.png"));
      RealGenericMaskAudit.write(old,w,h,new File(out,name+"-v088.png"));
      RealGenericMaskAudit.write(coordinated,w,h,new File(out,name+"-coordinated.png"));
      RealGenericMaskAudit.write(finalVersion,w,h,new File(out,name+"-v089.png"));
     }
    }
   }
  }
 }
}