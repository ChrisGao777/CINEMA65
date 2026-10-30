package com.cinema65.oneclick;

import java.util.Locale;

/**
 * Model contribution safety gate. Compare a MODEL-GUIDED render to the SAME
 * grade with NO model guidance: the traditional look may already be high-contrast,
 * and a model should not be penalized for artifacts it did not introduce.
 * The outcome applies identically to preview and full-resolution export.
 *
 * These are conservative research heuristics, NOT learned photographic quality.
 */
public final class ModelRiskAuditor {
    private ModelRiskAuditor(){}
    public static final class Decision {
        public final double gain,additionalBlack,additionalWhite,additionalInventedEdges;
        public final String reason;
        Decision(double g,double b,double w,double e,String r){
            gain=g;additionalBlack=b;additionalWhite=w;additionalInventedEdges=e;reason=r;
        }
        public String summary(){return String.format(Locale.US,
            "model exposure weight %.0f%%; incremental black %.3f%%, white %.3f%%, artificial edges %.4f%%; %s",
            100*gain,100*additionalBlack,100*additionalWhite,100*additionalInventedEdges,reason);
        }
    }
    /**
     * Evaluates three manual/automatic styles, not only the current selected style.
     * Considers .00 / .45 / 1.00 gain; the mild state is preferred only if full
     * gain adds measurable injury. Rejects an entire model edit if both fail.
     */
    /** Permissive research ceilings, NOT image-quality confidence values. */
    public static boolean withinLimits(double extraBlack,double extraWhite,
                                       double extraEdge,double extraReversal){
        if(!Double.isFinite(extraBlack)||!Double.isFinite(extraWhite)||
           !Double.isFinite(extraEdge)||!Double.isFinite(extraReversal))return false;
        return extraBlack<=.0015&&extraWhite<=.0020&&
               extraEdge<=.00030&&extraReversal<=.00020;
    }
    public static Decision calibrate(FilmCore.Analysis guided,FilmCore.Analysis pure,
                                     int[] original,int w,int h){
        if(guided==null||pure==null||original==null||(long)w*h!=original.length)
            throw new IllegalArgumentException("Bad gate inputs");
        if(!guided.hasModelGuidance()){
            FilmCore.setGuidanceGain(guided,0);
            return new Decision(0,0,0,0,"no credible model evidence");
        }
        double[][] old=new double[3][];
        for(int i=0;i<3;i++){
            int[] plain=original.clone();FilmCore.candidate(plain,w,h,pure,i);
            CandidateRanker.Metrics m=new CandidateRanker.Metrics(original,plain,w,h);
            old[i]=new double[]{m.newBlack,m.newWhite,m.inventedEdgeRate,m.edgeReversalRate};
        }
        Decision last=null;
        for(double g:new double[]{1,.45}){
            FilmCore.setGuidanceGain(guided,g);
            double maxBlack=0,maxWhite=0,maxEdges=0,maxReversal=0;
            for(int i=0;i<3;i++){
                int[] output=original.clone();FilmCore.candidate(output,w,h,guided,i);
                CandidateRanker.Metrics m=new CandidateRanker.Metrics(original,output,w,h);
                maxBlack=Math.max(maxBlack,Math.max(0,m.newBlack-old[i][0]));
                maxWhite=Math.max(maxWhite,Math.max(0,m.newWhite-old[i][1]));
                maxEdges=Math.max(maxEdges,Math.max(0,m.inventedEdgeRate-old[i][2]));
                maxReversal=Math.max(maxReversal,Math.max(0,m.edgeReversalRate-old[i][3]));
            }
            boolean safe=withinLimits(maxBlack,maxWhite,maxEdges,maxReversal);
            last=new Decision(g,maxBlack,maxWhite,maxEdges,
                safe?(g==1?"full budget passed":"scaled down to pass damage gate"):
                     "over budget: additional clipped/detail damage");
            if(safe)return last;
        }
        FilmCore.setGuidanceGain(guided,0);
        return new Decision(0,last.additionalBlack,last.additionalWhite,
                            last.additionalInventedEdges,"model contribution vetoed; pure grade restored");
    }
}