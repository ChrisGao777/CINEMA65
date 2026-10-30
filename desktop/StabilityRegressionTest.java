import com.cinema65.oneclick.*;
import java.util.Arrays;

/** Regression gates for reuse of analysis and low-allocation export blending. */
public final class StabilityRegressionTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int w=183,h=117;int[] source=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=30+(x*179/w),g=24+(y*201/h),b=20+(x+y)%151;
            source[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        FilmCore.Analysis original=FilmCore.analyze(source,w,h);
        FilmCore.Analysis copied=FilmCore.copyAnalysis(original);
        int[] a=source.clone(),b=source.clone();
        FilmCore.applyTile(a,w,h,0,0,w,h,original);
        FilmCore.applyTile(b,w,h,0,0,w,h,copied);
        check(Arrays.equals(a,b),"Analysis copy changed base rendering");
        float[] subject=new float[48*48];
        for(int y=10;y<36;y++)for(int x=15;x<31;x++)subject[y*48+x]=1;
        FilmCore.applyGuidance(copied,subject,48,48,null);
        check(!original.hasModelGuidance(),"Guidance leaked into baseline");
        int[] baselineAgain=source.clone();
        FilmCore.applyTile(baselineAgain,w,h,0,0,w,h,original);
        check(Arrays.equals(a,baselineAgain),"Baseline mutated after guidance");
        for(int level:new int[]{0,50,100,150}){
            int[] expected=a.clone(),actual=a.clone();
            FilmCore.blendOutput(source,expected,level);
            int[] tail=Arrays.copyOf(actual,actual.length+5);
            Arrays.fill(tail,actual.length,tail.length,0x87654321);
            FilmCore.blendOutputPrefix(source,tail,actual.length,level);
            check(Arrays.equals(expected,Arrays.copyOf(tail,actual.length)),"Prefix blend changed RGB");
            for(int i=actual.length;i<tail.length;i++)check(tail[i]==0x87654321,"Wrote unused stripe tail");
        }
        check(ExportMemoryPolicy.rejection(4000,3000,500L<<20,2L<<30,false)==null,
            "Normal 12MP photograph should pass on a suitably provisioned device");
        check(ExportMemoryPolicy.rejection(12000,9000,512L<<20,2L<<30,false)!=null,
            "Oversized mutable decode must be rejected before allocating");
        check(ExportMemoryPolicy.rejection(4000,3000,500L<<20,2L<<30,true)!=null,
            "Android low-memory signal must veto export");
        System.out.println("Stability regression PASSED: analysis reuse, baseline isolation, stripe blend and memory budget");
    }
}
