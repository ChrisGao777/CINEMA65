package com.cinema65.oneclick;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class AutoAuditActivity extends Activity {
  private static final int PICK=965;
  private TextView status;
  private boolean running=false;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,32,24,24);
    root.setBackgroundColor(0xfff6f8f7);
    TextView title=new TextView(this);title.setText("CINEMA 65 · 自动测试实验室");title.setTextSize(23);root.addView(title);
    TextView intro=new TextView(this);intro.setText("一次选择最多20张照片，自动运行完整本地模型、U2NETP候选、深度、三种基础影调与三种导演风格，输出离线ZIP报告。无需逐张切换设置。\n\n本工具只验证工程指标；没有人工标注时不能证明主体识别正确或照片更好看。");intro.setPadding(0,18,0,18);root.addView(intro);
    Button select=new Button(this);select.setText("选择照片并自动测试");root.addView(select);
    status=new TextView(this);status.setText("等待开始");status.setTextSize(15);root.addView(status);
    setContentView(root);
    select.setOnClickListener(v->{if(running)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK);});
    String pending=getPreferences(MODE_PRIVATE).getString("AUDIT_PENDING",null);
    if(pending!=null)status.setText("上次实验中断于 "+pending+"，可能是原生模型崩溃。");
  }
  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(request!=PICK||result!=RESULT_OK||data==null||running)return;
    LinkedHashSet<Uri> selected=new LinkedHashSet<>();
    if(data.getData()!=null)selected.add(data.getData());
    ClipData clips=data.getClipData();
    if(clips!=null)for(int j=0;j<clips.getItemCount()&&selected.size()<20;j++)selected.add(clips.getItemAt(j).getUri());
    if(selected.isEmpty())return;
    running=true;
    new Thread(()->batch(new ArrayList<>(selected)),"c65-auto-audit").start();
  }
  private void stage(String s) {getPreferences(MODE_PRIVATE).edit().putString("AUDIT_PENDING",s).commit();runOnUiThread(()->status.setText(s));}
  private static void entry(ZipOutputStream zip,String name,byte[] data)throws IOException {zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();}
  private static void image(ZipOutputStream zip,String name,int[] pixels,int w,int h)throws IOException {
    Bitmap b=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    try{ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,85,out);entry(zip,name,out.toByteArray());}finally{b.recycle();}
  }
  private void batch(ArrayList<Uri> photos){
    Uri output=null;int done=0,failed=0;
    StringBuilder report=new StringBuilder("CINEMA65 AUTO AUDIT v0.9.5\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+" Android "+Build.VERSION.RELEASE+"\nNo raw paths or EXIF stored. No ground-truth semantic labels. Original-resolution export NOT tested.\n\n");
    try {
      ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,"CINEMA65_AUDIT_"+System.currentTimeMillis()+".zip");values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/CINEMA65");values.put(MediaStore.MediaColumns.IS_PENDING,1);
      output=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
      if(output==null)throw new IOException("MediaStore insert failed");
      try(OutputStream os=getContentResolver().openOutputStream(output);ZipOutputStream zip=new ZipOutputStream(os)){
        for(Uri uri:photos){
          String key=String.format(java.util.Locale.US,"photo_%02d",done+1);stage(key+" / "+photos.size());
          try{one(uri,key,zip,report);}catch(Throwable e){failed++;report.append(key+" ERROR: "+e.getClass().getSimpleName()+" "+String.valueOf(e.getMessage())+"\n");}
          done++;
        }
        report.append("\nProcessed "+done+"; exceptions "+failed+". Automated metrics do not establish photographic quality.\n");
        entry(zip,"report.txt",report.toString().getBytes(StandardCharsets.UTF_8));
      }
      ContentValues visible=new ContentValues();visible.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(output,visible,null,null);
      getPreferences(MODE_PRIVATE).edit().remove("AUDIT_PENDING").commit();
      int n=done,f=failed;runOnUiThread(()->{status.setText("完成 "+n+" 张，异常 "+f+"。ZIP 已保存到 下载/CINEMA65，可直接分享报告。");running=false;});
    }catch(Throwable e){
      if(output!=null)try{getContentResolver().delete(output,null,null);}catch(Exception ignored){}
      runOnUiThread(()->{status.setText("批量报告失败: "+e.getClass().getSimpleName());running=false;});
    }
  }
  private void one(Uri uri,String key,ZipOutputStream zip,StringBuilder report)throws Exception{
    Bitmap bitmap=null;
    try{
      ImageDecoder.Source src=ImageDecoder.createSource(getContentResolver(),uri);
      bitmap=ImageDecoder.decodeBitmap(src,(decoder,info,s)->{
        int w=info.getSize().getWidth(),h=info.getSize().getHeight();
        double ratio=Math.min(1.0,960.0/Math.max(w,h));
        decoder.setTargetSize(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)));
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
      });
      int w=bitmap.getWidth(),h=bitmap.getHeight();int[] input=new int[w*h];bitmap.getPixels(input,0,w,0,0,w,h);
      report.append("\n"+key+" "+w+"x"+h+"\n");
      image(zip,key+"/original.jpg",input,w,h);
      FilmCore.Analysis baseline=FilmCore.analyze(input,w,h);
      stage(key+" vision");
      LocalVision.Guidance g=LocalVision.inspect(this,bitmap,true,true);
      report.append("person: "+g.personStatus+"; face: "+g.faceStatus+"\nU2NETP: "+g.genericStatus+" ("+g.genericMs+"ms)\ndepth: "+g.depthStatus+"\n");
      if(g.genericProposal!=null){
        GenericSubjectGate.Review r=GenericSubjectGate.assess(g.genericProposal,g.mapW,g.mapH);
        report.append("48x48 mask geometry: "+r.summary()+"\n");
        Bitmap overlay=DebugMaps.map(bitmap,g.genericProposal,g.mapW,g.mapH,0);
        if(overlay!=null){try{ByteArrayOutputStream out=new ByteArrayOutputStream();overlay.compress(Bitmap.CompressFormat.JPEG,85,out);entry(zip,key+"/u2netp_overlay.jpg",out.toByteArray());}finally{overlay.recycle();}}
      }
      FilmCore.Analysis guided=FilmCore.copyAnalysis(baseline);
      FilmCore.applyGuidance(guided,g.subject,g.mapW,g.mapH,g.depth);
      report.append("fusion: "+guided.fusionSummary()+"\n");
      stage(key+" rendering");
      for(int i=0;i<3;i++){
        int[] candidate=input.clone();FilmCore.candidate(candidate,w,h,guided,i);
        CandidateRanker.Metrics m=new CandidateRanker.Metrics(input,candidate,w,h);
        report.append("classic "+i+": delta="+m.meanAbsDifference+" black="+m.newBlack+" white="+m.newWhite+" newEdges="+m.inventedEdgeRate+"\n");
        image(zip,key+"/classic_"+i+".jpg",candidate,w,h);
      }
      int[] core=input.clone();FilmCore.applyTile(core,w,h,0,0,w,h,guided);
      DirectorCuts.Plan plan=DirectorCuts.analyze(input,w,h,g.subject,g.mapW,g.mapH,g.hasPerson||g.hasFace||g.hasGeneric);
      for(int i=0;i<3;i++){
        int[] candidate=core.clone();DirectorCuts.apply(input,candidate,w,h,plan,i,1.0,100);
        CandidateRanker.Metrics m=new CandidateRanker.Metrics(input,candidate,w,h);
        report.append("director "+i+": delta="+m.meanAbsDifference+" black="+m.newBlack+" white="+m.newWhite+" newEdges="+m.inventedEdgeRate+"\n");
        image(zip,key+"/director_"+i+".jpg",candidate,w,h);
        double drop=0, dark=0;
        for(int k=0;k<input.length;k++){
          int a=input[k],b=candidate[k];
          double ya=.2126*((a>>>16)&255)+.7152*((a>>>8)&255)+.0722*(a&255);
          double yb=.2126*((b>>>16)&255)+.7152*((b>>>8)&255)+.0722*(b&255);
          drop+=ya-yb;
          if(ya>=25&&yb<25)dark++;
          if(ya<25&&yb>=25)dark--;
        }
        double factor=ToneBudgetGuard.strength(Math.max(0,drop/input.length),Math.max(0,dark/input.length));
        int[] guarded=new int[input.length];
        for(int k=0;k<input.length;k++){
          int a=input[k],b=candidate[k];
          int r=(int)Math.round(((a>>>16)&255)* (1-factor)+((b>>>16)&255)*factor);
          int gch=(int)Math.round(((a>>>8)&255)*(1-factor)+((b>>>8)&255)*factor);
          int bl=(int)Math.round((a&255)*(1-factor)+(b&255)*factor);
          guarded[k]=0xff000000|(r<<16)|(gch<<8)|bl;
        }
        image(zip,key+"/director_guarded_"+i+".jpg",guarded,w,h);
        report.append("director_guarded "+i+": factor="+factor+"\\n");
      }
      report.append("semantic accuracy: NOT VERIFIED; aesthetic preference: NOT VERIFIED; 48x48 masks cannot validate fine edges.\n");
    }finally{if(bitmap!=null)bitmap.recycle();}
  }
}
