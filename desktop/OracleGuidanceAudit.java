import com.cinema65.oneclick.FilmCore;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.Locale;
/** Research only: user-authored ROIs and synthetic depth, NOT neural-model results.
 * Upper bound testing of grading when credible masks would be available.
 */
public class OracleGuidanceAudit {
 public static void main(String[] args)throws Exception {
  if(args.length!=4)throw new IllegalArgumentException("Usage: src roi.png synthetic_depth.png dest-stem");
  BufferedImage orig=ImageIO.read(new File(args[0]));
  BufferedImage roi=ImageIO.read(new File(args[1]));
  BufferedImage depth=ImageIO.read(new File(args[2]));
  int w=orig.getWidth(),h=orig.getHeight(),mw=roi.getWidth(),mh=roi.getHeight();
  if(depth.getWidth()!=mw||depth.getHeight()!=mh)throw new IllegalArgumentException("Mask shape mismatch");
  float[] sem=new float[mw*mh],dep=new float[mw*mh];
  for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
   int i=y*mw+x;sem[i]=roi.getRaster().getSample(x,y,0)/255f;dep[i]=depth.getRaster().getSample(x,y,0)/255f;
  }
  int[] before=orig.getRGB(0,0,w,h,null,0,w);
  FilmCore.Analysis basic=FilmCore.analyze(before,w,h),fused=FilmCore.analyze(before,w,h);
  FilmCore.applyGuidance(fused,sem,mw,mh,dep);
  System.out.printf(Locale.US,"%s trust %s%n",new File(args[0]).getName(),fused.fusionSummary());
  for(int t=0;t<3;t++){
   int[] pixels=before.clone();FilmCore.candidate(pixels,w,h,fused,t);
   BufferedImage rendered=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);rendered.setRGB(0,0,w,h,pixels,0,w);
   ImageIO.write(rendered,"png",new File(args[3]+"_guided_"+t+".png"));
  }
 }
}