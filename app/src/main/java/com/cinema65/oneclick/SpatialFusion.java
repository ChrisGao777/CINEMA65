package com.cinema65.oneclick;

import java.util.Arrays;
import java.util.Locale;

/**
 * CPU-only, deterministic evidence fusion. This is not a relighting neural network.
 * Saliency (what attracts the eye), semantics (what an object is), relative depth
 * (what is nearer), and illumination (where light already exists) remain separate.
 *
 * All outputs are low-frequency, normalized spatial fields in full-photo coordinates;
 * they can be sampled identically by preview rendering and full-resolution tiles.
 */
public final class SpatialFusion {
    private SpatialFusion() {}

    public static final class Plan {
        public final int width,height;
        public final float[] subject,far,near,light;
        public final double semanticTrust,depthTrust,anchorDepth,farCoverage;
        public final String semanticReason,depthReason;
        Plan(int w,int h,float[] s,float[] f,float[] n,float[] l,
             double st,double dt,double anchor,double cover,String sr,String dr){
            width=w;height=h;subject=s;far=f;near=n;light=l;
            semanticTrust=st;depthTrust=dt;anchorDepth=anchor;farCoverage=cover;
            semanticReason=sr;depthReason=dr;
        }
        public boolean hasGuidance(){return semanticTrust>0.08||depthTrust>0.14;}
        public String summary(){
            return String.format(Locale.US,
                "semantic %.2f [%s], depth %.2f [%s], reference %.2f, far %.1f%%",
                semanticTrust,semanticReason,depthTrust,depthReason,anchorDepth,100*farCoverage);
        }
    }

    public static Plan build(float[] attention,float[] illumination,float[] wide,int w,int h,
                             float[] semantic,float[] depth,int mw,int mh,double saliencyTrust){
        if(w<1||h<1||attention.length!=w*h||illumination.length!=w*h||wide.length!=w*h)
            throw new IllegalArgumentException("Bad scene grid");
        if(mw<1||mh<1)throw new IllegalArgumentException("Bad model grid");
        if((semantic!=null&&semantic.length!=mw*mh)||(depth!=null&&depth.length!=mw*mh))
            throw new IllegalArgumentException("Bad model map shape");
        boolean semanticInvalid=false,depthInvalid=false;
        if(semantic!=null)for(float v:semantic)if(!Float.isFinite(v)){semanticInvalid=true;break;}
        if(depth!=null)for(float v:depth)if(!Float.isFinite(v)){depthInvalid=true;break;}
        // Invalid inference is evidence of model failure, not a reason to corrupt an image.
        if(semanticInvalid)semantic=null;
        if(depthInvalid)depth=null;
        float[] s=new float[w*h],d=new float[w*h],light=new float[w*h];
        double area=0,peak=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            double mx=(x+.5)*mw/w-.5,my=(y+.5)*mh/h-.5;
            s[i]=semantic==null?0f:(float)clamp(sample(semantic,mw,mh,mx,my),0,1);
            d[i]=depth==null?.5f:(float)clamp(sample(depth,mw,mh,mx,my),0,1);
            light[i]=(float)clamp(.34+.66*smooth(-8,17,illumination[i]-wide[i]),.2,1);
            area+=s[i];peak=Math.max(peak,s[i]);
        }
        area/=s.length;
        double semanticTrust=0;
        String semanticReason=semanticInvalid?"non-finite model values":"no mask";
        if(semantic!=null){
            if(area<.0025 || area>.82 || peak<.52)semanticReason="implausible coverage/strength";
            else {
                // A small face can be credible; a whole-frame segmentation cannot.
                semanticTrust=clamp(.45+.45*smooth(.005,.065,area),.40,.9)
                    *smooth(.50,.92,peak)*(1-.60*smooth(.63,.82,area));
                semanticReason="coverage="+String.format(Locale.US,"%.3f",area);
            }
        }
        double depthTrust=0,anchor=.5;
        String depthReason=depthInvalid?"non-finite model values":"no map";
        if(depth!=null){
            float[] ds=d.clone();Arrays.sort(ds);
            double p10=ds[(int)(.10*(ds.length-1))],p90=ds[(int)(.90*(ds.length-1))];
            double spread=p90-p10;
            double rough=0,variance=0,mean=0;
            for(float z:d)mean+=z;mean/=d.length;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int i=y*w+x;double v=d[i];variance+=(v-mean)*(v-mean);
                if(x>0)rough+=(v-d[i-1])*(v-d[i-1]);
                if(y>0)rough+=(v-d[i-w])*(v-d[i-w]);
            }
            variance/=d.length;rough/=Math.max(1,2*w*h-w-h);
            double grain=variance<1e-8?9:rough/(variance+1e-7);
            if(spread<.11)depthReason="low spread";
            else if(grain>1.15)depthReason="spatially noisy";
            else {
                depthTrust=.90*smooth(.11,.42,spread)*(1-.75*smooth(.42,1.15,grain));
                // A depth map alone cannot decide WHAT the subject is. If there is
                // no semantic object, require compositional evidence as anchor.
                if(semanticTrust<.10)depthTrust*=smooth(.07,.38,saliencyTrust);
                depthReason=String.format(Locale.US,"spread %.2f, noise %.2f, anchor %.2f",spread,grain,saliencyTrust);
            }
        }
        // Reference depth: semantic foreground when credible, otherwise a measured
        // visual-attention region. Depth on its own MUST NOT create a subject spotlight.
        double weighted=0,norm=0;
        for(int i=0;i<d.length;i++){
            double a=semanticTrust>.10 ? semanticTrust*s[i] : smooth(.24,.62,attention[i]);
            weighted+=d[i]*a;norm+=a;
        }
        if(norm>1e-6)anchor=weighted/norm;
        else {for(float v:d)anchor+=v/d.length;anchor-=.5;}
        float[] sub=new float[w*h],far=new float[w*h],near=new float[w*h];
        double farSum=0;
        for(int i=0;i<sub.length;i++){
            double attn=clamp(attention[i],0,1);
            sub[i]=(float)(semanticTrust*s[i]);
            double delta=anchor-d[i]; // MiDaS inverse depth: lower = farther.
            double farRaw=smooth(.08,.30,delta);
            // Suppress near/far corrections where the image offers no reliable
            // subject anchor. Also suppress broad background edits at unknown sky.
            double context=semanticTrust>.10 ? (1-.86*s[i]) : (1-.68*smooth(.15,.65,attn));
            double conservative=semanticTrust>.10?1:.66;
            far[i]=(float)(depthTrust*conservative*farRaw*context);
            near[i]=(float)(depthTrust*smooth(.08,.30,-delta)*
                  (semanticTrust>.10?s[i]:.25*smooth(.30,.70,attn)));
            farSum+=far[i];
        }
        return new Plan(w,h,sub,far,near,light,semanticTrust,depthTrust,anchor,
                        farSum/far.length,semanticReason,depthReason);
    }

    private static double sample(float[] v,int w,int h,double px,double py){
        double x=clamp(px,0,w-1),y=clamp(py,0,h-1);
        int x0=(int)x,y0=(int)y,x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
        double dx=x-x0,dy=y-y0;
        return (1-dy)*((1-dx)*v[y0*w+x0]+dx*v[y0*w+x1])+
                    dy*((1-dx)*v[y1*w+x0]+dx*v[y1*w+x1]);
    }
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(x,b));}
    private static double smooth(double a,double b,double v){double x=clamp((v-a)/(b-a),0,1);return x*x*(3-2*x);}
}