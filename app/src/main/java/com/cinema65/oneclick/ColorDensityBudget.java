package com.cinema65.oneclick;

import java.util.Locale;

/** Background-only spatial color competition budget, computed from ORIGINAL pixels.
 * Never modifies source data and does not apply artificial global desaturation.
 * A credible whole-object mask is required; depth alone is insufficient.
 */
public final class ColorDensityBudget {
    private ColorDensityBudget() {}
    public static final class Plan {
        public final float[] capacity;
        public final int width,height;
        public final double mean,subjectChroma,backgroundChroma,trust;
        public final String reason;
        Plan(float[] c,int w,int h,double mean,double s,double b,double t,String why){
            capacity=c;width=w;height=h;this.mean=mean;subjectChroma=s;
            backgroundChroma=b;trust=t;reason=why;
        }
        public boolean active(){return trust>.35&&mean>.00003;}
        public String summary(){return String.format(Locale.US,
            "color-competition budget %.4f; subject chroma %.3f, background %.3f, trust %.2f; %s",
            mean,subjectChroma,backgroundChroma,trust,reason);
        }
    }
    public static Plan compute(SpatialFusion.Plan spatial,float[] luminance,
                               float[] r,float[] g,float[] b,int w,int h){
        if(spatial==null||luminance==null||r==null||g==null||b==null||
           luminance.length!=w*h||r.length!=w*h||g.length!=w*h||b.length!=w*h||
           spatial.width!=w||spatial.height!=h)
            throw new IllegalArgumentException("color map dimension mismatch");
        float[] result=new float[w*h];
        if(spatial.semanticTrust<=.35)return new Plan(result,w,h,0,0,0,spatial.semanticTrust,"no trusted object");
        float[] subj=new float[w*h];
        double weight=0,chromaticSubject=0,chromaticBackground=0,bgweight=0;
        for(int i=0;i<subj.length;i++){
            subj[i]=(float)clamp(spatial.subject[i]/spatial.semanticTrust,0,1);
            double ch=chroma(r[i],g[i],b[i]);
            weight+=subj[i];chromaticSubject+=subj[i]*ch;
            double bg=1-subj[i];bgweight+=bg;chromaticBackground+=bg*ch;
        }
        double coverage=weight/subj.length;
        double meanSub=chromaticSubject/Math.max(.000001,weight);
        double meanBg=chromaticBackground/Math.max(.000001,bgweight);
        if(coverage<.008||coverage>.60)return new Plan(result,w,h,0,meanSub,meanBg,spatial.semanticTrust,"object too small or too broad");
        float[] near=blur(subj,w,h,Math.max(.9,Math.min(w,h)*.065));
        double sum=0;
        for(int i=0;i<result.length;i++){
            double raw=subj[i];
            double inBackground=1-smooth(.055,.42,raw);
            double neighborhood=smooth(.035,.25,near[i]);
            double ch=chroma(r[i],g[i],b[i]);
            // Preserve sky and protected skin at full resolution downstream.
            double competing=smooth(.12,.29,ch);
            double midtone=smooth(23,42,luminance[i])*(1-smooth(79,93,luminance[i]));
            double capacity=spatial.semanticTrust*inBackground*neighborhood*competing*midtone;
            result[i]=(float)clamp(capacity,0,.90);sum+=result[i];
        }
        return new Plan(result,w,h,sum/result.length,meanSub,meanBg,spatial.semanticTrust,
            "local chromatic backdrop; preserve neutral scene and sky");
    }
    private static float[] blur(float[] src,int w,int h,double sigma){
        int radius=Math.max(1,(int)Math.ceil(2.7*sigma));
        double[] kernel=new double[2*radius+1];double norm=0;
        for(int i=-radius;i<=radius;i++){double v=Math.exp(-.5*i*i/(sigma*sigma));kernel[i+radius]=v;norm+=v;}
        for(int i=0;i<kernel.length;i++)kernel[i]/=norm;
        float[] tmp=new float[src.length],out=new float[src.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double v=0;for(int i=-radius;i<=radius;i++)v+=kernel[i+radius]*src[y*w+clamp(x+i,0,w-1)];
            tmp[y*w+x]=(float)v;
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double v=0;for(int i=-radius;i<=radius;i++)v+=kernel[i+radius]*tmp[clamp(y+i,0,h-1)*w+x];
            out[y*w+x]=(float)v;
        }
        return out;
    }
    private static double chroma(float r,float g,float b){double hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b));return (hi-lo)/255.;}
    private static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static double smooth(double a,double b,double x){double t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
}