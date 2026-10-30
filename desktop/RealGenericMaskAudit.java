import com.cinema65.oneclick.FilmCore;
import com.cinema65.oneclick.GenericSubjectGate;
import com.cinema65.oneclick.CandidateRanker;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import javax.imageio.ImageIO;
/** Reproduce offline U2NETP prediction in native Java film core; NO synthetic depth. */
public final class RealGenericMaskAudit {
 static BufferedImage scale(BufferedImage src,int max){int w=src.getWidth(),h=src.getHeight();double f=Math.min(1,max/(double)Math.max(w,h));int nw=Math.max(1,(int)Math.round(w*f)),nh=Math.max(1,(int)Math.round(h*f));BufferedImage r=new BufferedImage(nw,nh,BufferedImage.TYPE_INT_RGB);Graphics2D g=r.createGraphics();g.drawImage(src,0,0,nw,nh,null);g.dispose();return r;}
 static float[] maskAt(BufferedImage m,int w,int h){float[] f=new float[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int sx=Math.min(m.getWidth()-1,(int)((x+.5)*m.getWidth()/w)),sy=Math.min(m.getHeight()-1,(int)((y+.5)*m.getHeight()/h));f[y*w+x]=(m.getRGB(sx,sy)&255)/255f;}return f;}
 static void write(int[] arr,int w,int h,File f)throws Exception{BufferedImage b=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);b.setRGB(0,0,w,h,arr,0,w);ImageIO.write(b,"png",f);}
 static double diff(int[] a,int[] b){double v=0;for(int i=0;i<a.length;i++){int x=a[i],y=b[i];v+=Math.abs((x&255)-(y&255))+Math.abs(((x>>>8)&255)-((y>>>8)&255))+Math.abs(((x>>>16)&255)-((y>>>16)&255));}return v/(3*a.length);}
 public static void main(String[] args)throws Exception{
  if(args.length<4||(args.length-1)%2!=0)throw new IllegalArgumentException("Usage: outputDir input1.jpg mask1.png ...");
  File dir=new File(args[0]);dir.mkdirs();PrintWriter csv=new PrintWriter(new File(dir,"paired_metrics.csv"),"UTF-8");
  csv.println("photo,gate,mask_area,coherence,baseline_candidate,style,model_pixel_difference,edited_new_black,edited_new_white,new_edge_rate");
  for(int arg=1;arg<args.length;arg+=2){
   File p=new File(args[arg]),m=new File(args[arg+1]);BufferedImage img=scale(ImageIO.read(p),690);BufferedImage mask=ImageIO.read(m);
   int w=img.getWidth(),h=img.getHeight();int[] pixels=img.getRGB(0,0,w,h,null,0,w);
   float[] sem=maskAt(mask,48,48);GenericSubjectGate.Review gr=GenericSubjectGate.assess(sem,48,48);
   FilmCore.Analysis pure=FilmCore.analyze(pixels,w,h),guided=FilmCore.analyze(pixels,w,h);
   if(gr.usable)FilmCore.applyGuidance(guided,sem,48,48,null);
   System.out.println(p.getName()+" "+gr.summary()+" | hasGuide="+guided.fusionSummary());
   for(int style=0;style<3;style++){
    int[] traditional=pixels.clone(),model=pixels.clone();FilmCore.candidate(traditional,w,h,pure,style);FilmCore.candidate(model,w,h,guided,style);
    CandidateRanker.Metrics mm=new CandidateRanker.Metrics(pixels,model,w,h);
    double delta=diff(traditional,model);
    csv.printf(Locale.US,"%s,%s,%.4f,%.4f,%d,%d,%.4f,%.5f,%.5f,%.6f%n",p.getName(),gr.usable?"ACCEPT":"FALLBACK",gr.coverage,gr.connectedShare,pure.selectedTreatment,style,delta,mm.newBlack,mm.newWhite,mm.inventedEdgeRate);
    if(style==0){String base=p.getName().replaceAll("\\.[^.]+$","");write(pixels,w,h,new File(dir,base+"_original.png"));write(traditional,w,h,new File(dir,base+"_traditional.png"));write(model,w,h,new File(dir,base+"_model-guided.png"));}
   }
  }
  csv.close();
 }
}