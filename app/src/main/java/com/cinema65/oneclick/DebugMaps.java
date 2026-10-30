package com.cinema65.oneclick;
import android.graphics.Bitmap;
/** Actual analytic masks and output-difference diagnostics, no synthetic inference. */
public final class DebugMaps {
    private DebugMaps(){}
    private static int clip(int n){return Math.max(0,Math.min(255,n));}
    public static Bitmap map(Bitmap base,float[] mask,int mw,int mh,int mode){
        if(mask==null||mask.length!=mw*mh)return null;
        int w=base.getWidth(),h=base.getHeight(),cap=850;
        double sc=Math.min(1.0,cap/(double)Math.max(w,h));
        w=Math.max(1,(int)(w*sc));h=Math.max(1,(int)(h*sc));
        Bitmap mini=Bitmap.createScaledBitmap(base,w,h,true);
        int[] pix=new int[w*h];mini.getPixels(pix,0,w,0,0,w,h);
        int r=mode==2?252:mode==1?75:250,g=mode==2?168:mode==1?179:146,b=mode==2?68:mode==1?246:71;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int k=y*w+x,c=pix[k],idx=Math.min(mh-1,(int)((y+.5)*mh/h))*mw+Math.min(mw-1,(int)((x+.5)*mw/w));
            float v=Math.max(0,Math.min(1,mask[idx]));
            double alpha=.69*v; // zero evidence must render as no overlay
            pix[k]=0xff000000|(clip((int)(((c>>>16)&255)*(1-alpha)+r*alpha))<<16)|
                 (clip((int)(((c>>>8)&255)*(1-alpha)+g*alpha))<<8)|
                 clip((int)((c&255)*(1-alpha)+b*alpha));
        }
        if(mini!=base)mini.recycle();
        return Bitmap.createBitmap(pix,w,h,Bitmap.Config.ARGB_8888);
    }
    /** Color-mark pixels that actually changed, relative to the user-selected treatment. */
    public static Bitmap delta(Bitmap original,Bitmap result,boolean lift){
        int w=original.getWidth(),h=original.getHeight();
        if(result.getWidth()!=w||result.getHeight()!=h)return null;
        int cap=850;double scale=Math.min(1.0,cap/(double)Math.max(w,h));
        int sw=Math.max(1,(int)(w*scale)),sh=Math.max(1,(int)(h*scale));
        Bitmap before=Bitmap.createScaledBitmap(original,sw,sh,true),after=Bitmap.createScaledBitmap(result,sw,sh,true);
        int[] a=new int[sw*sh],b=new int[sw*sh];before.getPixels(a,0,sw,0,0,sw,sh);after.getPixels(b,0,sw,0,0,sw,sh);
        for(int k=0;k<a.length;k++){
            int r=a[k],t=b[k];
            double L=(.2126*((r>>>16)&255)+.7152*((r>>>8)&255)+.0722*(r&255));
            double N=(.2126*((t>>>16)&255)+.7152*((t>>>8)&255)+.0722*(t&255));
            double difference=lift?N-L:L-N;
            double alpha=.70*Math.max(0,Math.min(1,(difference-2)/26.));
            int cr=lift?248:66,cg=lift?193:137,cb=lift?57:242;
            b[k]=0xff000000|(clip((int)(((r>>>16)&255)*(1-alpha)+cr*alpha))<<16)|
              (clip((int)(((r>>>8)&255)*(1-alpha)+cg*alpha))<<8)|
              clip((int)((r&255)*(1-alpha)+cb*alpha));
        }
        if(before!=original)before.recycle();if(after!=result)after.recycle();
        return Bitmap.createBitmap(b,sw,sh,Bitmap.Config.ARGB_8888);
    }
}