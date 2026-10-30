package com.cinema65.oneclick;

import java.util.Arrays;
import java.util.Locale;

/**
 * v0.9.0 optical-narrative research, NOT a generated relight.
 * Three distinct photographic finishing hypotheses applied to the same baseline
 * output. Original photograph's actual illumination and optional trusted
 * segmentation are the ONLY sources of spatial structure. Never fabricates
 * new occlusion or cast shadows. Deterministic full-image/tile parity.
 */
public final class DirectorCuts {
    private DirectorCuts() {}
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    private static double smooth(double a,double b,double x){x=clamp((x-a)/(b-a),0,1);return x*x*(3-2*x);}
    private static double mix(double a,double b,double v){return a+(b-a)*v;}
    private static double luma(int c){return .2126*((c>>>16)&255)+.7152*((c>>>8)&255)+.0722*(c&255);}
    /** Byte-for-byte RGB mix matching FilmCore.blendOutput (including channel clamps). */
    private static int strengthMix(int raw,int edited,int strength){
        double a=clamp(strength,0,150)/100.0;
        int r=(int)Math.round(clamp(((raw>>>16)&255)+a*(((edited>>>16)&255)-((raw>>>16)&255)),0,255));
        int g=(int)Math.round(clamp(((raw>>>8)&255)+a*(((edited>>>8)&255)-((raw>>>8)&255)),0,255));
        int b=(int)Math.round(clamp((raw&255)+a*((edited&255)-(raw&255)),0,255));
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    /** Never let the optional Director Cut introduce clipping absent from the SAME
     *  baseline grade after the user's selected blend strength.
     *  These metric thresholds match CandidateRanker.Metrics.newBlack/newWhite.
     */
    static int preserveClippingBaseline(int raw,int before,int after,int strength){
        double y=luma(raw);
        int oldBlend=strengthMix(raw,before,strength),newBlend=strengthMix(raw,after,strength);
        double oldY=luma(oldBlend),newY=luma(newBlend);
        if(y>15 && oldY>3 && newY<=3)return before;
        if(y<238 && oldY<252 && newY>=252)return before;
        return after;
    }
    public static String name(int cut){return cut==0?"主体与环境":cut==1?"自然光线":cut==2?"冷暖叙事":"未知";}
    public static final class Plan {
        public final int width,height,gw,gh;
        public final boolean hasSubject;
        public final float[] focus,surround,lit,shadow;
        public final double baselineMean,subjectCoverage;
        Plan(int w,int h,int gw,int gh,boolean subject,float[] focus,float[] surround,
             float[] lit,float[] shadow,double mean,double coverage){
            width=w;height=h;this.gw=gw;this.gh=gh;hasSubject=subject;
            this.focus=focus;this.surround=surround;this.lit=lit;this.shadow=shadow;
            baselineMean=mean;subjectCoverage=coverage;
        }
        public String summary(){return String.format(Locale.US,
           "director cuts: semantic=%s coverage %.1f%%; measured illumination mean %.1f/255",
           hasSubject?"trusted":"unavailable",100*subjectCoverage,baselineMean);
        }
    }
    private static float[] blur(float[] a,int w,int h,int radius){
        if(radius<=0)return a.clone();
        float[] out=new float[a.length],tmp=new float[a.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double s=0;int n=0;
            for(int dx=-radius;dx<=radius;dx++){
                int xx=Math.max(0,Math.min(w-1,x+dx));s+=a[y*w+xx];n++;
            }
            tmp[y*w+x]=(float)(s/n);
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double s=0;int n=0;
            for(int dy=-radius;dy<=radius;dy++){
                int yy=Math.max(0,Math.min(h-1,y+dy));s+=tmp[yy*w+x];n++;
            }
            out[y*w+x]=(float)(s/n);
        }
        return out;
    }
    private static double sample(float[] a,int w,int h,double px,double py){
        double x=clamp(px*w-.5,0,w-1),y=clamp(py*h-.5,0,h-1);
        int x0=(int)x,y0=(int)y,x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
        double fx=x-x0,fy=y-y0;
        return mix(mix(a[y0*w+x0],a[y0*w+x1],fx),mix(a[y1*w+x0],a[y1*w+x1],fx),fy);
    }
    public static Plan analyze(int[] original,int w,int h,float[] modelMask,int mw,int mh,boolean credible){
        if(original==null||w<1||h<1||(long)w*h!=original.length)throw new IllegalArgumentException("Bad photo");
        if(credible&&(modelMask==null||mw<1||mh<1||modelMask.length!=(long)mw*mh))
            throw new IllegalArgumentException("Bad trusted mask");
        int gw=48,gh=48,n=gw*gh;
        float[] brightness=new float[n],subj=new float[n];int[] cnt=new int[n];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int pos=y*w+x,id=Math.min(gh-1,y*gh/h)*gw+Math.min(gw-1,x*gw/w);
            brightness[id]+=(float)luma(original[pos]);cnt[id]++;
        }
        double mean=0;
        for(int i=0;i<n;i++){brightness[i]=cnt[i]==0?128:brightness[i]/cnt[i];mean+=brightness[i];}
        mean/=n;
        if(credible){
            for(int y=0;y<gh;y++)for(int x=0;x<gw;x++){
                double sx=(x+.5)*mw/gw-.5,sy=(y+.5)*mh/gh-.5;
                int x0=(int)clamp(Math.round(sx),0,mw-1),y0=(int)clamp(Math.round(sy),0,mh-1);
                float v=modelMask[y0*mw+x0];
                if(!Float.isFinite(v))throw new IllegalArgumentException("NaN mask");
                subj[y*gw+x]=(float)clamp(v,0,1);
            }
        }
        float[] core=blur(subj,gw,gh,1),near=blur(subj,gw,gh,5),outer=blur(subj,gw,gh,10);
        double coverage=0;for(float v:subj)coverage+=v;coverage/=n;
        boolean trustworthy=credible && coverage>.015 && coverage<.64;
        if(!trustworthy)Arrays.fill(subj,0f);
        float[] surroundings=new float[n];
        for(int i=0;i<n;i++){
            double mask=trustworthy?clamp(.83*core[i]+.17*subj[i],0,1):0;
            subj[i]=(float)mask;
            // Protect foreground interior, and do not turn far-away corners into vignetting.
            double ring=(.58*near[i]+.42*outer[i])*(1-smooth(.09,.55,mask));
            surroundings[i]=(float)(trustworthy?clamp(ring,0,1):0);
        }
        float[] low=blur(brightness,gw,gh,2),wide=blur(brightness,gw,gh,9);
        float[] illuminated=new float[n],shadowed=new float[n];
        for(int i=0;i<n;i++){
            // Evidence of light already in the photograph, NOT a synthesized spotlight.
            double l=brightness[i],relative=low[i]-wide[i];
            illuminated[i]=(float)(smooth(-4,12,relative)*smooth(48,118,l));
            shadowed[i]=(float)(smooth(-3,-17,relative)*smooth(28,65,l)*(1-smooth(215,245,l)));
        }
        return new Plan(w,h,gw,gh,trustworthy,subj,surroundings,illuminated,shadowed,mean,coverage);
    }
    /** Apply finishing cut to a previously graded buffer. The original photograph is never altered. */
    public static void apply(int[] original,int[] baseline,int w,int h,Plan plan,int cut,double gain){
        apply(original,baseline,w,h,plan,cut,gain,100);
    }
    /** The final blend strength is needed to reject newly clipped pixels at 50–150%. */
    public static void apply(int[] original,int[] baseline,int w,int h,Plan plan,int cut,double gain,int finalStrength){
        if(plan==null||plan.width!=w||plan.height!=h||cut<0||cut>2||!Double.isFinite(gain)
            ||(long)w*h!=original.length||baseline.length!=original.length)
            throw new IllegalArgumentException("Bad cut state");
        gain=clamp(gain,0,1);
        if(gain==0||(cut==0&&!plan.hasSubject))return;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;baseline[i]=pixel(original[i],baseline[i],(x+.5)/w,(y+.5)/h,plan,cut,gain,finalStrength);
        }
    }
    public static int pixel(int raw,int graded,double px,double py,Plan p,int cut,double gain){
        return pixel(raw,graded,px,py,p,cut,gain,100);
    }
    /** Identical safety behavior for on-screen rendering and original-size striped export. */
    public static int pixel(int raw,int graded,double px,double py,Plan p,int cut,double gain,int finalStrength){
        if(gain<=0||(cut==0&&!p.hasSubject))return graded;
        double base=luma(graded),original=luma(raw);
        int r0=(raw>>>16)&255,g0=(raw>>>8)&255,b0=raw&255;
        double blueSky=smooth(7,30,b0-g0)*smooth(5,28,g0-r0)*smooth(75,135,original);
        double skin=smooth(5,18,r0-g0)*smooth(4,21,g0-b0)*
                    (1-smooth(50,84,r0-g0))*smooth(47,105,original)*(1-smooth(220,246,original));
        // Full luminance safety: do not crush already dark pixels or clip existing bright ones.
        double mid=smooth(18,53,original)*(1-smooth(203,241,original));
        double underexposed=smooth(35,87,original)*(1-smooth(130,195,original));
        double s=sample(p.focus,p.gw,p.gh,px,py),ring=sample(p.surround,p.gw,p.gh,px,py);
        double sun=sample(p.lit,p.gw,p.gh,px,py),sh=sample(p.shadow,p.gw,p.gh,px,py);
        double delta=0;
        if(cut==0){
            double fg=smooth(.13,.53,s),back=clamp(ring*(1-.96*fg),0,1);
            delta=6.2*fg*underexposed*(.55+.45*sun) -19.5*back*mid*(1-.93*blueSky)*(1-.75*skin);
        }else if(cut==1){
            delta=6.3*sun*mid*(1-.88*blueSky)*(1-.64*skin)
                  -8.8*sh*mid*(1-.82*blueSky)*(1-.62*skin);
            // A credible subject can lightly resist the deepest negative fill.
            delta*=1-.26*smooth(.2,.74,s);
        }else{
            delta= -3.0*mid*(1-.78*blueSky)*(1-.45*skin)*(1-.43*s);
        }
        delta*=gain;
        // Don't allow the added look to create near-black/near-white where neither
        // original nor baseline was already in that range.
        double minNew=(base>15&&original>15)?16:0;
        double maxNew=(base<240&&original<242)?239:255;
        double wanted=clamp(base+delta,minNew,maxNew);
        double ratio=base<1?1:wanted/base;
        double R=((graded>>>16)&255)*ratio,G=((graded>>>8)&255)*ratio,B=(graded&255)*ratio;
        if(cut==2){
            // Editorial split tone in existing light only. No global WB change;
            // sky and skin receive reduced color styling.
            double protection=(1-.96*skin)*(1-.88*blueSky);
            double cool=(1-sun)*mid*gain*protection;
            double warm=sun*mid*gain*protection;
            R+=warm*5.4-cool*3.7;
            G+=warm*1.3+cool*.7;
            B-=warm*4.1;B+=cool*5.2;
            // Remove local chromatic competition around an anchored foreground.
            if(p.hasSubject){
                double background=ring*(1-smooth(.10,.5,s));
                double mute=clamp(.16*background*mid*gain*(1-.95*blueSky)*(1-.9*skin),0,.13);
                double gray=.2126*R+.7152*G+.0722*B;
                R=mix(R,gray,mute);G=mix(G,gray,mute);B=mix(B,gray,mute);
            }
        }
        // Gamut compression around target grey is preferable to independent clipping.
        double hi=Math.max(R,Math.max(G,B)),lo=Math.min(R,Math.min(G,B));
        if(hi>253 || lo<2){
            double neutral=clamp(wanted,2,253);
            double factor=1;
            if(hi>253&&hi>neutral)factor=Math.min(factor,(253-neutral)/(hi-neutral));
            if(lo<2&&lo<neutral)factor=Math.min(factor,(neutral-2)/(neutral-lo));
            R=mix(neutral,R,clamp(factor,0,1));
            G=mix(neutral,G,clamp(factor,0,1));
            B=mix(neutral,B,clamp(factor,0,1));
        }
        // Optical safety guard retained at photograph's true pixel endpoints.
        double endpoint=smooth(7,17,original)*(1-smooth(243,253,original));
        R=mix((graded>>>16)&255,R,endpoint);
        G=mix((graded>>>8)&255,G,endpoint);
        B=mix(graded&255,B,endpoint);
        int rr=(int)Math.round(clamp(R,0,255)),gg=(int)Math.round(clamp(G,0,255)),bb=(int)Math.round(clamp(B,0,255));
        int candidate=0xff000000|(rr<<16)|(gg<<8)|bb;
        return preserveClippingBaseline(raw,graded,candidate,finalStrength);
    }
}