package com.cinema65.oneclick;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Conservative quality screening for pretrained salient-object masks.
 * This says whether the model produced a coherent, bounded proposal;
 * it NEVER estimates that the proposal is the photographer's intended subject.
 *
 * Thresholds are research candidates, calibrated on a very small convenience set;
 * use a larger, separately labelled test set before product release.
 */
public final class GenericSubjectGate {
    private GenericSubjectGate(){}
    public static final class Review {
        public final boolean usable;
        public final double coverage, connectedShare, borderOccupancy;
        public final String reason;
        Review(boolean u,double c,double s,double b,String r){usable=u;coverage=c;connectedShare=s;borderOccupancy=b;reason=r;}
        public String summary(){return String.format(Locale.US,"%s · area %.1f%%, coherence %.1f%%, edge %.1f%% (%s)",usable?"coherent proposal":"fallback",100*coverage,100*connectedShare,100*borderOccupancy,reason);}
    }
    public static Review assess(float[] mask,int w,int h){
        if(mask==null || w<1 || h<1 || (long)w*h!=mask.length)throw new IllegalArgumentException("Invalid salient-object mask");
        boolean[] on=new boolean[mask.length];int amount=0,edges=0,frame=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            if(!Float.isFinite(mask[i]))return new Review(false,0,0,0,"non-finite tensor");
            if(x==0||y==0||x==w-1||y==h-1)frame++;
            if(mask[i]>.55f){on[i]=true;amount++;if(x==0||y==0||x==w-1||y==h-1)edges++;}
        }
        double coverage=amount/(double)mask.length,border=edges/(double)Math.max(1,frame);
        int best=0;boolean[] seen=new boolean[on.length];ArrayDeque<Integer> q=new ArrayDeque<>();
        for(int i=0;i<on.length;i++){
            if(!on[i]||seen[i])continue;
            seen[i]=true;q.add(i);int size=0;
            while(!q.isEmpty()){
                int k=q.removeFirst();size++;int x=k%w,y=k/w;
                if(x>0&&on[k-1]&&!seen[k-1]){seen[k-1]=true;q.add(k-1);}
                if(x+1<w&&on[k+1]&&!seen[k+1]){seen[k+1]=true;q.add(k+1);}
                if(y>0&&on[k-w]&&!seen[k-w]){seen[k-w]=true;q.add(k-w);}
                if(y+1<h&&on[k+w]&&!seen[k+w]){seen[k+w]=true;q.add(k+w);}
            }
            best=Math.max(best,size);
        }
        double coherence=best/(double)Math.max(1,amount);
        String reason="bounded contiguous region";
        if(coverage<.060)reason="coverage below conservative 6.0% threshold";
        else if(coverage>.56)reason="whole scene foreground";
        else if(coherence<.76)reason="fragmented subject hypothesis";
        else if(border>.29)reason="model mask intersects image boundary extensively";
        boolean usable=reason.equals("bounded contiguous region");
        return new Review(usable,coverage,coherence,border,reason);
    }
}