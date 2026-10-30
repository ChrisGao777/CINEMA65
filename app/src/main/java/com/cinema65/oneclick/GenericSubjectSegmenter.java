package com.cinema65.oneclick;

import android.content.Context;
import android.graphics.Bitmap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Research-only pre-trained U2NETP ONNX. Entirely local; mask is a region PROPOSAL,
 * not a promise of the photographer's intended subject or a lighting instruction.
 * The original rendering remains the fallback if quality gating rejects inference.
 */
public final class GenericSubjectSegmenter {
    private GenericSubjectSegmenter() {}
    private static void mark(Context ctx,String step){
        ctx.getSharedPreferences("C65_CRASH_STAGE",Context.MODE_PRIVATE)
            .edit().putString("active","full: U2NETP "+step).commit();
    }
    public static final String MODEL="u2netp.onnx";
    private static final String SHA="309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8";
    public static final class Prediction {
        public final float[] mask;
        public final GenericSubjectGate.Review review;
        public final double tensorP02,tensorP98;
        Prediction(float[] m,GenericSubjectGate.Review r,double lo,double hi){mask=m;review=r;tensorP02=lo;tensorP98=hi;}
    }
    public static Prediction run(Context ctx,Bitmap photo,int mw,int mh)throws Exception{
        if(mw<1||mh<1)throw new IllegalArgumentException("Invalid mask grid");
        mark(ctx,"read model asset");
        byte[] modelBytes;
        try(InputStream in=ctx.getAssets().open(MODEL);ByteArrayOutputStream out=new ByteArrayOutputStream(5_000_000)){
            byte[] buf=new byte[32768];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            modelBytes=out.toByteArray();
        }
        mark(ctx,"verify model SHA256");
        StringBuilder hex=new StringBuilder();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(modelBytes);
        for(byte b:digest)hex.append(String.format(Locale.US,"%02x",b&255));
        if(!SHA.equals(hex.toString()))throw new IllegalStateException("Generic-subject model checksum mismatch");
        mark(ctx,"prepare 320x320 input");
        final int sz=320,plane=sz*sz;
        Bitmap scaled=Bitmap.createScaledBitmap(photo,sz,sz,true);
        int[] pix=new int[plane];scaled.getPixels(pix,0,sz,0,0,sz,sz);
        if(scaled!=photo)scaled.recycle();
        float peak=0;
        for(int p:pix)peak=Math.max(peak,Math.max((p>>>16)&255,Math.max((p>>>8)&255,p&255))/255f);
        peak=Math.max(1e-6f,peak);
        final float[] means={.485f,.456f,.406f},stds={.229f,.224f,.225f};
        FloatBuffer fb=ByteBuffer.allocateDirect(3*plane*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(int i=0;i<plane;i++){
            int p=pix[i];float r=((p>>>16)&255)/255f,g=((p>>>8)&255)/255f,b=(p&255)/255f;
            fb.put(i,(r/peak-means[0])/stds[0]);
            fb.put(plane+i,(g/peak-means[1])/stds[1]);
            fb.put(2*plane+i,(b/peak-means[2])/stds[2]);
        }
        fb.rewind();
        mark(ctx,"initialize ORT environment");
        OrtEnvironment env=OrtEnvironment.getEnvironment();
        float[] flat=new float[plane];
        mark(ctx,"configure session options");
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
            options.setIntraOpNumThreads(2);
            mark(ctx,"create session");
            try(OrtSession session=env.createSession(modelBytes,options)){
                if(!session.getOutputNames().contains("1959"))
                    throw new IllegalStateException("Unexpected U2NETP fused output name");
                String input=session.getInputNames().iterator().next();
                mark(ctx,"allocate input tensor");
                try(OnnxTensor tensor=OnnxTensor.createTensor(env,fb,new long[]{1,3,sz,sz})){
                    mark(ctx,"CPU inference (session.run)");
                    try(OrtSession.Result result=session.run(Collections.singletonMap(input,tensor),Collections.singleton("1959"))){
                        mark(ctx,"read inference output");
                        float[][][][] y=(float[][][][])result.get(0).getValue();
                        for(int yy=0;yy<sz;yy++)for(int xx=0;xx<sz;xx++){
                            float v=y[0][0][yy][xx];
                            if(!Float.isFinite(v))throw new IllegalStateException("Nonfinite segmentation output");
                            flat[yy*sz+xx]=v;
                        }
                    }
                }
            }
        }
        mark(ctx,"validate segmentation output");
        float[] ordered=flat.clone();Arrays.sort(ordered);
        float q02=ordered[(int)(.02*(plane-1))],q98=ordered[(int)(.98*(plane-1))];
        if(q98-q02<1e-6f)throw new IllegalStateException("Flat segmentation output");
        // Validate connectedness BEFORE shrinking to 48x48. Coarse sampling can
        // erase fragmented proposals and falsely turn them into a coherent blob.
        float[] highResolution=new float[plane];
        for(int i=0;i<plane;i++)highResolution[i]=Math.max(0,Math.min(1,
            (flat[i]-q02)/(q98-q02)));
        GenericSubjectGate.Review fullReview=GenericSubjectGate.assess(highResolution,sz,sz);
        float[] map=new float[mw*mh];
        for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
            double px=(x+.5)*sz/mw-.5,py=(y+.5)*sz/mh-.5;
            int x0=Math.max(0,Math.min(sz-1,(int)Math.floor(px)));
            int y0=Math.max(0,Math.min(sz-1,(int)Math.floor(py)));
            int x1=Math.min(sz-1,x0+1),y1=Math.min(sz-1,y0+1);
            double dx=Math.max(0,Math.min(1,px-x0)),dy=Math.max(0,Math.min(1,py-y0));
            float v=(float)((1-dy)*((1-dx)*flat[y0*sz+x0]+dx*flat[y0*sz+x1])+
                dy*((1-dx)*flat[y1*sz+x0]+dx*flat[y1*sz+x1]));
            map[y*mw+x]=Math.max(0,Math.min(1,(v-q02)/(q98-q02)));
        }
        GenericSubjectGate.Review coarse=GenericSubjectGate.assess(map,mw,mh);
        // Both resolutions must be geometrically credible. This is still only a
        // salient-object proposal, never proof of photographic intent.
        GenericSubjectGate.Review approved=fullReview.usable?coarse:fullReview;
        return new Prediction(map,approved,q02,q98);
    }
}