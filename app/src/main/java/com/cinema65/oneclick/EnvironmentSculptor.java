package com.cinema65.oneclick;

import java.util.Locale;

/**
 * Subject-relative, non-generative environment shaping. A bright semantic object
 * can gain presence by a restrained, *nearby* mid-tone background recession.
 * The subject mask is never a command to relight the subject, and depth alone
 * cannot enable this budget. The pre-existing light field limits each edit.
 *
 * 48-ish grid with separable Gaussian mask blur: no centered vignette or
 * fabricated hard cast shadows. Final per-pixel sky / highlight / dark guards
 * live in FilmCore, where the original color is available.
 */
public final class EnvironmentSculptor {
    private EnvironmentSculptor(){}
    public static final class Budget {
        public final int width,height;
        public final float[] backgroundRecession;
        public final double weightedMean,litSubject,trust,coverage;
        public final String reason;
        Budget(int w,int h,float[] b,double mean,double lit,double t,double c,String r){
            width=w;height=h;backgroundRecession=b;weightedMean=mean;
            litSubject=lit;trust=t;coverage=c;reason=r;
        }
        public boolean active(){return trust>.35 && weightedMean>.0001;}
        public String summary(){return String.format(Locale.US,
            "environment budget %.4f; subject L* %.1f; semantic trust %.2f; coverage %.1f%%; %s",
            weightedMean,litSubject,trust,coverage*100,reason);
        }
    }
    public static Budget compute(SpatialFusion.Plan plan,float[] sceneLight,float[] wide,
                                 int w,int h,double nocturne){
        if(plan==null||sceneLight==null||wide==null||w<1||h<1||
           sceneLight.length!=w*h||wide.length!=w*h||plan.width!=w||plan.height!=h)
            throw new IllegalArgumentException("Invalid environment inputs");
        float[] out=new float[w*h];
        if(plan.semanticTrust<=.35)
            return new Budget(w,h,out,0,0,plan.semanticTrust,0,"no trusted semantic anchor");
        float[] normalized=new float[w*h];double weight=0,lit=0;
        for(int i=0;i<normalized.length;i++){
            normalized[i]=(float)clamp(plan.subject[i]/plan.semanticTrust,0,1);
            weight+=normalized[i];lit+=normalized[i]*sceneLight[i];
        }
        double coverage=weight/normalized.length;
        if(coverage<.006||coverage>.58)
            return new Budget(w,h,out,0,0,plan.semanticTrust,coverage,"implausible foreground size");
        double subjectL=lit/Math.max(1e-6,weight);
        // The budget is for subject ALREADY BRIGHT. Shadowed portraits get the
        // older controlled subject fill, never synthetic surrounding darkness.
        double litEnough=smooth(61,69,subjectL);
        if(litEnough<.012)
            return new Budget(w,h,out,0,subjectL,plan.semanticTrust,coverage,"subject not bright enough");
        float[] adjacent=blur(normalized,w,h,3.6);
        double total=0;
        for(int i=0;i<out.length;i++){
            double semantic=normalized[i];
            // Restrict sculpt to outside the object, smoothly, leaving silhouette
            // and object holes to the per-pixel material guards.
            double exterior=1-smooth(.06,.34,semantic);
            double around=smooth(.018,.21,adjacent[i]);
            double local=sceneLight[i],ambient=wide[i];
            double exposed=smooth(31,51,local)*(1-smooth(78,91,local));
            double retainedShadows=smooth(26,46,ambient);
            // Existing local illumination drives shape: do not darken already
            // deep shadow, nor all far scenery merely because its depth is high.
            double existingLight=.54+.46*smooth(-8,12,local-ambient);
            double b=plan.semanticTrust*litEnough*exterior*around*exposed*
                     retainedShadows*existingLight*(1-.72*clamp(nocturne,0,1));
            out[i]=(float)clamp(b,0,.72);
            total+=out[i];
        }
        return new Budget(w,h,out,total/out.length,subjectL,
            plan.semanticTrust,coverage,"local midtone context, no global vignette");
    }
    private static float[] blur(float[] src,int w,int h,double sigma){
        int rad=(int)Math.ceil(sigma*2.7);double[] k=new double[rad*2+1];double norm=0;
        for(int i=-rad;i<=rad;i++){double v=Math.exp(-.5*i*i/(sigma*sigma));k[i+rad]=v;norm+=v;}
        for(int i=0;i<k.length;i++)k[i]/=norm;
        float[] tmp=new float[w*h],out=new float[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double val=0;for(int i=-rad;i<=rad;i++)val+=src[y*w+Math.max(0,Math.min(w-1,x+i))]*k[i+rad];
            tmp[y*w+x]=(float)val;
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double val=0;for(int i=-rad;i<=rad;i++)val+=tmp[Math.max(0,Math.min(h-1,y+i))*w+x]*k[i+rad];
            out[y*w+x]=(float)val;
        }
        return out;
    }
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static double smooth(double a,double b,double v){double x=clamp((v-a)/(b-a),0,1);return x*x*(3-2*x);}
}