package com.cinema65.oneclick;

/**
 * v0.8.7 experimental scene-relative display-referred highlight shoulder and shadow toe.
 * This runs on the finished sRGB candidate, after split-tone and gamut mapping.
 * It does not synthesize highlight detail; a source pixel clipped to white stays white.
 *
 * Pure deterministic per-pixel operation: full-image and global-coordinate stripe renders
 * must be bit-identical. Its intervention is proportional to actual NEW output risk,
 * not to subject/depth/saliency confidence.
 */
public final class ToneSafety {
    private ToneSafety() {}
    public static final class Correction {
        public final int color;
        public final double highlightSuppression,shadowRecovery;
        Correction(int color,double h,double s){this.color=color;highlightSuppression=h;shadowRecovery=s;}
    }
    private static double clamp(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static double smooth(double lo,double hi,double x){
        double t=clamp((x-lo)/(hi-lo),0,1);return t*t*(3-2*t);
    }
    private static int q(double v){return (int)Math.round(clamp(v,0,255));}
    private static double y(int p){return .2126*((p>>>16)&255)+.7152*((p>>>8)&255)+.0722*(p&255);}

    /**
     * Strength 0 preserves exact old output. 1 uses the conservative SDR shoulder/toe.
     * Knee slopes are continuous (C1); chroma is kept by a neutral gain for highlights
     * and a small equal-channel offset for shadows.
     */
    public static int protect(int source,int graded,double strength){
        if(!Double.isFinite(strength))throw new IllegalArgumentException("Nonfinite strength");
        double gain=clamp(strength,0,1);
        if(gain==0 || (source&0xffffff)==0xffffff)
            return graded;
        double originalY=y(source),outY=y(graded),resultY=outY;
        // Preserve existing highlights rather than fabricating recoverable detail.
        // Shoulder only acts strongly when the SOURCE is not already near-white.
        if(outY>235.0){
            double gap=outY-235.0;
            double shoulder=235.0+20.0*gap/(20.0+gap);
            double risk=smooth(0,1,1.0-smooth(235.0,248.0,originalY));
            double desired=outY-gain*risk*(outY-shoulder);
            resultY=desired;
        }
        // Recover texture only where an originally visible dark region would become
        // nearly uniform black. Leave true pre-existing near-black pixels alone.
        if(outY<17.0){
            double gap=17.0-outY;
            double toe=17.0-11.0*gap/(11.0+gap);
            double risk=smooth(12.0,31.0,originalY);
            double desired=outY+gain*risk*(toe-outY);
            resultY=desired;
        }
        if(Math.abs(resultY-outY)<.08)return graded;
        int r=(graded>>>16)&255,g=(graded>>>8)&255,b=graded&255;
        int rr,gg,bb;
        if(resultY<outY && outY>0){
            // Multiply all channels by the same scalar to keep the existing hue.
            double scale=resultY/outY;
            rr=q(r*scale);gg=q(g*scale);bb=q(b*scale);
        } else {
            // Black toe: tiny achromatic offset preserves dark chroma differences.
            double lift=resultY-outY;
            rr=q(r+lift);gg=q(g+lift);bb=q(b+lift);
        }
        return (graded&0xff000000)|(rr<<16)|(gg<<8)|bb;
    }
    /** Diagnostic path only. Do not call for each pixel in a full resolution render. */
    public static Correction correct(int source,int graded,double strength){
        int adjusted=protect(source,graded,strength);
        double difference=y(adjusted)-y(graded);
        return new Correction(adjusted,Math.max(0,-difference),Math.max(0,difference));
    }
}