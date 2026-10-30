import com.cinema65.oneclick.FilmCore;
import com.cinema65.oneclick.CandidateRanker;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

/** Identical source, treatment, deterministic noise, and no semantic input. */
public final class RealToneSafetyAudit {
    static double y(int p){return .2126*((p>>>16)&255)+.7152*((p>>>8)&255)+.0722*(p&255);}
    static double[] analyze(int[] src,int[] graded){
      int newlyDark=0,newlyWhite=0,nearHigh=0,nearLow=0;double mean=0;
      for(int i=0;i<src.length;i++){
        double before=y(src[i]),after=y(graded[i]);
        if(before>15&&after<=3)newlyDark++;
        if(before<238&&after>=252)newlyWhite++;
        if(before>15&&before<235&&after>=248)nearHigh++;
        if(before>15&&after<10)nearLow++;
        mean+=Math.abs(after-before);
      }
      return new double[]{newlyDark/(double)src.length,newlyWhite/(double)src.length,nearHigh/(double)src.length,nearLow/(double)src.length,mean/src.length};
    }
    static int[] process(FilmCore.Analysis a,int[] source,int w,int h,int treatment, double safety){
      int[] result=source.clone();FilmCore.setToneSafetyStrength(a,safety);
      FilmCore.candidate(result,w,h,a,treatment);
      return result;
    }
    static BufferedImage resize(BufferedImage source,int max){
      double scale=Math.min(1,max/(double)Math.max(source.getWidth(),source.getHeight()));
      int w=(int)Math.round(source.getWidth()*scale),h=(int)Math.round(source.getHeight()*scale);
      BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
      Graphics2D g=result.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(source,0,0,w,h,null);g.dispose();return result;
    }
    static void write(int[] rgb,int w,int h,String filename)throws Exception{
      BufferedImage b=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);b.setRGB(0,0,w,h,rgb,0,w);ImageIO.write(b,"png",new File(filename));
    }
    public static void main(String[] args)throws Exception{
      if(args.length<2)throw new IllegalArgumentException("Usage output-dir photos...");
      File dir=new File(args[0]);dir.mkdirs();
      try(FileWriter csv=new FileWriter(new File(dir,"audit.csv"))){
        csv.write("image,treatment,base_newblack,guard_newblack,base_newwhite,guard_newwhite,base_near_high,guard_near_high,base_near_low,guard_near_low,base_mean_abs_luma_change,guard_mean_abs_luma_change,mean_abs_guard_delta,edge_base,edge_guard\n");
        for(int z=1;z<args.length;z++){
          String in=args[z];BufferedImage src=resize(ImageIO.read(new File(in)),540);
          int w=src.getWidth(),h=src.getHeight();int[] pix=src.getRGB(0,0,w,h,null,0,w);
          FilmCore.Analysis a=FilmCore.analyze(pix,w,h);
          String label=new File(in).getName().replaceAll("\\.[^.]+$","");
          write(pix,w,h,new File(dir,label+"-original.png").getPath());
          for(int choice=0;choice<3;choice++){
            int[] baseline=process(a,pix,w,h,choice,0);
            int[] guarded=process(a,pix,w,h,choice,1);
            double[] b=analyze(pix,baseline),g=analyze(pix,guarded);
            double abs=0;for(int i=0;i<baseline.length;i++)abs+=Math.abs(y(baseline[i])-y(guarded[i]));
            CandidateRanker.Metrics e1=new CandidateRanker.Metrics(pix,baseline,w,h),e2=new CandidateRanker.Metrics(pix,guarded,w,h);
            csv.write(String.format(Locale.US,"%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%.4f,%.7f,%.7f%n",label,choice,b[0],g[0],b[1],g[1],b[2],g[2],b[3],g[3],b[4],g[4],abs/baseline.length,e1.inventedEdgeRate,e2.inventedEdgeRate));
            if(choice==a.selectedTreatment){
              write(baseline,w,h,new File(dir,label+"-v086.png").getPath());
              write(guarded,w,h,new File(dir,label+"-v087.png").getPath());
            }
          }
          System.out.println("AUDITED "+label+" "+w+"x"+h+" style="+a.selectedTreatment);
        }
      }
    }
}