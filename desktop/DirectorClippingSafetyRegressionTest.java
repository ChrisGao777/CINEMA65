import com.cinema65.oneclick.*;
import java.util.Arrays;

/** Prevent default natural-light and all other Director Cuts from adding 
 *  genuinely new black/white clipping at any supported strength.
 */
public final class DirectorClippingSafetyRegressionTest {
    private static void ok(boolean test,String info){if(!test)throw new AssertionError(info);}
    private static double y(int p){return .2126*((p>>>16)&255)+.7152*((p>>>8)&255)+.0722*(p&255);}
    private static int blend(int a,int b,int strength){
        int[] source={a},dest={b};FilmCore.blendOutput(source,dest,strength);return dest[0];
    }
    public static void main(String[] args){
        int width=192,height=192;int[] source=new int[width*height];
        for(int yy=0;yy<height;yy++)for(int xx=0;xx<width;xx++){
            int k=yy*width+xx,lum;
            if(yy>=45&&yy<145&&xx>=45&&xx<145)lum=80+xx%9;
            else if(xx>145)lum=230-yy%15;
            else lum=140+yy%25;
            source[k]=0xff000000|(lum<<16)|(lum<<8)|lum;
        }
        float[] mask=new float[48*48];
        for(int yy=12;yy<35;yy++)for(int xx=12;xx<35;xx++)mask[yy*48+xx]=1;
        DirectorCuts.Plan p=DirectorCuts.analyze(source,width,height,mask,48,48,true);
        ok(p.hasSubject,"subject should be credible");
        int checked=0,changed=0;
        for(int strength:new int[]{50,100,150})for(int cut=0;cut<3;cut++){
            int[] base=source.clone();
            // Grade near the black limit and near the white limit, where
            // otherwise-small editor deltas can suddenly force clipping.
            for(int k=0;k<base.length;k++){
                int lum=((k/width)<70)?(3+k%9):((k/width)>155?247+k%8:50+k%25);
                base[k]=0xff000000|(lum<<16)|(lum<<8)|lum;
            }
            int[] actual=base.clone();
            DirectorCuts.apply(source,actual,width,height,p,cut,1,strength);
            for(int k=0;k<base.length;k++){
                int before=blend(source[k],base[k],strength);
                int after=blend(source[k],actual[k],strength);
                double raw=y(source[k]);
                if(raw>15 && y(before)>3)ok(y(after)>3,"new clipped black: cut "+cut+", strength "+strength+", pixel "+k);
                if(raw<238 && y(before)<252)ok(y(after)<252,"new clipped white: cut "+cut+", strength "+strength+", pixel "+k);
                if(actual[k]!=base[k])changed++;
                checked++;
            }
        }
        ok(changed>0,"gate should retain non-clipping creative edits");
        System.out.println("Director clipping safety PASS: "+checked+" pixels, "+changed+" valid edits, strengths 50/100/150");
    }
}
