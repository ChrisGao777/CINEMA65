import com.cinema65.oneclick.*;
import java.util.Arrays;
/** Exercises v0.9.0 preview/export paths and all three cuts at non-100% strength. */
public final class DirectorIntegrationRegressionTest {
    private static void demand(boolean x,String msg){if(!x)throw new AssertionError(msg);}
    public static void main(String[] args){
        int w=211,h=143;int[] orig=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=Math.min(240,30+x%190),g=Math.min(230,25+y%175),b=Math.min(230,55+(x+y)%160);
            orig[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        float[] mask=new float[48*48];
        for(int y=8;y<39;y++)for(int x=16;x<34;x++)mask[y*48+x]=1f;
        DirectorCuts.Plan plan=DirectorCuts.analyze(orig,w,h,mask,48,48,true);
        demand(plan.hasSubject,"credible object should be used");
        FilmCore.Analysis analysis=FilmCore.analyze(orig,w,h);
        analysis.selectedTreatment=1;
        for(int cut=0;cut<3;cut++)for(int strength:new int[]{50,100,150}){
            int[] preview=orig.clone(),before=orig.clone();
            FilmCore.applyTile(preview,w,h,0,0,w,h,analysis);
            DirectorCuts.apply(before,preview,w,h,plan,cut,1.0);
            FilmCore.blendOutput(before,preview,strength);
            int[] stripes=new int[w*h];
            for(int y=0;y<h;y+=17){
                int rows=Math.min(17,h-y);
                int[] part=Arrays.copyOfRange(orig,y*w,(y+rows)*w);
                int[] ungraded=part.clone();
                FilmCore.applyTile(part,w,rows,0,y,w,h,analysis);
                for(int yy=0;yy<rows;yy++)for(int x=0;x<w;x++){
                    int k=yy*w+x;
                    part[k]=DirectorCuts.pixel(ungraded[k],part[k],(x+.5)/(double)w,(y+yy+.5)/(double)h,plan,cut,1.0);
                }
                FilmCore.blendOutput(ungraded,part,strength);
                System.arraycopy(part,0,stripes,y*w,part.length);
            }
            demand(Arrays.equals(preview,stripes),"striped export differs from preview cut="+cut+" strength="+strength);
        }
        // Cut 0 must have exact zero impact if no trustworthy segmentation exists.
        DirectorCuts.Plan noModel=DirectorCuts.analyze(orig,w,h,null,0,0,false);
        int[] original=orig.clone();
        DirectorCuts.apply(orig,original,w,h,noModel,0,1.0);
        demand(Arrays.equals(orig,original),"no semantic subject cannot fabricate relighting");
        System.out.println("DirectorIntegration regression PASSED: 3 cuts x 3 strengths, full-size tile parity and mask fallback");
    }
}