import com.cinema65.oneclick.FilmCore;
import java.util.Random;
/** Experimental desktop benchmark: not predictive of Android 17 wall-time. */
public class ToneSafetyPerformanceSmokeTest {
    public static void main(String[] args){
        int w=1100,h=770;
        Random rd=new Random(88);int[] source=new int[w*h];
        for(int i=0;i<source.length;i++)source[i]=0xff000000|rd.nextInt(0x1000000);
        FilmCore.Analysis a=FilmCore.analyze(source,w,h);
        long[] ms=new long[2];
        for(int t=0;t<2;t++){
            FilmCore.setToneSafetyStrength(a,t);
            for(int rep=0;rep<2;rep++){
                int[] out=source.clone();long since=System.nanoTime();
                FilmCore.candidate(out,w,h,a,1);long elapsed=System.nanoTime()-since;
                if(rep==1)ms[t]=elapsed/1_000_000;
            }
        }
        System.out.println("DESKTOP performance only, 1100x770: legacy="+ms[0]+"ms, toneGuard="+ms[1]+"ms; do not extrapolate to mobile or 80MP");
    }
}