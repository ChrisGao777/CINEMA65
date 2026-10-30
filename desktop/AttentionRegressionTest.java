import com.cinema65.oneclick.FilmCore;
import java.util.Arrays;

public class AttentionRegressionTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        final int w=480,h=320;
        int[] bland=new int[w*h];Arrays.fill(bland,0xff8d9193);
        FilmCore.Analysis base=FilmCore.analyze(bland,w,h);
        check(base.focusConfidence<.08,"Blank surface invented a subject: "+base.focusConfidence);
        int[] scene=bland.clone();
        for(int y=90;y<250;y++)for(int x=265;x<400;x++){
            boolean frame=x<273||x>392||y<98||y>243;
            int c=frame?0xffd89b56:0xff534171;
            if((x+y)%11==0)c=0xffa88c68;
            scene[y*w+x]=c;
        }
        FilmCore.Analysis a=FilmCore.analyze(scene,w,h);
        check(a.focusConfidence>.25,"Obvious colored subject not recognized: "+a.focusConfidence);
        check(a.focusX>.43&&a.focusX<.93,"Subject focus mislocated x="+a.focusX);
        check(a.focusY>.19&&a.focusY<.90,"Subject focus mislocated y="+a.focusY);
        int[] full=scene.clone();FilmCore.applyTile(full,w,h,0,0,w,h,a);
        int[] tiles=scene.clone();
        for(int y=0;y<h;y+=13){int rows=Math.min(13,h-y);int[] row=new int[w*rows];
            System.arraycopy(scene,y*w,row,0,row.length);
            FilmCore.applyTile(row,w,rows,0,y,w,h,a);
            System.arraycopy(row,0,tiles,y*w,row.length);
        }
        check(Arrays.equals(full,tiles),"Focus-field tile seam");
        double subjectContrast=0,backgroundContrast=0;
        for(int y=0;y<h;y+=3)for(int x=0;x<w;x+=3){
            int i=y*w+x;int before=scene[i],after=full[i];
            double diff=(((after>>>16)&255)+((after>>>8)&255)+(after&255)) / 3.0-
                (((before>>>16)&255)+((before>>>8)&255)+(before&255))/3.0;
            if(x>=270&&x<392&&y>=103&&y<240)subjectContrast+=diff;
            else backgroundContrast+=diff;
        }
        System.out.printf(java.util.Locale.US,
            "PASS: flat scene no hallucinated spotlight; focus=(%.2f,%.2f), confidence=%.2f; tile-identical%n",
            a.focusX,a.focusY,a.focusConfidence);
    }
}