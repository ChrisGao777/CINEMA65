package com.cinema65.oneclick;

import java.util.Locale;

/** Research scene-conditioned orchestration, independent of NIMA and semantic detection.
 * Three strategies remain alternatives, not three additive edits. Never infers new light.
 * Input ranges are measured from the ORIGINAL photo and credible low-resolution masks.
 */
public final class StrategyDirector {
    private StrategyDirector() {}
    public static final class Plan {
        public final boolean active;
        public final double[] dodge, recession, negative, natural, context, color;
        public final double litSubject, litConfidence, trust;
        public final String reason;
        Plan(boolean active, double[] dodge,double[] recession,double[] negative,
             double[] natural,double[] context,double[] color,
             double litSubject,double confidence,double trust,String reason){
            this.active=active;this.dodge=dodge;this.recession=recession;
            this.negative=negative;this.natural=natural;this.context=context;
            this.color=color;this.litSubject=litSubject;this.litConfidence=confidence;
            this.trust=trust;this.reason=reason;
        }
        public String summary(){return String.format(Locale.US,
            "%s · subject L*=%.1f · lit %.2f · semantic %.2f; subject/space/light context gains %.2f/%.2f/%.2f; %s",
            active?"responsive strategies":"legacy identical",litSubject,litConfidence,trust,
            dodge[0],recession[1],natural[2],reason);
        }
    }
    private static Plan identity(String reason){
        double[] a={1,1,1};return new Plan(false,a.clone(),a.clone(),a.clone(),a.clone(),a.clone(),
            new double[]{0,0,0},0,0,0,reason);
    }
    public static Plan plan(SpatialFusion.Plan scene,ExposureAllocator.Budget exposure,
                            EnvironmentSculptor.Budget environment,float[] illumination,
                            double nocturne){
        if(scene==null||exposure==null||illumination==null||illumination.length!=scene.width*scene.height)
            return identity("no reliable evidence");
        boolean object=scene.semanticTrust>.35;
        boolean depth=scene.depthTrust>.27;
        if(!object&&!depth)return identity("subject and depth untrusted");
        double mass=0,total=0;
        for(int i=0;i<illumination.length;i++){
            double m=scene.subject[i];mass+=m;total+=m*illumination[i];
        }
        double mean=mass>1e-6?total/mass:0;
        double lit=object?smooth(51,72,mean):0;
        double shadow=object?(1-smooth(39,67,mean)):0;
        double d=depth?smooth(.24,.64,scene.depthTrust):0;
        double n=clamp(nocturne,0,1);
        // Subject presence accent: retain shadows, focus existing light; tone safety
        // and the per-pixel material guard still set the actual legal exposure.
        double[] dodge={1+.21*shadow-.12*lit,1-.13*shadow-.10*lit,1-.25*shadow-.18*lit};
        double[] recession={1-.17*d,1+.26*d*(1-.65*n),1-.11*d};
        double[] negative={1-.13*lit,1+.14*d,1-.15*lit};
        double[] natural={1-.08*lit,1-.13*lit,1+.23*lit+.06*shadow};
        // A lit subject needs spatial/color context, not an additional spotlight.
        double[] context={1+.08*lit,1+.09*lit,1+.22*lit};
        // This multiplier applies ONLY to a background-color capacity map. It is
        // intentionally absent for depth-only scenes: depth has no object identity.
        double[] color=object?new double[]{.70,1.,1.28}:new double[]{0,0,0};
        if(!object){for(int i=0;i<3;i++){dodge[i]=1;context[i]=1;natural[i]=1;}}
        return new Plan(true,dodge,recession,negative,natural,context,color,mean,lit,
            scene.semanticTrust,object?"subject-anchored budgets":"relative depth only");
    }
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static double smooth(double a,double b,double x){double t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
}