package com.cinema65.oneclick;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Hardware-accelerated, single aligned before/after view with an accessible drag divider. */
public final class CompareView extends View {
    private static final int GOLD=Color.rgb(224,193,151);
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint ui=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame=new RectF();
    private Bitmap before,after;
    private float position=.5f;
    private boolean active=false;
    private float density;
    public CompareView(Context context){
        super(context);density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setContentDescription("电影前后对比，左右拖动滑块查看原图与处理效果");
    }
    public void setImages(Bitmap original,Bitmap processed){
        before=original;after=processed;position=.5f;invalidate();
    }
    public void clear(){before=null;after=null;invalidate();}
    public boolean hasImages(){return before!=null&&after!=null;}
    private float dp(float f){return f*density;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawColor(0xff080d10);
        if(!hasImages())return;
        float pw=after.getWidth(),ph=after.getHeight();
        if(pw<=0||ph<=0)return;
        float scale=Math.min(getWidth()/pw,getHeight()/ph);
        float w=pw*scale,h=ph*scale;
        float left=(getWidth()-w)*.5f,top=(getHeight()-h)*.5f;
        frame.set(left,top,left+w,top+h);
        canvas.save();canvas.clipRect(frame);
        canvas.drawBitmap(after,null,frame,bitmapPaint);
        float split=left+position*w;
        canvas.save();canvas.clipRect(left,top,split,top+h);
        canvas.drawBitmap(before,null,frame,bitmapPaint);
        canvas.restore();
        ui.setStrokeWidth(dp(2));ui.setStyle(Paint.Style.STROKE);ui.setColor(Color.WHITE);
        canvas.drawLine(split,top,split,top+h,ui);ui.setStyle(Paint.Style.FILL);
        float centerY=(top+top+h)*.5f;
        ui.setShadowLayer(dp(9),0,dp(2),0xa0000000);
        ui.setColor(Color.WHITE);canvas.drawCircle(split,centerY,dp(20),ui);ui.clearShadowLayer();
        ui.setColor(0xff142129);ui.setStrokeWidth(dp(2.3f));
        ui.setStyle(Paint.Style.STROKE);ui.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(split-dp(5),centerY-dp(5),split-dp(9),centerY,ui);
        canvas.drawLine(split-dp(9),centerY,split-dp(5),centerY+dp(5),ui);
        canvas.drawLine(split+dp(5),centerY-dp(5),split+dp(9),centerY,ui);
        canvas.drawLine(split+dp(9),centerY,split+dp(5),centerY+dp(5),ui);
        ui.setStyle(Paint.Style.FILL);
        if(w>dp(180)){
            drawLabel(canvas,"原图",left+dp(12),top+dp(16),true);
            drawLabel(canvas,"CINEMA 65",left+w-dp(12),top+dp(16),false);
        }
        canvas.restore();
    }
    private void drawLabel(Canvas c,String text,float edge,float top,boolean left){
        ui.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        ui.setTextSize(dp(11));float tw=ui.measureText(text);
        float pad=dp(10);float labelW=tw+pad*2,labelH=dp(29);
        float x=left?edge:edge-labelW;
        ui.setColor(0xd510171c);c.drawRoundRect(x,top,x+labelW,top+labelH,dp(12),dp(12),ui);
        ui.setColor(left?Color.WHITE:GOLD);c.drawText(text,x+pad,top+dp(19),ui);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!hasImages())return false;
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){active=true;getParent().requestDisallowInterceptTouchEvent(true);setPosition(e.getX());return true;}
        if(action==MotionEvent.ACTION_MOVE &&active){setPosition(e.getX());return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(active){setPosition(e.getX());active=false;performClick();return true;}
        }
        return true;
    }
    private void setPosition(float x){position=Math.max(0,Math.min(1,(x-frame.left)/Math.max(1,frame.width())));invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
}