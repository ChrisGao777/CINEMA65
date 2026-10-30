import com.cinema65.oneclick.FilmCore;
import com.cinema65.oneclick.CandidateRanker;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
/** Research-only CLI: original and 3 candidate styles from identical source and scene analysis.
 * No neural inference takes place on desktop; do not call these "AI" outputs.
 */
public final class StrategyAudit {
  public static void main(String[] args) throws Exception {
    if(args.length<2)throw new IllegalArgumentException("Usage: outputDir files...");
    File folder=new File(args[0]);folder.mkdirs();
    PrintWriter csv=new PrintWriter(new File(folder,"audit.csv"),"UTF-8");
    csv.println("photo,width,height,traditional,tone,distinctiveness,photo_luma,average_absolute_change,clipped_black,clipped_white,new_hard_edge_rate,reversed_edge_rate,pair_difference_0_1,pair_difference_1_2");
    for(int f=1;f<args.length;f++){
       File srcfile=new File(args[f]);BufferedImage pic=ImageIO.read(srcfile);if(pic==null)continue;
       int iw=pic.getWidth(),ih=pic.getHeight();double s=Math.min(1,650./Math.max(iw,ih));
       int w=Math.max(1,(int)Math.round(iw*s)),h=Math.max(1,(int)Math.round(ih*s));
       BufferedImage resized=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
       Graphics2D gg=resized.createGraphics();gg.drawImage(pic,0,0,w,h,null);gg.dispose();
       int[] base=resized.getRGB(0,0,w,h,null,0,w);
       FilmCore.Analysis scene=FilmCore.analyze(base,w,h);
       String stub=srcfile.getName().replaceAll("\\.[^.]+$","");
       ImageIO.write(resized,"png",new File(folder,stub+"_original.png"));
       int[][] all=new int[3][];
       for(int j=0;j<3;j++){
          all[j]=base.clone();FilmCore.candidate(all[j],w,h,scene,j);
          BufferedImage b=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
          b.setRGB(0,0,w,h,all[j],0,w);
          ImageIO.write(b,"png",new File(folder,stub+"_candidate"+j+".png"));
          CandidateRanker.Metrics metrics=new CandidateRanker.Metrics(base,all[j],w,h);
          csv.printf(Locale.US,"%s,%d,%d,%d,%d,%.4f,%.2f,%.4f,%.6f,%.6f,%.7f,%.7f,%.4f,%.4f%n",
            stub,w,h,scene.selectedTreatment,j,scene.saliencyDistinctiveness,scene.median,
            metrics.meanAbsDifference,metrics.newBlack,metrics.newWhite,
            metrics.inventedEdgeRate,metrics.edgeReversalRate,
            pixelDelta(all[0],all[1]),pixelDelta(all[1],all[2]));
       }
       System.out.printf(Locale.US,"AUDIT %s: baseline %d; salient evidence %.3f; candidate 0/1=%.3f 1/2=%.3f%n",stub,scene.selectedTreatment,scene.saliencyDistinctiveness,pixelDelta(all[0],all[1]),pixelDelta(all[1],all[2]));
    }
    csv.close();
  }
  private static double pixelDelta(int[] a,int[] b){if(a==null||b==null)return Double.NaN;long sum=0;for(int i=0;i<a.length;i++){
    sum+=Math.abs((a[i]&255)-(b[i]&255))+Math.abs(((a[i]>>8)&255)-((b[i]>>8)&255))+Math.abs(((a[i]>>16)&255)-((b[i]>>16)&255));
   }return sum/(3.0*a.length);
  }
}