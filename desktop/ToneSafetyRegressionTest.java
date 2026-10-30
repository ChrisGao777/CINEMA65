import com.cinema65.oneclick.FilmCore;
import com.cinema65.oneclick.ToneSafety;
import java.util.Arrays;
import java.util.Random;

public class ToneSafetyRegressionTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static double Y(int p){return .2126*((p>>>16)&255)+.7152*((p>>>8)&255)+.0722*(p&255);}
    public static void main(String[] args){
        final int original=0xffb2b4b0;
        for(int i=0;i<256;i++){
            int v=0xff000000|(i<<16)|(i<<8)|i;
            check(ToneSafety.protect(original,v,0)==v,"Zero weight must be exact identity");
        }
        double previous=-1;
        for(int i=0;i<256;i++){
            int v=0xff000000|(i<<16)|(i<<8)|i;
            int p=ToneSafety.protect(original,v,1);
            double n=Y(p);
            check(n>=previous-0.001,"Tone response is not monotonic at "+i);
            previous=n;
        }
        int highSource=0xffbcb7b4,blown=0xffffffff;
        int repaired=ToneSafety.protect(highSource,blown,1);
        check(Y(repaired)<252,"Original mid-high must not create fully clipped white");
        check(Y(repaired)>236,"Highlight safety should not gray out light");
        int deep=ToneSafety.protect(0xff050505,0xff000000,1);
        check(deep==0xff000000,"Already black original must stay black");
        int originallyVisible=ToneSafety.protect(0xff373737,0xff010101,1);
        check(Y(originallyVisible)>5,"Previously visible dark region should keep a minimum floor");
        check(ToneSafety.protect(0xffffffff,0xfffefefa,1)==0xfffefefa,"Already clipped white is unrecoverable");
        int colorful=ToneSafety.protect(0xffc2b4aa,0xfffdf2dc,1);
        check(((colorful>>>16)&255)>((colorful>>>8)&255)&&((colorful>>>8)&255)>(colorful&255),"Hue ordering changed");
        final int w=323,h=167;
        Random rnd=new Random(1007);
        int[] src=new int[w*h];for(int i=0;i<src.length;i++)src[i]=0xff000000|rnd.nextInt(0x1000000);
        FilmCore.Analysis a=FilmCore.analyze(src,w,h);int treatment=a.selectedTreatment;
        int[] legacy=src.clone();FilmCore.setToneSafetyStrength(a,0);FilmCore.applyTile(legacy,w,h,0,0,w,h,a);
        int[] guarded=src.clone();FilmCore.setToneSafetyStrength(a,1);FilmCore.applyTile(guarded,w,h,0,0,w,h,a);
        check(!Arrays.equals(legacy,guarded),"Guard must have real influence");
        int[] stripes=src.clone();int row=0;
        while(row<h){int count=Math.min(29,h-row);int[] part=Arrays.copyOfRange(src,row*w,(row+count)*w);
            FilmCore.applyTile(part,w,count,0,row,w,h,a);System.arraycopy(part,0,stripes,row*w,part.length);row+=count;}
        check(Arrays.equals(guarded,stripes),"Guard breaks striped export parity");
        double total=0;int count=0;
        for(int i=0;i<legacy.length;i++){
          double d=Math.abs(Y(legacy[i])-Y(guarded[i]));total+=d;if(d>1)count++;
        }
        check(count>0,"Guard should affect at-risk pixels");
        System.out.printf(java.util.Locale.US,"PASS tone safety: monotone; preserve chroma; highlight/toe; switch; tiled parity. Mean luma delta %.3f; >1 level %.1f%%%n",total/src.length,100.*count/src.length);
    }
}