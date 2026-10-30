package com.cinema65.oneclick;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.content.res.AssetFileDescriptor;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.framework.image.ByteBufferExtractor;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier;
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Classifications;
import com.google.mediapipe.tasks.components.containers.ClassificationResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import org.tensorflow.lite.Interpreter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Locale;

/** Entirely on-device image analysis: semantic mask, landmarks, scene cues, NIMA and depth.
 * Models are shipped with the APK; individual inference failures never break editing.
 */
public final class LocalVision {
    private LocalVision() {}
    public static final class Guidance {
        public float[] subject; // image-space foreground likelihood at 48x48
        public float[] genericSubject; // U2NETP accepted mask ONLY: may influence grade
        public float[] genericProposal; // rejected/accepted proposal: DIAGNOSTICS ONLY
        public float[] depth;   // relative depth, optionally available, 48x48
        public int mapW=48,mapH=48;
        public boolean hasFace,hasPerson,hasDepth,hasClassifier,hasAesthetic,hasTechnical;
        public String scene="未分类";
        public String genericStatus="尚未运行";
        public long genericMs;
        public boolean hasGeneric;
        public String personStatus="未运行",faceStatus="未运行",classifierStatus="未运行",depthStatus="未运行";
        public long personMs,faceMs,classifierMs,depthMs;
        public double aesthetic=Double.NaN,technical=Double.NaN;
        public double warmCast;
        public String report(){
            StringBuilder b=new StringBuilder("本地分析：");
            b.append(hasPerson?"人物✓ ":"人物— ");
            b.append(hasFace?"面部✓ ":"面部— ");
            b.append(hasGeneric?"物体✓ ":"物体— ");
            b.append(hasClassifier?"分类✓ ":"分类— ");
            b.append(hasDepth?"空间✓ ":"空间— ");
            b.append(hasAesthetic?"审美✓ ":"审美— ");
            b.append(hasTechnical?"技术✓":"技术—");
            return b.toString();
        }
    }
    private static boolean assetExists(Context ctx,String name){
        try {ctx.getAssets().open(name).close();return true;}catch(Exception ignored){return false;}
    }
    public static Guidance inspect(Context ctx,Bitmap bitmap,boolean doDepth){
        return inspect(ctx,bitmap,doDepth,true);
    }
    /** Enable generic ONNX segmentation only after explicit opt-in. */
    public static Guidance inspect(Context ctx,Bitmap bitmap,boolean doDepth,boolean enableGeneric){
        Guidance g=new Guidance();
        int w=bitmap.getWidth(),h=bitmap.getHeight();
        // Gray-world chromatic bias is a safety *signal*, not automatic WB neutralization.
        int[] px=new int[w*h];bitmap.getPixels(px,0,w,0,0,w,h);
        double sr=0,sg=0,sb=0,count=0;
        for(int k=0;k<px.length;k+=Math.max(1,px.length/6000)){
            int c=px[k],r=(c>>>16)&255,gg=(c>>>8)&255,b=c&255;
            if(Math.min(r,Math.min(gg,b))<16||Math.max(r,Math.max(gg,b))>243)continue;
            sr+=r;sg+=gg;sb+=b;count++;
        }
        g.warmCast=count==0?0:Math.max(-1,Math.min(1,(sr-sb)/(count*80)));
        MPImage img=new BitmapImageBuilder(bitmap).build();
        long stepTime=android.os.SystemClock.elapsedRealtime();
        if(assetExists(ctx,"selfie_segmenter.tflite"))try{
            ImageSegmenter.ImageSegmenterOptions opt=ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("selfie_segmenter.tflite").build())
                .setRunningMode(RunningMode.IMAGE).setOutputCategoryMask(true).setOutputConfidenceMasks(false).build();
            try(ImageSegmenter segmenter=ImageSegmenter.createFromOptions(ctx,opt)){
                ImageSegmenterResult result=segmenter.segment(img);
                if(result.categoryMask().isPresent()){
                    MPImage mask=result.categoryMask().get();
                    ByteBuffer bb=ByteBufferExtractor.extract(mask).duplicate();bb.rewind();
                    float[] subject=resizeMask(bb,mask.getWidth(),mask.getHeight(),g.mapW,g.mapH);
                    float sum=0;for(float v:subject)sum+=v;
                    double fraction=sum/subject.length;
                    if(fraction>.015 && fraction<.85){g.subject=subject;g.hasPerson=true;g.personStatus=String.format(Locale.US,"成功，面积 %.1f%%",fraction*100);}
                    else g.personStatus=String.format(Locale.US,"未检出可信人物，面积 %.1f%%",fraction*100);
                }
            }
        }catch(Throwable ex){g.personStatus=reason(ex);}
        g.personMs=android.os.SystemClock.elapsedRealtime()-stepTime;
        stepTime=android.os.SystemClock.elapsedRealtime();
        if(assetExists(ctx,"face_landmarker.task"))try{
            FaceLandmarker.FaceLandmarkerOptions opt=FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setRunningMode(RunningMode.IMAGE).setNumFaces(3).build();
            try(FaceLandmarker face=FaceLandmarker.createFromOptions(ctx,opt)){
                FaceLandmarkerResult result=face.detect(img);
                if(!result.faceLandmarks().isEmpty()){
                    g.hasFace=true;g.faceStatus="识别到 "+result.faceLandmarks().size()+" 张面孔";
                    if(g.subject==null)g.subject=new float[g.mapW*g.mapH];
                    for(java.util.List<NormalizedLandmark> landmarks:result.faceLandmarks()){
                        // Use mesh envelope as a soft facial preservation / attention prior.
                        float minX=1,minY=1,maxX=0,maxY=0;
                        for(NormalizedLandmark p:landmarks){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());}
                        double cx=(minX+maxX)/2.,cy=(minY+maxY)/2.;
                        double rx=Math.max(.04,(maxX-minX)*.76),ry=Math.max(.04,(maxY-minY)*.75);
                        for(int y=0;y<g.mapH;y++)for(int x=0;x<g.mapW;x++){
                            double dx=((x+.5)/g.mapW-cx)/rx,dy=((y+.5)/g.mapH-cy)/ry;
                            float v=(float)Math.exp(-1.6*(dx*dx+dy*dy));
                            int j=y*g.mapW+x;g.subject[j]=Math.max(g.subject[j],.92f*v);
                        }
                    }
                }
            }
        }catch(Throwable ex){g.faceStatus=reason(ex);}
        if(!g.hasFace && g.faceStatus.equals("未运行"))g.faceStatus="未检测到面部";
        g.faceMs=android.os.SystemClock.elapsedRealtime()-stepTime;
        stepTime=android.os.SystemClock.elapsedRealtime();
        if(assetExists(ctx,"efficientnet_lite0.tflite"))try{
            ImageClassifier.ImageClassifierOptions opt=ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientnet_lite0.tflite").build())
                .setRunningMode(RunningMode.IMAGE).setMaxResults(5).build();
            try(ImageClassifier cl=ImageClassifier.createFromOptions(ctx,opt)){
                ImageClassifierResult r=cl.classify(img);
                if(!r.classificationResult().classifications().isEmpty()){
                    Classifications classes=r.classificationResult().classifications().get(0);
                    if(!classes.categories().isEmpty()){
                        Category best=classes.categories().get(0);g.scene=best.categoryName();g.hasClassifier=true;g.classifierStatus="类别："+g.scene;
                    }
                }
            }
        }catch(Throwable ex){g.classifierStatus=reason(ex);}
        g.classifierMs=android.os.SystemClock.elapsedRealtime()-stepTime;
        // Research-only U2NETP: use only if no credible person/face guidance exists.
        // Offline model is bundled at build time; absence or inference error is safe.
        stepTime=android.os.SystemClock.elapsedRealtime();
        if(enableGeneric && g.subject==null && assetExists(ctx,GenericSubjectSegmenter.MODEL))try{
            GenericSubjectSegmenter.Prediction p=GenericSubjectSegmenter.run(ctx,bitmap,g.mapW,g.mapH);
            g.genericStatus=p.review.summary();
            // Retain rejected proposals for visual assessment WITHOUT routing them
            // into trusted subject lighting, strategy budgets, or saved output.
            g.genericProposal=p.mask;
            if(p.review.usable){g.genericSubject=p.mask;g.subject=p.mask;g.hasGeneric=true;}
        }catch(Throwable ex){g.genericStatus=reason(ex);}
        else if(!enableGeneric)g.genericStatus="U2NETP 独立关闭（完整模式可单独启用）";
        else if(g.subject!=null)g.genericStatus="人物/面部引导已优先，不启用通用物体";
        g.genericMs=android.os.SystemClock.elapsedRealtime()-stepTime;
        stepTime=android.os.SystemClock.elapsedRealtime();
        if(doDepth && assetExists(ctx,"midas_small_256_fp16.tflite"))try{
            ctx.getSharedPreferences("C65_CRASH_STAGE",Context.MODE_PRIVATE)
                .edit().putString("active","full: MiDaS depth").commit();
            float[] map=depthInference(ctx,bitmap,g.mapW,g.mapH);
            if(map!=null){g.depth=map;g.hasDepth=true;g.depthStatus="成功 · MiDaS Small FP16 · 48×48";}
            else g.depthStatus="深度输出不可信";
        }catch(Throwable ex){g.depthStatus=reason(ex);}
        g.depthMs=android.os.SystemClock.elapsedRealtime()-stepTime;
        if(!doDepth)g.depthStatus="本次已禁用";
        return g;
    }
    private static float[] resizeMask(ByteBuffer bb,int w,int h,int tw,int th){
        float[] out=new float[tw*th];
        for(int y=0;y<th;y++)for(int x=0;x<tw;x++){
            int sx=Math.min(w-1,(int)((x+.5)*w/tw));int sy=Math.min(h-1,(int)((y+.5)*h/th));
            out[y*tw+x]=(bb.get(sy*w+sx)&0xff)==1?1f:0f;
        }
        // Very small 3x3 antialias kernel on model mask to suppress jagged spotlight edges.
        float[] blurred=out.clone();for(int y=1;y<th-1;y++)for(int x=1;x<tw-1;x++){
            float sum=0;for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++)sum+=out[(y+yy)*tw+x+xx];
            blurred[y*tw+x]=sum/9f;
        }
        return blurred;
    }
    private static MappedByteBuffer model(Context c,String name)throws Exception{
        try(AssetFileDescriptor fd=c.getAssets().openFd(name);
            FileInputStream fs=new FileInputStream(fd.getFileDescriptor());
            FileChannel channel=fs.getChannel()){
            return channel.map(FileChannel.MapMode.READ_ONLY,fd.getStartOffset(),fd.getDeclaredLength());
        }
    }
    public static final class ScoreResult {
        public double value=Double.NaN;
        public String status="未运行";
        public long elapsedMs;
    }
    private static String reason(Throwable e){
        Throwable current=e;int depth=0;
        while(current.getCause()!=null&&depth++<4)current=current.getCause();
        String m=current.getMessage();
        return current.getClass().getSimpleName()+(m==null?"":": "+m.replace('\n',' ').substring(0,Math.min(115,m.length())));
    }
    public static ScoreResult scoreDetailed(Context ctx,Bitmap photo,String asset){
        ScoreResult d=new ScoreResult();long start=android.os.SystemClock.elapsedRealtime();
        if(!assetExists(ctx,asset)){d.status="模型资源不存在";return d;}
        try{
            d.value=scoreRaw(ctx,photo,asset);
            d.status=Double.isFinite(d.value)?"成功":"模型输出无效";
        }catch(Throwable ex){d.status=reason(ex);}
        finally{d.elapsedMs=android.os.SystemClock.elapsedRealtime()-start;}
        return d;
    }
    public static double score(Context ctx,Bitmap photo,String asset){return scoreDetailed(ctx,photo,asset).value;}
    private static double scoreRaw(Context ctx,Bitmap photo,String asset)throws Exception{
        try(Interpreter interpreter=new Interpreter(model(ctx,asset),new Interpreter.Options().setNumThreads(2))){
            // NIMA's square input must come from a center crop. Stretching a 3:2
            // or panoramic image into 1:1 changes faces, buildings and the score.
            int side=Math.min(photo.getWidth(),photo.getHeight());
            Bitmap square=photo,b=null;
            int[] pix=new int[224*224];
            try{
                if(photo.getWidth()!=photo.getHeight())
                    square=Bitmap.createBitmap(photo,(photo.getWidth()-side)/2,
                        (photo.getHeight()-side)/2,side,side);
                b=Bitmap.createScaledBitmap(square,224,224,true);
                b.getPixels(pix,0,224,0,0,224,224);
            }finally{
                if(b!=null&&b!=square&&b!=photo)b.recycle();
                if(square!=photo)square.recycle();
            }
            ByteBuffer in=ByteBuffer.allocateDirect(224*224*3*4).order(ByteOrder.nativeOrder());
            for(int p:pix){in.putFloat((((p>>>16)&255)/127.5f)-1f);in.putFloat((((p>>>8)&255)/127.5f)-1f);in.putFloat(((p&255)/127.5f)-1f);}
            in.rewind();float[][] out=new float[1][10];interpreter.run(in,out);
            double total=0,weighted=0;
            for(int i=0;i<10;i++){total+=out[0][i];weighted+=(i+1)*out[0][i];}
            return total>.1?weighted/total:Double.NaN;
        }
    }
    /** MiDaS v2.1 Small FP16: validated model contract NHWC [1,256,256,3] -> [1,256,256].
     * Inverse depth: brighter = nearer. Never treat it as physical lighting. */
    private static float[] depthInference(Context ctx,Bitmap photo,int mw,int mh)throws Exception{
        final int iw=256,ih=256;
        // MiDaS expects a square tensor. Aspect-fit plus neutral padding avoids
        // stretching people and buildings; crop the matching output window.
        float scale=Math.min(iw/(float)photo.getWidth(),ih/(float)photo.getHeight());
        int dw=Math.max(1,Math.min(iw,Math.round(photo.getWidth()*scale)));
        int dh=Math.max(1,Math.min(ih,Math.round(photo.getHeight()*scale)));
        int left=(iw-dw)/2,top=(ih-dh)/2;
        Bitmap input=Bitmap.createBitmap(iw,ih,Bitmap.Config.ARGB_8888);
        Canvas board=new Canvas(input);board.drawColor(0xff808080);
        android.graphics.Rect sourceRect=new android.graphics.Rect(0,0,photo.getWidth(),photo.getHeight());
        board.drawBitmap(photo,sourceRect,new android.graphics.Rect(left,top,left+dw,top+dh),new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] px=new int[iw*ih];input.getPixels(px,0,iw,0,0,iw,ih);
        input.recycle();
        ByteBuffer bb=ByteBuffer.allocateDirect(3*iw*ih*4).order(ByteOrder.nativeOrder());
        final float[] means={.485f,.456f,.406f},stds={.229f,.224f,.225f};
        for(int p:px){
            bb.putFloat((((p>>>16)&255)/255f-means[0])/stds[0]);
            bb.putFloat((((p>>>8)&255)/255f-means[1])/stds[1]);
            bb.putFloat(((p&255)/255f-means[2])/stds[2]);
        }
        bb.rewind();
        float[][][] output=new float[1][ih][iw];
        try(Interpreter it=new Interpreter(model(ctx,"midas_small_256_fp16.tflite"),
            new Interpreter.Options().setNumThreads(3).setUseXNNPACK(true))){
            int[] inShape=it.getInputTensor(0).shape(),outShape=it.getOutputTensor(0).shape();
            if(inShape.length!=4||inShape[1]!=ih||inShape[2]!=iw||inShape[3]!=3||
               outShape.length!=3||outShape[1]!=ih||outShape[2]!=iw)
                throw new IllegalStateException("MiDaS shape mismatch: "+java.util.Arrays.toString(inShape)+" -> "+java.util.Arrays.toString(outShape));
            it.run(bb,output);
        }
        float[] v=new float[mw*mh];float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
            int ix=Math.min(iw-1,left+Math.min(dw-1,(int)((x+.5)*dw/mw)));
            int iy=Math.min(ih-1,top+Math.min(dh-1,(int)((y+.5)*dh/mh)));
            float z=output[0][iy][ix];if(!Float.isFinite(z))return null;
            v[y*mw+x]=z;min=Math.min(min,z);max=Math.max(max,z);
        }
        if(max-min<1e-6f)return null;
        for(int i=0;i<v.length;i++)v[i]=(v[i]-min)/(max-min);
        return v;
    }
}
