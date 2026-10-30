import com.cinema65.oneclick.FilmCore;
import java.util.Arrays;
import java.util.Random;

public class CoreRegressionTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        final int w=1001,h=617;
        int[] image=new int[w*h];Random rand=new Random(77);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=(x*255)/(w-1),g=(y*255)/(h-1),b=(r+g+rand.nextInt(50))%256;
            image[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        FilmCore.Analysis a=FilmCore.analyze(image,w,h);
        check(a.styleStrength>=.55&&a.styleStrength<=.98,"Invalid strength");
        check(a.nocturne>=0&&a.nocturne<=1,"Invalid scene blend");
        int[] once=image.clone(),twice=image.clone();
        FilmCore.applyTile(once,w,h,0,0,w,h,a);
        FilmCore.applyTile(twice,w,h,0,0,w,h,a);
        check(Arrays.equals(once,twice),"Non-deterministic result");
        // Global-coordinate tiled processing must be bit-identical, even on partial final row.
        int[] tiled=image.clone();int rows=49;
        for(int y=0;y<h;y+=rows){
            int count=Math.min(rows,h-y);int[] temp=new int[w*count];
            System.arraycopy(tiled,y*w,temp,0,temp.length);
            FilmCore.applyTile(temp,w,count,0,y,w,h,a);
            System.arraycopy(temp,0,tiled,y*w,temp.length);
        }
        check(Arrays.equals(tiled,once),"Stripe processing has seams or mismatched output");
        int[] allBlack=new int[10000];Arrays.fill(allBlack,0xff000000);
        FilmCore.Analysis black=FilmCore.grade(allBlack,100,100);
        for(int p:allBlack)check((p&0xffffff)<0x020202,"Black damaged");
        int[] white=new int[10000];Arrays.fill(white,0xffffffff);
        FilmCore.grade(white,100,100);
        for(int p:white)check((p&0xffffff)>=0xfcfcfc,"White damaged");
        int[] night=new int[10000];Arrays.fill(night,0xff222533);
        FilmCore.Analysis dark=FilmCore.analyze(night,100,100);
        int[] day=new int[10000];Arrays.fill(day,0xffd6c7b3);
        FilmCore.Analysis light=FilmCore.analyze(day,100,100);
        check(dark.nocturne>light.nocturne,"Scene analysis not adaptive");
        check(!Arrays.equals(once,image),"Filter is identity");
        boolean thrown=false;
        try{FilmCore.applyTile(new int[20],4,5,w-1,h-1,w,h,a);}catch(IllegalArgumentException e){thrown=true;}
        check(thrown,"Invalid geometry not rejected");
        System.out.println("PASS: deterministic; preview/stripe identical; protect blacks/whites; adaptive; input safety");
        System.out.println("Sample analysis: "+a.summary()+"; night="+dark.nocturne+"; day="+light.nocturne);
    }
}