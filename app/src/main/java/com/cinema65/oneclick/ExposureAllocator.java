package com.cinema65.oneclick;

import java.util.Locale;

/**
 * Independently budgeted dodge/burn regions for a deterministic photographic edit.
 * A foreground mask is an OBJECT proposal, not a command to brighten. A depth map
 * estimates relative distance, not incident illumination. The allocator only permits
 * additional light where local photographed exposure provides evidence that an edit
 * will preserve bright/dark structure. All fields are smooth 48-ish cell lattices.
 *
 * No new cast shadows or object geometry are ever generated.
 */
public final class ExposureAllocator {
    private ExposureAllocator(){}

    public static final class Budget {
        public final int width,height;
        public final float[] subjectDodge, backgroundBurn, existingLight, highlightReserve;
        public final double averageDodge,averageBurn,backgroundHeadroom,subjectHeadroom;
        public final double semanticTrust;
        Budget(int w,int h,float[] sd,float[] bb,float[] el,float[] hr,
               double dodge,double burn,double bg,double sh,double trust){
            width=w;height=h;subjectDodge=sd;backgroundBurn=bb;
            existingLight=el;highlightReserve=hr;
            averageDodge=dodge;averageBurn=burn; backgroundHeadroom=bg;
            subjectHeadroom=sh;semanticTrust=trust;
        }
        public boolean enabled(){return semanticTrust>.08;}
        public String summary(){return String.format(Locale.US,
            "guided exposure: semantic %.2f; dodge budget %.3f, burn budget %.3f; subject room %.2f; background room %.2f",
            semanticTrust,averageDodge,averageBurn,subjectHeadroom,backgroundHeadroom);}
    }

    public static Budget compute(SpatialFusion.Plan spatial,float[] sceneLight,float[] wide,
                                 int w,int h,double median,double nocturne){
        if(spatial==null||sceneLight==null||wide==null||w<1||h<1||
           sceneLight.length!=w*h||wide.length!=w*h||spatial.width!=w||spatial.height!=h)
            throw new IllegalArgumentException("Invalid exposure scene");
        int count=w*h;
        float[] dodge=new float[count],burn=new float[count],natural=new float[count],high=new float[count];
        double subNorm=0,subjectY=0,bgNorm=0,backgroundY=0;
        for(int i=0;i<count;i++){
            double m=clamp(spatial.subject[i],0,1);
            subNorm+=m;subjectY+=m*sceneLight[i];
            bgNorm+=1-m;backgroundY+=(1-m)*sceneLight[i];
        }
        subjectY=subNorm>1e-5?subjectY/subNorm:median;
        backgroundY=bgNorm>1e-5?backgroundY/bgNorm:median;
        // These ratios reflect exposure allocation only: they are NOT semantic confidence.
        double subjectHeadroom=1-smooth(42,72,subjectY);
        double backgroundHeadroom=smooth(31,69,backgroundY);
        double sumDodge=0,sumBurn=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            double subj=clamp(spatial.subject[i],0,1);
            double local=sceneLight[i],large=wide[i];
            double lightEvidence=smooth(-12,11,local-large);
            double remainingLight=(1-smooth(50,78,local));
            double middle=smooth(10,24,local)*(1-smooth(82,97,local));
            // Cloud/sky protection happens per-pixel in FilmCore, while this grid
            // provides smooth exposure capacity independent of output resolution.
            double highlights=(1-smooth(63,85,local));
            double alreadyShadow=smooth(11,27,large);
            // A shadowed but recognisable subject may receive restrained fill;
            // no mask can create a new large hard-edged spotlight by itself.
            double shadowFill=.16+.84*lightEvidence;
            double d=subj*shadowFill*remainingLight*middle;
            // Keep the full foreground budget below 0.92 even with a very confident mask.
            dodge[i]=(float)clamp(d,0,.92);
            natural[i]=(float)lightEvidence;
            high[i]=(float)clamp(highlights,0,1);
            // Burn only comparatively well-exposed BACKGROUND, never already dark
            // distant areas. A strong subject alone cannot warrant darkening a sky.
            double brighterBg=smooth(-6,15,large-subjectY);
            double semContext=(1-smooth(.08,.55,subj));
            double depthSupport=.28+.72*clamp(spatial.far[i],0,1);
            double burnEligible=semContext*brighterBg*alreadyShadow*
                (1-smooth(76,93,local))*(1-.75*nocturne);
            double b=burnEligible*depthSupport*backgroundHeadroom;
            // A pure depth map, without credible object evidence, must not create
            // a scene-wide negative fill from this module.
            if(spatial.semanticTrust<.10){d=0;b=0;dodge[i]=0;}
            burn[i]=(float)clamp(b,0,.68);
            sumDodge+=dodge[i];sumBurn+=burn[i];
        }
        return new Budget(w,h,dodge,burn,natural,high,sumDodge/count,sumBurn/count,
                          backgroundHeadroom,subjectHeadroom,spatial.semanticTrust);
    }
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double smooth(double a,double b,double v){double x=clamp((v-a)/(b-a),0,1);return x*x*(3-2*x);}
}