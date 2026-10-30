package com.cinema65.oneclick;

import java.util.Locale;

/** CINEMA65 v0.7: multiscale composition-aware filmic sculpting, offline deterministic sRGB SDR.
 * Analysis is performed once on a scaled preview. The resulting scene
 * model is then used for preview grading and tiled original-size export.
 * Pixel coordinates are global: tile seams are mathematically impossible.
 */
public final class FilmCore {
    private FilmCore() {}
    private static final double[] KEYS={0,5,10,20,35,50,65,80,95,100};
    private static final double[] DAY={0,2.7,5.9,13.1,28.1,47.5,67.8,85.2,96.1,100};
    private static final double[] DARK={0,3.8,9.4,20.8,37.5,54.7,71.8,86.0,96.4,100};
    private static final double[] LINEAR=new double[256],LSTAR=new double[4097],NEUTRAL=new double[1001];
    private static final double[] DAY_CURVE=curve(DAY),DARK_CURVE=curve(DARK);
    static {
        for(int i=0;i<256;i++){
            double x=i/255.0;LINEAR[i]=x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);
        }
        for(int i=0;i<LSTAR.length;i++)LSTAR[i]=yToL(i/4096.0);
        for(int i=0;i<NEUTRAL.length;i++)NEUTRAL[i]=toSrgb(lToY(i/10.0));
    }
    /** Scene analysis; treatment and research guidance can change after construction. */
    public static final class Analysis {
        public final double darkFraction,highlightFraction,median,contrast,nocturne,styleStrength,highSaturation;
        public final double p10,p90,p98,lightDepth,skinFraction,focusConfidence,focusX,focusY,lightDx,lightDy;
        /** Internally chosen treatment: 0 = subject detail, 1 = spatial light, 2 = color narrative. */
        public int selectedTreatment;
        /** Calibrated *relative* saliency distinctiveness, not a semantic probability. */
        public double saliencyDistinctiveness;
        public final int width,height;
        private final float[] illumination, wideField, attention;
        private SpatialFusion.Plan fusion;
        private ExposureAllocator.Budget exposure;
        private EnvironmentSculptor.Budget environment;
        private StrategyDirector.Plan director;
        private ColorDensityBudget.Plan colorDensity;
        private float[] redField,greenField,blueField;
        private double orchestratorStrength=1.0;
        private double colorStrength=1.0;
        private double guidanceGain=1.0;
        private double environmentStrength=1.0;
        /** Pure SDR output safety; 0 recovers exact v0.8.6 pixels for A/B studies. */
        private double toneSafetyStrength=1.0;
        private final int iw,ih;
        Analysis(double df,double hf,double m,double c,double n,double strength,double sat,
                 double p10,double p90,double p98,double skinFraction,double lightDepth,
                 int width,int height,float[] illumination,float[] wideField,float[] attention,
                 int iw,int ih,double focusConfidence,double focusX,double focusY,double lightDx,double lightDy){
            darkFraction=df;highlightFraction=hf;median=m;contrast=c;nocturne=n;
            styleStrength=strength;highSaturation=sat;this.p10=p10;this.p90=p90;this.p98=p98;
            this.skinFraction=skinFraction;this.lightDepth=lightDepth;
            this.width=width;this.height=height;this.illumination=illumination;
            this.wideField=wideField;this.attention=attention;this.iw=iw;this.ih=ih;
            this.focusConfidence=focusConfidence;this.focusX=focusX;this.focusY=focusY;
            this.lightDx=lightDx;this.lightDy=lightDy;
        }
        /** Independent decision state; large source-derived grids are shared read-only. */
        Analysis(Analysis source){
            this(source.darkFraction,source.highlightFraction,source.median,source.contrast,
                 source.nocturne,source.styleStrength,source.highSaturation,
                 source.p10,source.p90,source.p98,source.skinFraction,source.lightDepth,
                 source.width,source.height,source.illumination,source.wideField,
                 source.attention,source.iw,source.ih,source.focusConfidence,
                 source.focusX,source.focusY,source.lightDx,source.lightDy);
            selectedTreatment=source.selectedTreatment;
            saliencyDistinctiveness=source.saliencyDistinctiveness;
            redField=source.redField;greenField=source.greenField;blueField=source.blueField;
            fusion=source.fusion;exposure=source.exposure;environment=source.environment;
            director=source.director;colorDensity=source.colorDensity;
            orchestratorStrength=source.orchestratorStrength;
            colorStrength=source.colorStrength;guidanceGain=source.guidanceGain;
            environmentStrength=source.environmentStrength;
            toneSafetyStrength=source.toneSafetyStrength;
        }
        public String fusionSummary(){return fusion==null?"unmodeled":fusion.summary();}
        public boolean hasModelGuidance(){return fusion!=null&&fusion.hasGuidance();}
        public double semanticTrust(){return fusion==null?0:fusion.semanticTrust;}
        public double depthTrust(){return fusion==null?0:fusion.depthTrust;}
        public double guidanceGain(){return guidanceGain;}
        public double environmentStrength(){return environmentStrength;}
        public String exposureSummary(){return exposure==null?"no exposure guidance":exposure.summary();}
        public String environmentSummary(){return environment==null?"no environment guidance":environment.summary();}
        public String strategySummary(){return director==null?"no coordinated strategy":director.summary();}
        public String colorSummary(){return colorDensity==null?"no color budget":colorDensity.summary();}
        public String strategyName(){
            if(fusion==null||!fusion.hasGuidance())return "传统规则（模型未提供可靠空间证据）";
            if(fusion.semanticTrust>.35)return "可信人物主体优先 · 自然受光保护";
            if(nocturne>.45)return "暗光保护 · 低强度远景分离";
            if(median>65&&fusion.depthTrust>.32)return "明亮场景 · 纵深分层";
            if(fusion.depthTrust>.25)return "自然纵深 · 保守空间塑光";
            return "结构证据优先 · 有限局部修正";
        }
        public String summary(){return String.format(Locale.US,
          "构图塑光 · %s · 区分度 %.0f%%",treatmentName(),100*saliencyDistinctiveness);}
        public String treatmentName(){return selectedTreatment==0?"主体细节":selectedTreatment==1?"空间塑光":"色彩叙事";}
    }
    /** Reads pixels without modifying them. Preview should retain the image's full aspect ratio. */
    public static Analysis analyze(int[] src,int width,int height){
        validate(src,width,height);
        int[] hist=new int[101];int skip=Math.max(1,src.length/140000);
        int count=0,dark=0,highlight=0,saturated=0,skinCount=0;
        for(int k=0;k<src.length;k+=skip){
            int p=src[k];double l=lightness(p);hist[clamp((int)Math.round(l),0,100)]++;
            if(l<30)dark++;if(l>93)highlight++;
            int r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
            if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>115)saturated++;
            if(skinMask(r/255.0,g/255.0,b/255.0,l)>.35)skinCount++;
            count++;
        }
        double df=dark/(double)count,hf=highlight/(double)count,sat=saturated/(double)count,skinFraction=skinCount/(double)count;
        double p10=percentile(hist,count,.10),p50=percentile(hist,count,.50);
        double p90=percentile(hist,count,.90),p98=percentile(hist,count,.98);
        double contrast=p90-p10;
        double nocturne=smooth(.34,.72,df)*(1-smooth(38,57,p50));
        // Deliberately more pronounced look than 0.4; local safeguards operate per pixel.
        // Skin-heavy and already highly saturated scenes are dialed back, not flattened.
        double extremeContrast=smooth(71,92,contrast);
        double strength=clampD(1.0-.12*extremeContrast-.10*smooth(.04,.23,hf)
                                 -.12*smooth(.12,.46,sat)-.14*smooth(.08,.35,skinFraction),.67,1.0);
        // Light-depth drive: never infer true geometry from brightness; shape tonal zones.
        double lightDepth=clampD(.89+.16*smooth(24,60,contrast)-.27*smooth(.11,.40,skinFraction)
            -.12*smooth(.08,.24,hf),.55,1.05);
        // Coarse, smooth 2D spatial light field, not a detail sharpener.
        int iw=Math.max(1,Math.min(48,(width+44)/45));
        int ih=Math.max(1,Math.min(48,(height+44)/45));
        // Derive a low-resolution visual-attention map from actual local evidence.
        // It intentionally does NOT claim semantic object or physical depth recognition.
        float[] illum=new float[iw*ih], red=new float[iw*ih], green=new float[iw*ih], blue=new float[iw*ih];
        float[] texture=new float[iw*ih];int[] hits=new int[iw*ih];
        int step=Math.max(1,src.length/180000);
        for(int k=0;k<src.length;k+=step){
            int x=k%width,y=k/width;
            int ix=Math.min(iw-1,(int)((long)x*iw/width));
            int iy=Math.min(ih-1,(int)((long)y*ih/height));
            int id=iy*iw+ix,p=src[k];
            double l=lightness(p);
            illum[id]+=(float)l;
            red[id]+=(p>>>16)&255;green[id]+=(p>>>8)&255;blue[id]+=p&255;
            // Texture is a *feature for choosing where to dodge*, not image sharpening.
            int x2=Math.min(width-1,x+5),y2=Math.min(height-1,y+5);
            texture[id]+=(float)Math.min(20, .5*(Math.abs(l-lightness(src[y*width+x2]))+
                                                Math.abs(l-lightness(src[y2*width+x]))));
            hits[id]++;
        }
        for(int i=0;i<illum.length;i++){
            if(hits[i]>0){
                float cellCount=hits[i];illum[i]/=cellCount;red[i]/=cellCount;green[i]/=cellCount;
                blue[i]/=cellCount;texture[i]/=cellCount;
            } else {illum[i]=(float)p50;red[i]=green[i]=blue[i]=128;}
        }
        float[] nearField=blur(illum,iw,ih,1);
        float[] smallField=blur(illum,iw,ih,3);
        float[] wideField=blur(illum,iw,ih,11);
        float[] detail=blur(texture,iw,ih,2);
        float[] reds=blur(red,iw,ih,6),greens=blur(green,iw,ih,6),blues=blur(blue,iw,ih,6);
        float[] scores=new float[iw*ih];
        double peak=0;int peakIndex=0;
        for(int yy=0;yy<ih;yy++)for(int xx=0;xx<iw;xx++){
            int i=yy*iw+xx;
            double nearContrast=Math.abs(nearField[i]-smallField[i]);
            double globalContrast=Math.abs(smallField[i]-wideField[i]);
            double regionalContrast=Math.abs(illum[i]-nearField[i]);
            double colorDist=(Math.abs(red[i]-reds[i])+Math.abs(green[i]-greens[i])+
                              Math.abs(blue[i]-blues[i]))/3.0;
            double textureScore=smooth(.9,5.6,detail[i]);
            double chromatic=smooth(8,38,colorDist);
            double luminanceScore=smooth(2.1,13,globalContrast);
            double lightStructure=smooth(2.0,10,nearContrast);
            double centerX=(xx+.5)/iw,centerY=(yy+.5)/ih;
            double radius=Math.pow((centerX-.5)/.7,2)+Math.pow((centerY-.54)/.75,2);
            double centerPrior=.72+.28*Math.exp(-1.5*radius);
            double blueSky=smooth(14,48,blue[i]-(red[i]+green[i])*.5)
                *smooth(35,67,illum[i])*(1-smooth(.85,5.5,detail[i]))
                *(1-smooth(.20,.54,centerY));
            double regionStructure=smooth(2.2,8.0,regionalContrast);
            double score=(.24*textureScore+.30*luminanceScore+.22*chromatic
                         +.15*lightStructure+.09*regionStructure)
                *centerPrior*(1-.72*blueSky)*smooth(8,21,illum[i])*(1-smooth(96,100,illum[i]));
            scores[i]=(float)score;
            if(score>peak){peak=score;peakIndex=i;}
        }
        // Build soft main + subordinate attention fields, never a single hard spotlight.
        // The top peaks must have spatial separation and actual local evidence.
        float[] smoothScores=blur(scores,iw,ih,2);
        double dominant=0;int dominantId=0;
        for(int i=0;i<smoothScores.length;i++)if(smoothScores[i]>dominant){
            dominant=smoothScores[i];dominantId=i;
        }
        double supportPeak=0;int supportId=-1;
        for(int yy=0;yy<ih;yy++)for(int xx=0;xx<iw;xx++){
            int i=yy*iw+xx;
            double d=Math.hypot((xx-dominantId%iw)/(double)iw,
                                (yy-dominantId/iw)/(double)ih);
            if(d>.23&&smoothScores[i]>supportPeak){supportPeak=smoothScores[i];supportId=i;}
        }
        float[] sorted=smoothScores.clone();java.util.Arrays.sort(sorted);
        double q75=sorted[(int)(.75*(sorted.length-1))];
        // Preserve the proven v0.6-v0.8.2 rendering gain to avoid a portrait
        // regression. Expose a separately calibrated diagnostic score instead;
        // the old render-drive is NOT a probability of the correct subject.
        double focusConfidence=smooth(.035,.135,dominant-q75)*smooth(.10,.35,dominant);
        double distinctiveness=.86*smooth(.028,.17,dominant-q75)*smooth(.10,.38,dominant);
        double focusX=((dominantId%iw)+.5)/iw,focusY=((dominantId/iw)+.5)/ih;
        double secondConfidence=(supportId>=0?smooth(.59,.92,supportPeak/Math.max(.001,dominant)):0);
        // Smooth centered blob, plus a second subject only when evidence is genuinely strong.
        float[] attention=new float[iw*ih];
        for(int yy=0;yy<ih;yy++)for(int xx=0;xx<iw;xx++){
            int i=yy*iw+xx;
            double px=(xx+.5)/iw,py=(yy+.5)/ih;
            double x=(px-focusX)/.245,y=(py-focusY)/.29;
            double main=Math.exp(-.92*(x*x+y*y));
            double sub=0;
            if(supportId>=0){
                double tx=(px-((supportId%iw+.5)/iw))/.23;
                double ty=(py-((supportId/iw+.5)/ih))/.27;
                sub=.44*secondConfidence*Math.exp(-1.02*(tx*tx+ty*ty));
            }
            double spatialEvidence=smooth(.12,.64,smoothScores[i]);
            attention[i]=(float)(focusConfidence*clampD(.76*main+sub+.16*spatialEvidence,0,1));
        }
        attention=blur(attention,iw,ih,2);
        // Prefer the direction of pre-existing broad luminance gradients.
        // No fabricated cast shadows and no claim of true depth estimation.
        double gx=0,gy=0,total=0;
        for(int yy=1;yy<ih-1;yy++)for(int xx=1;xx<iw-1;xx++){
            int i=yy*iw+xx;double weight=.3+.7*attention[i];
            gx+=(wideField[i+1]-wideField[i-1])*weight;
            gy+=(wideField[i+iw]-wideField[i-iw])*weight;total+=weight;
        }
        gx/=Math.max(1,total);gy/=Math.max(1,total);
        double mag=Math.hypot(gx,gy),confidence=smooth(.23,1.35,mag);
        double dx=confidence*gx/Math.max(mag,1.e-7),dy=confidence*gy/Math.max(mag,1.e-7);
        Analysis result=new Analysis(df,hf,p50,contrast,nocturne,strength,sat,p10,p90,p98,
                            skinFraction,lightDepth,width,height,smallField,wideField,attention,
                            iw,ih,focusConfidence,focusX,focusY,dx,dy);
        result.saliencyDistinctiveness=distinctiveness;
        result.redField=red;result.greenField=green;result.blueField=blue;
        // Evaluate three *actual* deterministic rendering candidates on an evenly spread
        // subsample. Quality scores include composition, clipping and color safety.
        result.selectedTreatment=selectTreatment(src,width,height,result);
        return result;
    }
    /** Fork a completed analysis before applying optional model guidance or choosing a cut.
     * This avoids repeating expensive histogram, saliency and candidate calculations.
     */
    public static Analysis copyAnalysis(Analysis source){
        if(source==null)throw new IllegalArgumentException("Missing scene analysis");
        return new Analysis(source);
    }
    /**
     * Model evidence is deliberately separate from photographic saliency.
     * Do not boost near/far depth equally, and NEVER force treatment=spatial.
     * Call only after scene analysis, before candidate rendering.
     */
    public static void applyGuidance(Analysis a,float[] subject,int mw,int mh,float[] depth){
        if(a==null||mw<1||mh<1)throw new IllegalArgumentException("Invalid guidance metadata");
        a.fusion=SpatialFusion.build(a.attention,a.illumination,a.wideField,a.iw,a.ih,
                                      subject,depth,mw,mh,a.saliencyDistinctiveness);
        a.exposure=ExposureAllocator.compute(a.fusion,a.illumination,a.wideField,
                                              a.iw,a.ih,a.median,a.nocturne);
        a.environment=EnvironmentSculptor.compute(a.fusion,a.illumination,a.wideField,
                                                   a.iw,a.ih,a.nocturne);
        a.director=StrategyDirector.plan(a.fusion,a.exposure,a.environment,a.illumination,a.nocturne);
        a.colorDensity=ColorDensityBudget.compute(a.fusion,a.illumination,
                                 a.redField,a.greenField,a.blueField,a.iw,a.ih);
        // Keep the traditional saliency map unchanged for fair A/B diagnostics.
        // Selected candidate remains the scene's original hypothesis until ranked.
    }
    public static void setGuidanceGain(Analysis a,double gain){
        if(a==null||!Double.isFinite(gain))throw new IllegalArgumentException("Invalid guidance gain");
        a.guidanceGain=clampD(gain,0,1);
    }
    public static void setEnvironmentStrength(Analysis a,double value){
        if(a==null||!Double.isFinite(value))throw new IllegalArgumentException("Invalid environment strength");
        a.environmentStrength=clampD(value,0,1);
    }
    public static void setToneSafetyStrength(Analysis a,double value){
        if(a==null||!Double.isFinite(value))throw new IllegalArgumentException("Invalid tone safety strength");
        a.toneSafetyStrength=clampD(value,0,1);
    }
    public static double toneSafetyStrength(Analysis a){return a.toneSafetyStrength;}
    /** v0.8.9 research-only exact ablation: 0 preserves v0.8.8 pixel logic. */
    public static void setOrchestratorStrength(Analysis a,double v){
        if(a==null||!Double.isFinite(v))throw new IllegalArgumentException("Invalid strategy strength");
        a.orchestratorStrength=clampD(v,0,1);
    }
    public static void setColorStrength(Analysis a,double v){
        if(a==null||!Double.isFinite(v))throw new IllegalArgumentException("Invalid color strength");
        a.colorStrength=clampD(v,0,1);
    }
    public static float[] debugColorBudget(Analysis a){
        return a.colorDensity==null?new float[a.iw*a.ih]:a.colorDensity.capacity.clone();
    }
    /** Research-only maps: exposure allowance, NOT the actual output dodge/burn. */
    public static float[] debugSubjectDodgeBudget(Analysis a){
        return a.exposure==null?new float[a.iw*a.ih]:a.exposure.subjectDodge.clone();
    }
    public static float[] debugBackgroundBurnBudget(Analysis a){
        return a.exposure==null?new float[a.iw*a.ih]:a.exposure.backgroundBurn.clone();
    }
    public static float[] debugEnvironmentBudget(Analysis a){
        return a.environment==null?new float[a.iw*a.ih]:a.environment.backgroundRecession.clone();
    }
    public static float[] debugDepthFar(Analysis a){
        return a.fusion==null?new float[a.iw*a.ih]:a.fusion.far.clone();
    }
    public static float[] debugSemantic(Analysis a){
        return a.fusion==null?new float[a.iw*a.ih]:a.fusion.subject.clone();
    }
    /** Read-only copy of the actual saliency mask used by pixel(). For diagnostics only. */
    public static float[] debugAttention(Analysis a){return a.attention.clone();}
    public static int debugAttentionWidth(Analysis a){return a.iw;}
    public static int debugAttentionHeight(Analysis a){return a.ih;}
    /** Strength blend (100 is the exact cinematic output; 0 is untouched source). */
    public static void blendOutput(int[] original,int[] graded,int percent){
        if(original.length!=graded.length)throw new IllegalArgumentException("Blend size mismatch");
        blendOutputPrefix(original,graded,original.length,percent);
    }
    /** Blend the populated prefix of a reusable export stripe without another full clone. */
    public static void blendOutputPrefix(int[] original,int[] graded,int count,int percent){
        if(original==null||graded==null||count<0||count>original.length||count>graded.length)
            throw new IllegalArgumentException("Blend size mismatch");
        double gain=Math.max(0,Math.min(150,percent))/100.0;
        if(gain==1)return;
        for(int i=0;i<count;i++){
            int a=original[i],b=graded[i];
            int r=clamp((int)Math.round(((a>>>16)&255)+gain*(((b>>>16)&255)-((a>>>16)&255))),0,255);
            int g=clamp((int)Math.round(((a>>>8)&255)+gain*(((b>>>8)&255)-((a>>>8)&255))),0,255);
            int bl=clamp((int)Math.round((a&255)+gain*((b&255)-(a&255))),0,255);
            graded[i]=0xff000000|(r<<16)|(g<<8)|bl;
        }
    }
    /** Render explicit candidate for NIMA comparison, changing no metadata. */
    public static void candidate(int[] pixels,int w,int h,Analysis a,int treatment){
        validate(pixels,w,h);
        if(treatment<0||treatment>2)throw new IllegalArgumentException("candidate 0-2");
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            pixels[i]=pixel(pixels[i],(x+.5)/(double)w,(y+.5)/(double)h,x,y,a,treatment);
        }
    }
    /** Grade a full buffer. Kept for desktop clients and backward compatibility. */
    public static Analysis grade(int[] pixels,int width,int height){
        Analysis info=analyze(pixels,width,height);
        applyTile(pixels,width,height,0,0,width,height,info);
        return info;
    }
    /** Grade pixels in-place. left/top are coordinates in full image, NOT tile coords. */
    public static void applyTile(int[] pixels,int tileWidth,int tileHeight,int left,int top,
                                 int fullWidth,int fullHeight,Analysis a){
        validate(pixels,tileWidth,tileHeight);
        if(a==null||left<0||top<0||fullWidth<tileWidth||fullHeight<tileHeight||
           (long)left+tileWidth>fullWidth||(long)top+tileHeight>fullHeight)
            throw new IllegalArgumentException("Invalid tile geometry");
        for(int y=0;y<tileHeight;y++){
            double py=(top+y+.5)/fullHeight;
            for(int x=0;x<tileWidth;x++){
                int idx=y*tileWidth+x;
                pixels[idx]=pixel(pixels[idx],(left+x+.5)/fullWidth,py,left+x,top+y,a);
            }
        }
    }
    /** Candidate evaluation on a small, deterministic, stratified sample. */
    private static int selectTreatment(int[] src,int w,int h,Analysis a){
        int stride=Math.max(1,(int)Math.ceil(Math.sqrt((w*(double)h)/4200.0)));
        double[] sum=new double[3],bg=new double[3],risk=new double[3];
        double subjectN=0,backgroundN=0;
        for(int y=stride/2;y<h;y+=stride)for(int x=stride/2;x<w;x+=stride){
            int p=src[y*w+x];double l=lightness(p);
            double f=sample(a.attention,a.iw,a.ih,
                    clampD((x+.5)*a.iw/w-.5,0,a.iw-1),clampD((y+.5)*a.ih/h-.5,0,a.ih-1));
            double foreground=smooth(.18,.64,f),background=1-foreground;
            double skin=skinMask(((p>>>16)&255)/255.0,((p>>>8)&255)/255.0,(p&255)/255.0,l);
            subjectN+=foreground;backgroundN+=background;
            for(int j=0;j<3;j++){
                int o=pixel(p,(x+.5)/(double)w,(y+.5)/(double)h,x,y,a,j);
                double l2=lightness(o),delta=l2-l;
                sum[j]+=foreground*delta;
                bg[j]+=background*delta;
                // Reject clipped regions, heavy skin shifts and unnecessary crushed shadows.
                if(l>9 && l<92 && (l2<5 || l2>98))risk[j]+=2.4;
                if(skin>.35)risk[j]+=.017*Math.abs(l2-l)+
                    .065*(Math.abs(((o>>>16)&255)-((p>>>16)&255))+
                          Math.abs(((o>>>8)&255)-((p>>>8)&255))+
                          Math.abs((o&255)-(p&255)))/3;
                if(l<30 && delta< -15) risk[j]+=.65;
            }
        }
        double best=-Double.MAX_VALUE;int chosen=1;
        for(int j=0;j<3;j++){
            double relative=sum[j]/Math.max(1,subjectN)-bg[j]/Math.max(1,backgroundN);
            // A scene-level preference stabilizes difficult or ambiguous photos.
            double prior=(j==0?1.1*smooth(.015,.08,a.skinFraction)*(1-smooth(.20,.39,a.skinFraction)):
                j==1?.70*smooth(25,55,a.contrast)*a.focusConfidence:
                .24+.80*(1-a.focusConfidence)+.5*smooth(.08,.31,a.highSaturation));
            double score=1.1*clampD(relative,-15,15)+prior-risk[j]/Math.max(1,subjectN+backgroundN)*1.7;
            if(j==0 && a.skinFraction>.035 && a.skinFraction<.24)score+=.85;
            // A desert or tan wall can occupy half the frame and have skin-like RGB.
            // Never claim this is a portrait or let it force a conservative candidate.
            if(j==0 && a.skinFraction>.29)score-=4.5;
            if(j==1 && a.focusConfidence<.14)score-=1.1;
            if(score>best){best=score;chosen=j;}
        }
        return chosen;
    }

    private static int pixel(int c,double px,double py,int x,int y,Analysis a){
        return pixel(c,px,py,x,y,a,a.selectedTreatment);
    }
    private static int pixel(int c,double px,double py,int x,int y,Analysis a,int treatment){
        // Preserve physically clipped pure white; no detail can be invented offline.
        if((c&0x00ffffff)==0x00ffffff)return 0xffffffff;
        double r=((c>>>16)&255)/255.0,g=((c>>>8)&255)/255.0,b=(c&255)/255.0;
        double lum=lightness(c),gray=neutral(lum);
        double noct=a.nocturne,st=a.styleStrength;
        double skin=skinMask(r,g,b,lum);
        double map=mix(sampleCurve(DAY_CURVE,lum),sampleCurve(DARK_CURVE,lum),noct);
        double posx=clampD(px*a.iw-.5,0,a.iw-1),posy=clampD(py*a.ih-.5,0,a.ih-1);
        double local=sample(a.illumination,a.iw,a.ih,posx,posy);
        double wide=sample(a.wideField,a.iw,a.ih,posx,posy);
        // v0.6: first hold the scene down, then lift only evidenced visual subjects.
        // All masks are soft and deterministic; no synthetic geometry or shadows.
        double safeMid=smooth(9,25,lum)*(1-smooth(87,99,lum));
        double normalized=(wide-a.median)/Math.max(18,a.contrast*.42);
        double zone=clampD(normalized,-1.15,1.15);
        double focus=clampD(sample(a.attention,a.iw,a.ih,posx,posy),0,1);
        double subject=smooth(.11,.72,focus);
        double sem=0,far=0,near=0,photoLight=.5;
        double allocatedLift=0,allocatedBurn=0,environmentBudget=0,highReserve=1;
        if(a.fusion!=null && a.fusion.hasGuidance()){
            double mx=clampD(px*a.fusion.width-.5,0,a.fusion.width-1);
            double my=clampD(py*a.fusion.height-.5,0,a.fusion.height-1);
            sem=sample(a.fusion.subject,a.fusion.width,a.fusion.height,mx,my);
            far=sample(a.fusion.far,a.fusion.width,a.fusion.height,mx,my);
            near=sample(a.fusion.near,a.fusion.width,a.fusion.height,mx,my);
            photoLight=sample(a.fusion.light,a.fusion.width,a.fusion.height,mx,my);
            if(a.exposure!=null && a.exposure.enabled()){
                allocatedLift=sample(a.exposure.subjectDodge,a.iw,a.ih,posx,posy);
                allocatedBurn=sample(a.exposure.backgroundBurn,a.iw,a.ih,posx,posy);
                highReserve=sample(a.exposure.highlightReserve,a.iw,a.ih,posx,posy);
                if(a.environment!=null && a.environment.active())
                    environmentBudget=sample(a.environment.backgroundRecession,a.iw,a.ih,posx,posy);
            }
        }
        double depthZone=smooth(-.35,.65,zone);
        // Separate dominant subject from surrounding structural layers, not a radial vignette.
        double farContext=clampD((.67-.52*subject)*(.6+.4*(1-depthZone)),0,1);
        double sculpt=(treatment==1?6.2:treatment==0?4.4:4.9)*zone*a.lightDepth*safeMid;
        double shaped=clampD((local-wide)/Math.max(15,a.contrast*.43),-1,1);
        sculpt+=1.9*shaped*safeMid;
        double localLight=smooth(-14,20,local-a.median);
        double directional=((px-a.focusX)*a.lightDx+(py-a.focusY)*a.lightDy);
        double lightSide=clampD(directional*1.2,-.55,.55);
        // Image-wide shadows are allowed to recede; do NOT lift all midtones.
        double highKey=smooth(57,78,a.median)*(1-.30*smooth(50,80,a.contrast));
        double hold=-(treatment==1?3.9:treatment==0?2.9:3.2)*(1-.30*noct)*safeMid;
        double negative=-(treatment==1?16.5:treatment==0?10.8:11.8)*a.focusConfidence
                *farContext*safeMid*(.94-.24*localLight);
        // Dodge follows visual saliency + natural illumination, not an arbitrary oval light.
        double dodge=(treatment==0?18.8:treatment==1?20.4:14.2)*subject*safeMid
                *(.50+.50*localLight)*(1-(treatment==0?.27:.48)*skin);
        double relight=(treatment==1?6.8:treatment==0?3.5:4.0)
                *subject*lightSide*safeMid*(1-.64*skin);
        sculpt+=negative+dodge+hold+relight;
        // Genuine fusion: role != illumination != distance. A subject in shadow can
        // be gently recovered but is not transformed into a fabricated direct light.
        // Distant regions may recede only where depth geometry is credible.
        // Three genuinely different MODEL-GUIDED lighting hypotheses:
        // 0 portrait/object presence, 1 recession through depth, 2 existing-lit
        // surfaces. They retain the proven v0.8.2 rendering EXACTLY when no
        // model passes the independent trust gates above.
        // v0.8.6: The model indicates where the subject is, but independently
        // budgeted source exposure decides HOW MUCH light is actually available.
        // Do not brighten a lit doorway just because U2NETP selects the doorway.
        double semanticDodge=(treatment==0?14.2:treatment==1?5.7:3.6)*allocatedLift*safeMid
                *(1-.52*skin);
        double blueSkyProtection=smooth(.045,.16,b-(r+g)*.5)*smooth(34,68,lum);
        // A coarse semantic foreground blob may leak over real sky. The gate uses
        // CYAN ordering (B > G > R) rather than "anything blue" so purple painted
        // doors and indigo fabrics are not automatically suppressed. No local
        // highlight should create a fake white spotlight in blue open sky.
        double openSkyMaterial=smooth(.045,.13,g-r)*smooth(.06,.14,b-g)*smooth(27,52,lum);
        // A soft segmentation mask can include bright cloud or pale background
        // through an object's openings. Do not brighten surfaces which are
        // already strongly exposed: this prevents synthetic halos and blown
        // white patches, without reducing the lift of a genuinely dark subject.
        double prelitGuard=1-.97*smooth(49,68,lum);
        semanticDodge*=(1-.97*openSkyMaterial)*prelitGuard*(.50+.50*highReserve);
        // Never crush true night atmosphere simply because a depth net sees far.
        // High-key photographs tolerate a slightly stronger spatial separation.
        double sceneDepthBudget=(1-.58*noct)*(1+.14*highKey);
        double distantRecede=-(treatment==1?13.0:treatment==0?3.7:4.1)*far*safeMid
                *(1-.6*sem)*(.89-.31*localLight)*(1-.75*blueSkyProtection)*sceneDepthBudget;
        double nearStructure=(treatment==0?2.4:treatment==1?1.4:1.2)*near*safeMid
                *(.38+.62*photoLight);
        // Existing-photons hypothesis: add light ONLY to structures supported
        // by actual pre-existing local illumination. Neither near depth nor high
        // image saliency alone constitutes a light source.
        double litEvidence=smooth(.62,.88,photoLight);
        double sceneAnchor=(.55*sem+.45*subject*clampD(a.saliencyDistinctiveness,0,1));
        double litTrust=a.fusion==null?0:clampD(a.fusion.semanticTrust+.24*a.fusion.depthTrust,0,1);
        double existingLightDodge=(treatment==2?12.5:treatment==0?3.8:2.6)
                *allocatedLift*litEvidence*litTrust*safeMid*(1-.32*skin);
        existingLightDodge*=(1-.97*openSkyMaterial)*prelitGuard;
        // Negative fill is a separate bounded exposure budget. The current
        // algorithmic depth recession remains, but any EXTRA model-guided burn
        // requires a credible subject and originally exposed background.
        double backgroundNegative=-(treatment==1?7.6:treatment==0?3.2:2.1)
                *allocatedBurn*safeMid*(1-.82*blueSkyProtection);
        // v0.8.8: when the foreground is ALREADY LIT, emphasize it by allowing
        // modest local recession of midtone surroundings, not another spotlight.
        // Pixel-level guards protect sky/hole highlights and already black regions.
        double exposedBackground=smooth(34,55,lum)*(1-smooth(78,91,lum));
        double contextRecession=-(treatment==0?5.0:treatment==1?7.4:8.2)
                *environmentBudget*safeMid*exposedBackground
                *(1-.96*openSkyMaterial)*(1-.88*blueSkyProtection)
                *(1-.90*skin);
        // Scene-coordinated hypotheses. An explicit zero-strength ablation recovers
        // EXACT v0.8.8 RGB pixels, independent of the presence of valid models.
        double od=1,or=1,on=1,ol=1,oe=1;
        if(a.director!=null && a.director.active){
            double k=a.orchestratorStrength;
            od=mix(1,a.director.dodge[treatment],k);
            or=mix(1,a.director.recession[treatment],k);
            on=mix(1,a.director.negative[treatment],k);
            ol=mix(1,a.director.natural[treatment],k);
            oe=mix(1,a.director.context[treatment],k);
        }
        sculpt+=a.guidanceGain*(od*semanticDodge+or*distantRecede+nearStructure+
                            ol*existingLightDodge+on*backgroundNegative+
                            a.environmentStrength*oe*contextRecession);
        sculpt*=1-.30*smooth(74,97,lum);
        map+=sculpt;
        // High-key images may be redirected toward a low-key visual story, while
        // retaining white/highlight endpoints and natural local illumination.
        map-=highKey*((treatment==1?27.5:23.0)*(1-subject)+4.6)*smooth(12,48,lum)
             *(.72+.28*(1-smooth(98,100,lum)));
        // Clear blue skies hold dramatic density without artificial sharp gradients.
        double preBlue=b-(r+g)*.5;
        double skyBlue=smooth(.045,.16,preBlue)*smooth(32,67,lum)*(1-.50*noct);
        map-=skyBlue*(4.9+2.2*(1-py))*st;
        // Compress intense existing highlights instead of attempting to recover clipping.
        double high=smooth(83,97,lum)*smooth(89,99,a.p98);
        map-=2.1*high;
        map=clampD(lum+st*(map-lum),0,100);
        double target=neutral(map);
        double blue=b-(r+g)*.5;
        double sky=smooth(.057,.18,blue)*smooth(27,63,lum)*(1-.42*noct);
        double yellow=(r+g)*.5-b;
        double warm=smooth(.025,.15,yellow)*smooth(22,66,lum)*(1-.52*skin);
        double shadow=smooth(3,17,lum)*(1-smooth(35,69,lum));
        double neutralShadow=shadow*(1-.75*warm)*(1-.78*skin);
        // Saturation lowers in bright chromatic peaks, while middle density holds on plaster.
        double spread=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
        double chroma=.957-.16*smooth(.30,.65,spread)+.035*smooth(.05,.17,spread)*(1-smooth(.26,.50,spread));
        chroma=mix(1,chroma,st);
        double R=target+(r-gray)*chroma;
        double G=target+(g-gray)*chroma;
        double B=target+(b-gray)*chroma;
        // Shadow/cyan split tone tracks luminance and scene structure, not absolute position.
        double coldZone=.72+.28*smooth(0,1,-zone);
        double colorStyle=(treatment==2?1.28:treatment==0?.88:1.0);
        double cool=(.063+.014*noct)*neutralShadow*st*coldZone*(1-.18*subject)
                *colorStyle*(1-.42*skin);
        double ambientCold=(.012+.055*highKey)*(1-subject)*smooth(12,46,lum)*st*colorStyle;
        cool+=ambientCold;
        R-=cool*.84;G+=cool*.09;B+=cool*.89;
        // Warm reflected sunlight, boosted in genuine bright warm chromatic zones.
        double sunZone=.68+.24*smooth(0,1,zone)+.24*subject;
        double sunlight=(.069+.008*(1-noct))*warm*(1-smooth(89,100,lum))*st*sunZone*colorStyle;
        R+=sunlight*.95;G+=sunlight*.24;B-=sunlight*.81;
        double purple=smooth(.018,.095,Math.min(r-g,b-g))*shadow*(.22+.78*noct)*st;
        R-=.039*purple;G+=.015*purple;
        // Blue density: add depth while preserving lighter sky gradients.
        double density=sky*st*(.84+.16*(1-noct))*(treatment==2?1.18:1);
        R-=.032*density;G-=.022*density;B-=.007*density;
        // Complementary COLOR DENSITY is not exposure and never global saturation.
        // Only surrounding, colorful, already-exposed background is gently muted,
        // while original skin/sky and model-predicted foreground are preserved.
        if(a.colorDensity!=null && a.colorDensity.active() && a.guidanceGain>0 && a.colorStrength>0){
            double capacity=sample(a.colorDensity.capacity,a.iw,a.ih,posx,posy);
            double styleScale=a.director!=null?a.director.color[treatment]:1.0;
            double chromatic=smooth(.09,.27,spread);
            double semanticInterior=(a.fusion==null||a.fusion.semanticTrust<.35)?0:
                smooth(.08,.38,sem/Math.max(.001,a.fusion.semanticTrust));
            double densityGain=clampD(capacity*chromatic*(1-semanticInterior)*styleScale*a.guidanceGain*
                a.colorStrength*.12,0,.105);
            double highlightGuard=1-smooth(77,91,lum);
            double skyMaterial=smooth(.05,.10,g-r)*smooth(.06,.11,b-g)*smooth(27,48,lum);
            densityGain*=highlightGuard*(1-smooth(.24,.58,skyMaterial))*(1-.95*skin);
            // Linear RGB interpolation toward approximately equal luma avoids a
            // hue shift while reducing high-frequency chromatic competition.
            double localNeutral=.2126*R+.7152*G+.0722*B;
            R=mix(R,localNeutral,densityGain);G=mix(G,localNeutral,densityGain);
            B=mix(B,localNeutral,densityGain);
        }
        // Hard bounds for black/white reproduction. Film character lives in between.
        double guard=smooth(1.5,8,lum)
             *(1-(1-.96*highKey*(1-.62*subject))*smooth(91,99.6,lum));
        R=mix(r,R,guard);G=mix(g,G,guard);B=mix(b,B,guard);
        // Converge out-of-gamut colors toward neutral, never independent RGB clipping.
        double hi=Math.max(R,Math.max(G,B)),lo=Math.min(R,Math.min(G,B));
        if(hi>1||lo<0){
            double neutralTarget=clampD(target,0,1),factor=1;
            if(hi>1&&hi>neutralTarget)factor=Math.min(factor,(1-neutralTarget)/(hi-neutralTarget));
            if(lo<0&&lo<neutralTarget)factor=Math.min(factor,neutralTarget/(neutralTarget-lo));
            factor=clampD(factor,0,1);
            R=neutralTarget+(R-neutralTarget)*factor;
            G=neutralTarget+(G-neutralTarget)*factor;
            B=neutralTarget+(B-neutralTarget)*factor;
        }
        double grain=(noise(x,y)-.5)*.004*st*smooth(11,28,lum)*(1-smooth(75,97,lum));
        int ro=clamp((int)Math.round((R+grain)*255),0,255);
        int go=clamp((int)Math.round((G+grain)*255),0,255);
        int bo=clamp((int)Math.round((B+grain)*255),0,255);
        int output=0xff000000|(ro<<16)|(go<<8)|bo;
        return ToneSafety.protect(c,output,a.toneSafetyStrength);
    }
    private static double skinMask(double r,double g,double b,double L){
        return smooth(.018,.080,r-g)*smooth(.012,.105,g-b)
         *(1-smooth(.25,.44,r-g))*(1-smooth(.22,.40,g-b))
         *smooth(17,36,L)*(1-smooth(79,95,L));
    }
    private static void validate(int[] pixels,int width,int height){
        if(pixels==null||width<=0||height<=0||(long)width*height!=pixels.length)
            throw new IllegalArgumentException("Invalid pixels");
    }
    private static double percentile(int[] h,int count,double p){
        int need=Math.max(1,(int)Math.ceil(count*p)),total=0;
        for(int i=0;i<h.length;i++){total+=h[i];if(total>=need)return i;}
        return 100;
    }
    private static double lightness(int p){
        double lum=.2126729*LINEAR[(p>>>16)&255]+.7151522*LINEAR[(p>>>8)&255]+.072175*LINEAR[p&255];
        return LSTAR[clamp((int)Math.round(lum*4096),0,4096)];
    }
    private static double neutral(double L){
        double z=clampD(10*L,0,1000);int i=(int)z;
        return i>=1000?NEUTRAL[1000]:mix(NEUTRAL[i],NEUTRAL[i+1],z-i);
    }
    private static double sampleCurve(double[] lut,double L){
        double z=clampD(L*10,0,1000);int i=(int)z;
        return i>=1000?lut[1000]:mix(lut[i],lut[i+1],z-i);
    }
    private static double[] curve(double[] v){
        int n=KEYS.length;double[] sec=new double[n-1],m=new double[n];
        for(int i=0;i<n-1;i++)sec[i]=(v[i+1]-v[i])/(KEYS[i+1]-KEYS[i]);
        m[0]=sec[0];m[n-1]=sec[n-2];
        for(int i=1;i<n-1;i++){
            if(sec[i-1]*sec[i]<=0)m[i]=0;
            else{
                double a=KEYS[i]-KEYS[i-1],b=KEYS[i+1]-KEYS[i];
                double w1=2*b+a,w2=b+2*a;
                m[i]=(w1+w2)/(w1/sec[i-1]+w2/sec[i]);
            }
        }
        double[] out=new double[1001];int seg=0;
        for(int i=0;i<out.length;i++){
            double x=i/10.0;while(seg+2<n&&x>KEYS[seg+1])seg++;
            double h=KEYS[seg+1]-KEYS[seg],t=(x-KEYS[seg])/h,t2=t*t,t3=t2*t;
            out[i]=(2*t3-3*t2+1)*v[seg]+(t3-2*t2+t)*h*m[seg]
                +(-2*t3+3*t2)*v[seg+1]+(t3-t2)*h*m[seg+1];
        }
        return out;
    }
    private static float[] blur(float[] in,int w,int h,int passes){
        int[] kern={1,4,6,4,1};float[] src=in.clone(),tmp=new float[in.length],dst=new float[in.length];
        for(int pass=0;pass<passes;pass++){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                float z=0;for(int j=-2;j<=2;j++)z+=kern[j+2]*src[y*w+clamp(x+j,0,w-1)];
                tmp[y*w+x]=z/16;
            }
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                float z=0;for(int j=-2;j<=2;j++)z+=kern[j+2]*tmp[clamp(y+j,0,h-1)*w+x];
                dst[y*w+x]=z/16;
            }
            float[] swap=src;src=dst;dst=swap;
        }
        return src;
    }
    private static double sample(float[] p,int w,int h,double x,double y){
        int x0=(int)x,y0=(int)y,x1=Math.min(x0+1,w-1),y1=Math.min(y0+1,h-1);
        return mix(mix(p[y0*w+x0],p[y0*w+x1],x-x0),
                mix(p[y1*w+x0],p[y1*w+x1],x-x0),y-y0);
    }
    private static double yToL(double x){return x>.008856451679?116*Math.cbrt(x)-16:903.296296*x;}
    private static double lToY(double x){return x>8?Math.pow((x+16)/116,3):x/903.296296;}
    private static double toSrgb(double x){return x<=.0031308?12.92*x:1.055*Math.pow(x,1/2.4)-.055;}
    private static double smooth(double a,double b,double x){double t=clampD((x-a)/(b-a),0,1);return t*t*(3-2*t);}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
    private static int clamp(int x,int a,int b){return Math.max(a,Math.min(x,b));}
    private static double clampD(double x,double a,double b){return Math.max(a,Math.min(x,b));}
    private static double noise(int x,int y){int k=x*374761393+y*668265263+65*1442695041;
        k=(k^(k>>>13))*1274126177;k^=k>>>16;return (k&65535)/65535.0;}
}
