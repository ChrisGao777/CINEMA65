package com.cinema65.oneclick;

import java.util.Locale;

/**
 * Compare actual candidate renders. NIMA is one (not the only) judge.
 * Do not interpret hundredths of a NIMA point as a reliable aesthetic victory.
 * A candidate is rejected if it creates significantly more hard clipping.
 */
public final class CandidateRanker {
    private CandidateRanker(){}
    public static final class Metrics {
        public final double newBlack,newWhite,meanAbsDifference,meanLumaChange;
        /** Fraction of neighboring initially smooth pixel pairs with a newly sharp edge. */
        public final double inventedEdgeRate,edgeReversalRate;
        public Metrics(int[] original,int[] changed){this(original,changed,0,0);}
        public Metrics(int[] original,int[] changed,int width,int height){
            if((width==0)!=(height==0) || (width!=0 && (long)width*height!=original.length))
                throw new IllegalArgumentException("Damage metric dimensions mismatch");
            if(original.length!=changed.length||original.length==0)throw new IllegalArgumentException("Frame size mismatch");
            int black=0,white=0;double difference=0,luma=0;
            for(int i=0;i<original.length;i++){
                int a=original[i],b=changed[i];
                int ar=(a>>>16)&255,ag=(a>>>8)&255,ab=a&255;
                int br=(b>>>16)&255,bg=(b>>>8)&255,bb=b&255;
                double aY=.2126*ar+.7152*ag+.0722*ab;
                double bY=.2126*br+.7152*bg+.0722*bb;
                if(aY>15 && bY<=3)black++;
                if(aY<238 && bY>=252)white++;
                difference+=(Math.abs(ar-br)+Math.abs(ag-bg)+Math.abs(ab-bb))/3.;
                luma+=bY-aY;
            }
            int n=original.length;
            newBlack=black/(double)n;newWhite=white/(double)n;
            meanAbsDifference=difference/n;meanLumaChange=luma/n;
            double invented=0,reversed=0,total=0;
            if(width>1&&height>1){
                // Compare ORIGINAL versus EDITED edge transitions. A natural object boundary
                // is allowed to become more contrasty, but a hard edge invented in an
                // originally flat region is suspicious (segmentation halo / mask seam).
                for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                    int k=y*width+x;
                    if(x+1<width){
                        int j=k+1;double old=Y(original[k])-Y(original[j]);
                        double now=Y(changed[k])-Y(changed[j]);
                        if(Math.abs(old)<3.5 && Math.abs(now)>12)invented++;
                        if(Math.abs(old)>18 && Math.abs(now)>13 && old*now<0)reversed++;
                        total++;
                    }
                    if(y+1<height){
                        int j=k+width;double old=Y(original[k])-Y(original[j]);
                        double now=Y(changed[k])-Y(changed[j]);
                        if(Math.abs(old)<3.5 && Math.abs(now)>12)invented++;
                        if(Math.abs(old)>18 && Math.abs(now)>13 && old*now<0)reversed++;
                        total++;
                    }
                }
            }
            inventedEdgeRate=total>0?invented/total:0;
            edgeReversalRate=total>0?reversed/total:0;
        }
        private static double Y(int p){return .2126*((p>>>16)&255)+.7152*((p>>>8)&255)+.0722*(p&255);}

    }
    public static final class Choice {
        public final int selected;
        public final double[] quality;
        public final double margin;
        public final String reason;
        Choice(int s,double[] q,double m,String r){selected=s;quality=q;margin=m;reason=r;}
        public String summary(){return String.format(Locale.US,"candidate %d; advantage %.3f; %s",selected,margin,reason);}
    }
    /**
     * @param baseline traditional scene decision, never overwritten by depth presence.
     * @param aesthetic array of NIMA scores (NaN means missing); same scale for all looks.
     * @param technical NIMA technical scores (NaN allowed).
     * @param metrics damage measured against unprocessed scene using same aspect ratio.
     * @param preference bounded user preference in [-0.08,+0.08] (not unlimited vote drift).
     */
    public static Choice select(int baseline,double[] aesthetic,double[] technical,
                                Metrics[] metrics,double[] preference){
        if(baseline<0||baseline>=3||aesthetic.length!=3||technical.length!=3||
            metrics.length!=3||preference.length!=3)throw new IllegalArgumentException("3 candidates expected");
        double[] q=new double[3];
        int valid=0;
        for(int i=0;i<3;i++){
            if(!Double.isFinite(aesthetic[i])){q[i]=Double.NEGATIVE_INFINITY;continue;}
            valid++;
            double tech=Double.isFinite(technical[i])?technical[i]:5;
            double forcedClipping=metrics[i].newBlack+metrics[i].newWhite;
            double inventedEdges=metrics[i].inventedEdgeRate,flippedEdges=metrics[i].edgeReversalRate;
            // Excessive novelty is not intrinsically a quality gain. Limit extreme
            // edits only; do not systematically reward dull pictures.
            double excess=Math.max(0,metrics[i].meanAbsDifference-38);
            q[i]=.68*aesthetic[i]+.32*tech
                 -5.0*forcedClipping-.015*excess
                 -170.0*inventedEdges-65.0*flippedEdges
                 +Math.max(-.08,Math.min(.08,preference[i]));
        }
        if(valid==0)return new Choice(baseline,q,0,"NIMA unavailable; traditional fallback");
        int winner=baseline;
        double best=q[baseline];
        if(!Double.isFinite(best)){best=Double.NEGATIVE_INFINITY;for(int i=0;i<3;i++)if(Double.isFinite(q[i])){winner=i;best=q[i];break;}}
        for(int i=0;i<3;i++)if(q[i]>best){winner=i;best=q[i];}
        double base=q[baseline];
        // .10 on a 1-10 scale is a tuning threshold, not a confidence interval.
        // Its purpose is to avoid gratuitous selection flips for tiny differences.
        if(Double.isFinite(base) && winner!=baseline){
            double gap=best-base;
            if(gap<.10)return new Choice(baseline,q,gap,"near-tie; keep traditional decision");
            return new Choice(winner,q,gap,"clear measured gain (damage-checked)");
        }
        return new Choice(winner,q,Double.isFinite(base)?best-base:0,
                winner==baseline?"traditional decision retained":"baseline score unavailable");
    }
}