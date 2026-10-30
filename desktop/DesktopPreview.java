import com.cinema65.oneclick.FilmCore;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
public class DesktopPreview {
 public static void main(String[] args)throws Exception{
    if(args.length!=2)throw new IllegalArgumentException("Usage: input.jpg output.png");
    BufferedImage src=ImageIO.read(new File(args[0]));
    if(src==null)throw new IllegalArgumentException("Unreadable input");
    int w=src.getWidth(),h=src.getHeight();
    int[] original=src.getRGB(0,0,w,h,null,0,w);
    // Mimics Android preview resolution and unified export analysis.
    int pw=w,ph=h;
    BufferedImage low=src;
    if(Math.max(w,h)>1800){
      double sc=1800./Math.max(w,h);pw=Math.max(1,(int)Math.round(w*sc));ph=Math.max(1,(int)Math.round(h*sc));
      low=new BufferedImage(pw,ph,BufferedImage.TYPE_INT_RGB);
      low.getGraphics().drawImage(src,0,0,pw,ph,null);
    }
    int[] preview=low.getRGB(0,0,pw,ph,null,0,pw);
    FilmCore.Analysis info=FilmCore.analyze(preview,pw,ph);
    for(int y=0;y<h;y+=64){
      int rows=Math.min(64,h-y);int[] strip=new int[w*rows];
      System.arraycopy(original,y*w,strip,0,strip.length);
      FilmCore.applyTile(strip,w,rows,0,y,w,h,info);
      System.arraycopy(strip,0,original,y*w,strip.length);
    }
    BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
    out.setRGB(0,0,w,h,original,0,w);
    ImageIO.write(out,"PNG",new File(args[1]));
    System.out.println(info.summary()+" / "+w+"x"+h);
 }
}