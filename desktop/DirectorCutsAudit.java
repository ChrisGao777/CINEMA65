import com.cinema65.oneclick.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Research-only, before-only local photo A/B. No image data sent to GitHub. */
public final class DirectorCutsAudit {
    private static int[] pixels(BufferedImage image){int w=image.getWidth(),h=image.getHeight();return image.getRGB(0,0,w,h,null,0,w);}
    private static BufferedImage resize(BufferedImage in,int max){
        int w=in.getWidth(),h=in.getHeight();if(Math.max(w,h)<=max)return in;
        int tw=(int)Math.round(w*max/(double)Math.max(w,h)),th=(int)Math.round(h*max/(double)Math.max(w,h));
        BufferedImage out=new BufferedImage(tw,th,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g=out.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(in,0,0,tw,th,null);g.dispose();return out;
    }
    private static void save(int[] v,int w,int h,File f)throws Exception{
        BufferedImage im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);im.setRGB(0,0,w,h,v,0,w);ImageIO.write(im,"png",f);
    }
    public static void main(String[] a)throws Exception{
        if(a.length<1||a.length%3!=1)throw new IllegalArgumentException("outdir (photo mask48 mask320)+; '-' for missing mask");
        File output=new File(a[0]);output.mkdirs();
        try(PrintWriter csv=new PrintWriter(new File(output,"audit.csv"),"UTF-8")){
            csv.println("photo,cut,credible,baseline_vs_original_meanrgb,cut_vs_baseline_meanrgb,cut_vs_baseline_delta_luma,newblack_increment,newwhite_increment,edge_increment,edge_reversal_increment,safe,gate_info");
            for(int z=1;z<a.length;z+=3){
                File input=new File(a[z]);BufferedImage img=resize(ImageIO.read(input),720);int w=img.getWidth(),h=img.getHeight();int[] source=pixels(img);
                FilmCore.Analysis standard=FilmCore.analyze(source,w,h);
                int[] baseline=source.clone();FilmCore.candidate(baseline,w,h,standard,standard.selectedTreatment);
                float[] mask=null;boolean ok=false;String why="missing";
                if(!a[z+1].equals("-")){
                    BufferedImage m=ImageIO.read(new File(a[z+1]));mask=RealStrategyAudit.rawMask(m,48,48);
                    BufferedImage m320=ImageIO.read(new File(a[z+2]));
                    GenericSubjectGate.Review low=GenericSubjectGate.assess(mask,48,48);
                    GenericSubjectGate.Review hi=GenericSubjectGate.assess(RealStrategyAudit.rawMask(m320,320,320),320,320);
                    ok=low.usable&&hi.usable;why=String.format(Locale.US,"48=%s;320=%s",low.usable,hi.usable);
                }
                DirectorCuts.Plan scene=DirectorCuts.analyze(source,w,h,mask,48,48,ok);
                save(source,w,h,new File(output,input.getName().replaceAll("\\.[^.]+$","")+"-original.png"));
                save(baseline,w,h,new File(output,input.getName().replaceAll("\\.[^.]+$","")+"-baseline.png"));
                CandidateRanker.Metrics bmetr=new CandidateRanker.Metrics(source,baseline,w,h);
                for(int cut=0;cut<3;cut++){
                    int[] candidate=null;CandidateRanker.Metrics newmetr=null;
                    double chosen=0;
                    for(double gain:new double[]{1.0,.7,.4,0}){
                        int[] trial=baseline.clone();DirectorCuts.apply(source,trial,w,h,scene,cut,gain);
                        CandidateRanker.Metrics mm=new CandidateRanker.Metrics(source,trial,w,h);
                        double xb=Math.max(0,mm.newBlack-bmetr.newBlack),xw=Math.max(0,mm.newWhite-bmetr.newWhite);
                        double xe=Math.max(0,mm.inventedEdgeRate-bmetr.inventedEdgeRate),xr=Math.max(0,mm.edgeReversalRate-bmetr.edgeReversalRate);
                        if(ModelRiskAuditor.withinLimits(xb,xw,xe,xr)){
                            candidate=trial;newmetr=mm;chosen=gain;break;
                        }
                    }
                    if(candidate==null)throw new IllegalStateException("0-gain control must pass");
                    double abs=0,luma=0;for(int i=0;i<source.length;i++){
                        int old=baseline[i],now=candidate[i];
                        abs+=(Math.abs(((old>>>16)&255)-((now>>>16)&255))+Math.abs(((old>>>8)&255)-((now>>>8)&255))+Math.abs((old&255)-(now&255)))/3.;
                        luma+=.2126*(((now>>>16)&255)-((old>>>16)&255))+.7152*(((now>>>8)&255)-((old>>>8)&255))+.0722*((now&255)-(old&255));
                    }
                    double bd=Math.max(0,newmetr.newBlack-bmetr.newBlack),wd=Math.max(0,newmetr.newWhite-bmetr.newWhite),edge=Math.max(0,newmetr.inventedEdgeRate-bmetr.inventedEdgeRate),rev=Math.max(0,newmetr.edgeReversalRate-bmetr.edgeReversalRate);
                    csv.printf(Locale.US,"%s,%d,%s,%.3f,%.3f,%+.3f,%.6f,%.6f,%.6f,%.6f,%s,%s%n",
                          input.getName(),cut,ok,bmetr.meanAbsDifference,abs/source.length,luma/source.length,bd,wd,edge,rev,chosen,why);
                    save(candidate,w,h,new File(output,input.getName().replaceAll("\\.[^.]+$","")+"-cut"+cut+".png"));
                }
            }
        }
    }
}