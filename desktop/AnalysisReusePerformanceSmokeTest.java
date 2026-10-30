import com.cinema65.oneclick.FilmCore;
import java.util.Locale;

/** Indicative desktop microbenchmark, not a phone runtime guarantee. */
public final class AnalysisReusePerformanceSmokeTest {
    public static void main(String[] args){
        int w=960,h=720;int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=25+220*x/w,g=20+210*y/h,b=25+(x+3*y)%200;
            pixels[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        for(int i=0;i<2;i++)FilmCore.copyAnalysis(FilmCore.analyze(pixels,w,h));
        long oldNs=0,newNs=0;
        for(int i=0;i<4;i++){
            long begin=System.nanoTime();
            FilmCore.Analysis first=FilmCore.analyze(pixels,w,h);
            FilmCore.Analysis second=FilmCore.analyze(pixels,w,h);
            oldNs+=System.nanoTime()-begin;
            if(first.selectedTreatment!=second.selectedTreatment)
                throw new AssertionError("Non-deterministic analysis");
            begin=System.nanoTime();
            FilmCore.Analysis base=FilmCore.analyze(pixels,w,h);
            FilmCore.Analysis guidance=FilmCore.copyAnalysis(base);
            newNs+=System.nanoTime()-begin;
            if(base.selectedTreatment!=guidance.selectedTreatment)
                throw new AssertionError("Copy did not retain treatment");
        }
        System.out.printf(Locale.US,"Desktop 960x720 analysis pair: repeated %.1f ms, shared %.1f ms (%.2fx speedup); NOT Android wall time%n",
            oldNs/4e6,newNs/4e6,(double)oldNs/newNs);
    }
}
