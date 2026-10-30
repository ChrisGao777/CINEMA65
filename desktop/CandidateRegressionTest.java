import com.cinema65.oneclick.FilmCore;
import java.util.Arrays;
public class CandidateRegressionTest {
    static void ck(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    static int rgb(int r,int g,int b){return 0xff000000|(r<<16)|(g<<8)|b;}
    public static void main(String[] args){
        int w=440,h=320;int[] image=new int[w*h];
        Arrays.fill(image,rgb(53,71,81));
        for(int y=62;y<286;y++)for(int x=155;x<296;x++) {
            int dx=x-226,dy=y-168;
            if(dx*dx/4500.0+dy*dy/8000.0<1.0) {
                int shading=(int)(18*Math.max(-1,Math.min(1,-dx/72.0)));
                image[y*w+x]=rgb(192+shading,136+shading,112+shading);
            }
        }
        FilmCore.Analysis a=FilmCore.analyze(image,w,h);
        ck(a.selectedTreatment>=0&&a.selectedTreatment<=2,"invalid treatment");
        ck(a.focusConfidence>.20,"central subject did not register");
        ck(a.focusX>.2&&a.focusX<.80,"subject focus x off center");
        int[] graded=image.clone();FilmCore.applyTile(graded,w,h,0,0,w,h,a);
        int orig=image[168*w+226],altered=graded[168*w+226];
        int change=Math.abs((orig>>16&255)-(altered>>16&255))+
            Math.abs((orig>>8&255)-(altered>>8&255))+
            Math.abs((orig&255)-(altered&255));
        ck(change<155,"skinlike region underwent extreme color shift: "+change);
        // Broad sandstone-like hue is not a real portrait and must not force mode 0.
        int[] tan=new int[w*h];
        Arrays.fill(tan,rgb(178,135,97));
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) if((x+y)%17<7)
            tan[y*w+x]=rgb(185,140,102);
        FilmCore.Analysis sand=FilmCore.analyze(tan,w,h);
        ck(sand.selectedTreatment!=0,"broad warm sandstone forced portrait mode");
        // Independent selection is reproducible on identical inputs.
        FilmCore.Analysis a2=FilmCore.analyze(image,w,h);
        ck(a2.selectedTreatment==a.selectedTreatment,"candidate selection is nondeterministic");
        System.out.println("PASS: portrait-like region protected; sandstone not classified as portrait; candidate stable");
    }
}