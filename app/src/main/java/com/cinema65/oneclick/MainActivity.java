package com.cinema65.oneclick;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Context;
import android.content.ContentValues;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** CINEMA65 v0.8: on-device fusion of visual analysis and cinematic grading; preview analyzes, export retains full source pixels. */
public final class MainActivity extends Activity {
    private static final int PICK=65;
    private static final int INK=0xff243946,GOLD=0xfff1ad77,WHITE=0xff233947,MUTED=0xff607883;
    private CompareView comparison;
    private FrameLayout preview;
    private LinearLayout placeholder;
    private TextView chip, heading, hint;
    private Button pickButton,saveButton,likeButton,labButton,maskButton,recordButton;
    private ProgressBar spinner;
    private Bitmap originalPreview,gradedPreview;
    private Uri selectedSource;
    private FilmCore.Analysis scene,baseScene,guidedScene;
    private LocalVision.Guidance latestGuidance;
    private Bitmap diagnosticPreview;
    private String modelDetails="尚未分析照片";
    private String activeRunId=null;
    private double lastModelContribution=Double.NaN;
    private int autoTreatment=1,baseAutoTreatment=1,manualTreatment=-1,strengthPercent=100;
    private boolean fusionEnabled=true;
    // v0.9.0 Director Cuts are an optional post-grade finishing layer.
    // The default experimental look is Natural Light, with one-tap baseline A/B.
    private volatile int directorCut=1; // -1 disables; 0 subject, 1 natural light, 2 warm/cool
    private DirectorCuts.Plan activeDirectorPlan;

    private long lastAnalysisMs=0;
    private String previewMode="成片";
    private int selectedWidth,selectedHeight;
    private volatile boolean busy=false;
    private int generation=0;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xfff7f8f4);getWindow().setNavigationBarColor(0xfff7f8f4);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(19),dp(20),dp(19),dp(18));
        page.setBackground(gradient(0xfffcf9f3,0xffe8f3f4,0));setContentView(page);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);titles.addView(brand);
        TextView title=text("CINEMA",26,WHITE,true);title.setLetterSpacing(.11f);brand.addView(title);
        TextView number=text(" 65",26,GOLD,true);brand.addView(number);
        TextView slogan=text("LIGHT & STORY  /  ON DEVICE",9,MUTED,true);
        slogan.setLetterSpacing(.14f);slogan.setPadding(0,dp(6),0,0);titles.addView(slogan);
        TextView offline=text("●  离线智能",10,0xff397c70,true);
        offline.setGravity(Gravity.CENTER);offline.setPadding(dp(11),dp(9),dp(11),dp(9));
        offline.setBackground(round(0xffdff3e8,14,0xffa5d7c9));header.addView(offline);

        TextView section=text("光影 · 让画面有主角",11,GOLD,true);
        section.setLetterSpacing(.15f);section.setPadding(dp(3),dp(18),0,dp(10));page.addView(section);
        preview=new FrameLayout(this);preview.setBackground(round(0xfff1f4f1,22,0xffd5e4e4));
        preview.setClipToOutline(true);page.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        comparison=new CompareView(this);comparison.setVisibility(View.INVISIBLE);
        preview.addView(comparison,new FrameLayout.LayoutParams(-1,-1));
        placeholder=new LinearLayout(this);placeholder.setGravity(Gravity.CENTER);
        placeholder.setOrientation(LinearLayout.VERTICAL);
        preview.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        TextView icon=text("◎",75,0xff70a5ad,false);icon.setGravity(Gravity.CENTER);placeholder.addView(icon);
        TextView empty=text("光影，交给 CINEMA 65",17,WHITE,true);empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(5),dp(10),dp(5),0);placeholder.addView(empty);
        TextView subtitle=text("从相册选取 · 自动处理 · 原片不变",12,MUTED,false);
        subtitle.setGravity(Gravity.CENTER);subtitle.setPadding(dp(5),dp(12),dp(5),0);placeholder.addView(subtitle);
        chip=text("C65  /  DIRECTOR CUTS  /  STABILITY",10,WHITE,true);chip.setLetterSpacing(.09f);
        chip.setPadding(dp(12),dp(8),dp(12),dp(8));chip.setBackground(round(0xe8f4f8fa,13,0x556c9aa6));
        FrameLayout.LayoutParams chipP=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        chipP.setMargins(0,dp(12),0,0);preview.addView(chip,chipP);chip.setVisibility(View.GONE);
        spinner=new ProgressBar(this);spinner.setIndeterminateTintList(ColorStateList.valueOf(GOLD));
        preview.addView(spinner,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));spinner.setVisibility(View.GONE);

        LinearLayout description=new LinearLayout(this);description.setOrientation(LinearLayout.VERTICAL);
        description.setPadding(dp(3),dp(16),dp(3),dp(14));page.addView(description);
        heading=text("等待你的下一帧",18,WHITE,true);description.addView(heading);
        hint=text("只需选择照片，其余交给 CINEMA 65。",12,MUTED,false);
        hint.setPadding(0,dp(7),0,0);description.addView(hint);
        // A compact, direct-access test toolbar; essential actions need no nested menus.
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        page.addView(controls);
        LinearLayout mainActions=new LinearLayout(this);mainActions.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(mainActions,new LinearLayout.LayoutParams(-1,dp(49)));
        pickButton=makeButton("① 选照片",false);
        labButton=makeButton("② 导演风格",false);
        maskButton=makeButton("③ 看选区",false);
        addEqualButton(mainActions,pickButton,true);
        addEqualButton(mainActions,labButton,true);
        addEqualButton(mainActions,maskButton,false);
        LinearLayout finishActions=new LinearLayout(this);finishActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams finishP=new LinearLayout.LayoutParams(-1,dp(49));finishP.topMargin=dp(7);
        controls.addView(finishActions,finishP);
        recordButton=makeButton("④ 诊断记录",false);
        saveButton=makeButton("⑤ 保存原尺寸",true);
        addEqualButton(finishActions,recordButton,true);
        addEqualButton(finishActions,saveButton,false);
        saveButton.setEnabled(false);saveButton.setAlpha(.48f);
        labButton.setEnabled(true);maskButton.setEnabled(false);
        labButton.setAlpha(1f);maskButton.setAlpha(.48f);
        pickButton.setOnClickListener(v->pick());saveButton.setOnClickListener(v->save());
        labButton.setOnClickListener(v->openLab());
        maskButton.setOnClickListener(v->openMaskChooser());
        recordButton.setOnClickListener(v->openRecordHistory());
        // A light-touch preference action is available in the style sheet.
        likeButton=makeButton("♡ 喜欢这次风格",false);
        likeButton.setOnClickListener(v->rememberPreference());
        TextView footer=text("C65 · 蒙版质量诊断 v0.9.5 · 离线 · 不上传原图",10,0xff748d91,true);
        footer.setGravity(Gravity.CENTER);footer.setLetterSpacing(.035f);
        footer.setPadding(0,dp(9),0,0);page.addView(footer);
        // Detect native kills / LMK that bypass Java try/catch. Never store photo paths.
        String interrupted=getSharedPreferences("C65_CRASH_STAGE",MODE_PRIVATE).getString("active",null);
        if(interrupted!=null){
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean("C65_ADVANCED",false)
                .putBoolean("C65_ONNX_GENERIC",false).apply();
            getSharedPreferences("C65_CRASH_STAGE",MODE_PRIVATE).edit().remove("active").commit();
            try{DiagnosticStore.create(this,"v0.9.5 上次选图分析未正常完成\n最后阶段："+interrupted+
                "\n自动回到稳定模式；可能是内存不足或原生推理崩溃，需 logcat 才能判定根因。\n");}
            catch(Exception ignored){}
            hint.setText("检测到上次处理异常中断；已切换稳定模式。④可查看记录。 ");
        }else{
            hint.setText("稳定模式默认开启 · 点击②可手动启用完整模型实验");
        }
        handleSharedImage(getIntent());
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String label,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(label);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return t;
    }
    private GradientDrawable round(int bg,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private GradientDrawable gradient(int a,int b,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(radius));return d;
    }
    private Button makeButton(String name,boolean primary){
        Button b=new Button(this);b.setText(name);b.setTextSize(14);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(primary?INK:INK);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55ffffff),
            round(primary?GOLD:0xfff0f5f3,17,primary?0:0xffa4c1c4),null));return b;
    }
    private void addEqualButton(LinearLayout row,Button btn,boolean margin){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        if(margin)lp.rightMargin=dp(7);
        row.addView(btn,lp);
        btn.setTextSize(12);
        btn.setPadding(dp(2),0,dp(2),0);
    }
    private boolean advancedMode(){return getPreferences(MODE_PRIVATE).getBoolean("C65_ADVANCED",false);}
    private boolean genericOnnxEnabled(){
        return getPreferences(MODE_PRIVATE).getBoolean("C65_ONNX_GENERIC",false);
    }
    private void toggleGenericOnnx(){
        if(!genericOnnxEnabled()){
            new AlertDialog.Builder(this).setTitle("单独启用 U2NETP ONNX？")
                .setMessage("这是 v0.9.3 兼容性测试项。完整模式默认跳过 U2NETP；"
                    +"单独开启后，需在完整模式下重新选择照片。"
                    +"若原生推理崩溃，下次启动将自动关闭这两项实验开关。")
                .setNegativeButton("取消",null)
                .setPositiveButton("单独启用",(dialog,which)->{
                    getPreferences(MODE_PRIVATE).edit().putBoolean("C65_ONNX_GENERIC",true).apply();
                    Toast.makeText(this,"U2NETP 已开启，使用完整模式重新选图生效",Toast.LENGTH_LONG).show();
                }).show();
        }else{
            getPreferences(MODE_PRIVATE).edit().putBoolean("C65_ONNX_GENERIC",false).apply();
            Toast.makeText(this,"U2NETP 已关闭，重新选图生效",Toast.LENGTH_LONG).show();
        }
    }
    private void markStage(String stage){
        getSharedPreferences("C65_CRASH_STAGE",MODE_PRIVATE).edit().putString("active",stage).commit();
    }
    private void clearStage(){getSharedPreferences("C65_CRASH_STAGE",MODE_PRIVATE).edit().remove("active").commit();}
    private void toggleAdvanced(){
        boolean next=!advancedMode();
        if(next){
            new AlertDialog.Builder(this).setTitle("开启完整模型实验？")
                .setMessage("将启用人物、深度和审美推理。U2NETP ONNX 默认独立关闭，需在②另行开启。此模式仍可能触发其他原生模型错误。开启后请重新选图。")
                .setNegativeButton("取消",null).setPositiveButton("继续实验",(d,w)->{
                    getPreferences(MODE_PRIVATE).edit().putBoolean("C65_ADVANCED",true).apply();
                    Toast.makeText(this,"完整模型实验已开启；请重新选照片",Toast.LENGTH_LONG).show();
                }).show();
        }else{
            getPreferences(MODE_PRIVATE).edit().putBoolean("C65_ADVANCED",false).apply();
            Toast.makeText(this,"已使用低内存稳定模式；重新选照片生效",Toast.LENGTH_LONG).show();
        }
    }
    private void rememberPreference(){
        if(scene==null)return;
        boolean isDirector=directorCut>=0;
        int current=isDirector?directorCut:scene.selectedTreatment;
        String key=(isDirector?"C65_DIRECTOR_PREFERENCE_":"C65_TREATMENT_")+current;
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        float weight=Math.min(.50f,prefs.getFloat(key,0)+.04f);
        prefs.edit().putFloat(key,weight).apply();
        String name=isDirector?DirectorCuts.name(current):treatment(current);
        DiagnosticStore.event(this,activeRunId,"用户偏好："+name+
            (isDirector?"（仅评审记录，不参与自动择优）":"（影响经典候选评分）"));
        Toast.makeText(this,isDirector?"导演风格偏好已记录，尚未启用自动学习":"已在本机记录偏好",Toast.LENGTH_LONG).show();
    }
    private void openMaskChooser(){
        if(originalPreview==null)return;
        String[] options={"查看正常成片","人物 / 主体蒙版","关注点权重图","相对深度图","实际提亮区域","实际压暗区域","融合主体引导","背景纵深引导","主体提亮预算（意图）","背景压暗预算（意图）","环境衬托预算（意图）","背景色彩预算（意图）","U2NETP 候选蒙版（仅诊断）"};
        new AlertDialog.Builder(this).setTitle("③ 查看实际选区")
            .setItems(options,(d,which)->{
                switch(which){
                    case 0: showResult();heading.setText("你的电影画面，已经完成");break;
                    case 1: displayMask("主体");break;
                    case 2: displayMask("关注点");break;
                    case 3: displayMask("深度");break;
                    case 4: displayMask("提亮");break;
                    case 5: displayMask("压暗");break;
                    case 6: displayMask("融合主体");break;
                    case 7: displayMask("背景纵深");break;
                    case 8: displayMask("主体提亮预算");break;
                    case 9: displayMask("背景压暗预算");break;
                    case 10: displayMask("环境衬托预算");break;
                    case 11: displayMask("背景色彩预算");break;
                    case 12: displayMask("U2NETP候选");break;
                }
            }).setNegativeButton("返回",null).show();
    }
    private void copyText(String text){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("CINEMA 65 Lab diagnostics",text));
        Toast.makeText(this,"已复制诊断记录",Toast.LENGTH_SHORT).show();
    }
    private void exportRecords(){
        String content=DiagnosticStore.combined(this);
        try{
            String filename="CINEMA65_diagnostics_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".txt";
            ContentValues v=new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,filename);
            v.put(MediaStore.Downloads.MIME_TYPE,"text/plain");
            v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/CINEMA65");
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(uri==null)throw new IOException("Cannot create diagnostic text file");
            try(OutputStream os=getContentResolver().openOutputStream(uri)){
                if(os==null)throw new IOException("Cannot open diagnostic output");
                os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Toast.makeText(this,"诊断记录已保存到 下载/CINEMA65",Toast.LENGTH_LONG).show();
        }catch(Exception ex){Toast.makeText(this,"导出失败："+ex.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}
    }
    private void openRecordHistory(){
        java.io.File[] files=DiagnosticStore.all(this);
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17),dp(13),dp(17),dp(15));scroll.addView(panel);
        TextView description=text("记录保存在本机，每次分析自动写入；最多保留最近 50 次。不会保存照片或相册文件路径。",12,MUTED,false);
        panel.addView(description);
        addLabButton(panel,"导出全部记录为 TXT（下载/CINEMA65）",()->exportRecords());
        addLabButton(panel,"复制全部记录",()->copyText(DiagnosticStore.combined(this)));
        if(files.length==0){TextView empty=text("暂无诊断记录。处理一张照片后会自动创建。",13,INK,false);panel.addView(empty);}
        else for(int i=0;i<files.length;i++){
            java.io.File file=files[i];
            String preview=DiagnosticStore.read(file);
            String[] lines=preview.split("\n");
            String title=file.getName().replace("C65_","").replace(".txt","");
            if(lines.length>4)title+="  ·  "+lines[4].replace("照片：","");
            addLabButton(panel,"▤  "+title,()->showRecord(file));
        }
        new AlertDialog.Builder(this).setTitle("④ 诊断记录 · "+files.length+" 次")
             .setView(scroll).setPositiveButton("关闭",null).show();
    }
    private void showRecord(java.io.File file){
        String data=DiagnosticStore.read(file);
        ScrollView scroll=new ScrollView(this);TextView body=text(data,12,INK,false);
        body.setPadding(dp(18),dp(15),dp(18),dp(20));body.setTextIsSelectable(true);scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("诊断详情 · "+file.getName().replace(".txt",""))
            .setView(scroll).setNeutralButton("复制本条",(d,w)->copyText(data))
            .setPositiveButton("关闭",null).show();
    }
    private void pick(){
        if(busy)return;
        Intent intent;
        if(Build.VERSION.SDK_INT>=33){
            // Framework Photo Picker opens a PHOTO GRID, not ACTION_OPEN_DOCUMENT folders.
            intent=new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            // Pre-Android 13: prefer gallery-capable content selection, not document browser.
            intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {startActivityForResult(intent,PICK);}
        catch(Exception ex){
            try{
                Intent fallback=new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("image/*");
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(Intent.createChooser(fallback,"选择照片"),PICK);
            }catch(Exception unavailable){
                Toast.makeText(this,"没有可用的照片选择器，请检查相册应用",Toast.LENGTH_LONG).show();
            }
        }
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(request==PICK&&code==RESULT_OK){
            Uri selected=data==null?null:data.getData();
            if(selected==null&&data!=null&&data.getClipData()!=null&&
               data.getClipData().getItemCount()>0)
                selected=data.getClipData().getItemAt(0).getUri();
            if(selected!=null)process(selected);
            else Toast.makeText(this,"相册没有返回可读取的照片，请重试",Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleSharedImage(intent);}
    private void handleSharedImage(Intent intent){
        if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction()))return;
        if(intent.getType()==null||!intent.getType().startsWith("image/"))return;
        Uri uri;
        if(Build.VERSION.SDK_INT>=33)uri=intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
        else uri=(Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if(uri!=null)process(uri);
    }
    private void process(Uri uri){
        if(busy)return;
        busy=true;final int task=++generation;
        final boolean advanced=advancedMode();
        markStage(advanced?"full: decode":"safe: decode");
        pickButton.setEnabled(false);saveButton.setEnabled(false);saveButton.setAlpha(.5f);
        spinner.setVisibility(View.VISIBLE);placeholder.setVisibility(View.GONE);
        chip.setVisibility(View.GONE);comparison.setVisibility(View.INVISIBLE);
        labButton.setEnabled(false);maskButton.setEnabled(false);
        heading.setText("正在分析画面并雕刻光影");
        hint.setText(advanced?"完整模型实验 · 分析与渲染中…":"稳定模式 · 低内存预览（跳过原生模型）");
        new Thread(()->{
            Bitmap raw=null;
            long started=android.os.SystemClock.elapsedRealtime();
            try{
                ImageDecoder.Source source=ImageDecoder.createSource(getContentResolver(),uri);
                final int[] dims=new int[2];
                raw=ImageDecoder.decodeBitmap(source,(decoder,info,src)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                    dims[0]=w;dims[1]=h;
                    double scale=Math.min(1.0,1280.0/Math.max(w,h));
                    decoder.setTargetSize(Math.max(1,(int)Math.round(w*scale)),
                        Math.max(1,(int)Math.round(h*scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                });
                int w=raw.getWidth(),h=raw.getHeight();
                // All controls operate on small preview buffers only.
                int[] pixels=new int[w*h];raw.getPixels(pixels,0,w,0,0,w,h);
                if(!advanced){
                    // The crash-safe first pass is *fully useful* on its own:
                    // original film grade + three distinct Director Cuts.
                    // It must never load ONNX/MediaPipe/NIMA native runtimes.
                    markStage("safe: deterministic grade");
                    int[] input=pixels.clone();
                    FilmCore.Analysis classic=FilmCore.analyze(pixels,w,h);
                    DirectorCuts.Plan plan=DirectorCuts.analyze(input,w,h,null,48,48,false);
                    FilmCore.applyTile(pixels,w,h,0,0,w,h,classic);
                    DirectorCuts.apply(input,pixels,w,h,plan,1,1.0,100);
                    Bitmap graded=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
                    Bitmap original=raw;raw=null;
                    final long elapsed=android.os.SystemClock.elapsedRealtime()-started;
                    final String report="CINEMA 65 · v0.9.5 蒙版质量诊断\n照片："+dims[0]+"×"+dims[1]+
                        "\n模式：低内存稳定模式（跳过所有原生视觉模型及 NIMA）"+
                        "\n导演剪辑：自然光线；其他两种风格可在②手动切换"+
                        "\n主体与环境：无可信 AI 蒙版，安全回退基础影调"+
                        "\n分析耗时："+elapsed+" ms\n";
                    markStage("safe: UI handoff");
                    runOnUiThread(()->{
                        if(task!=generation){original.recycle();graded.recycle();clearStage();return;}
                        Bitmap oldOrig=originalPreview,oldGraded=gradedPreview;
                        originalPreview=original;gradedPreview=graded;selectedSource=uri;
                        scene=classic;baseScene=classic;guidedScene=classic;
                        latestGuidance=new LocalVision.Guidance();
                        latestGuidance.personStatus="稳定模式未运行";
                        latestGuidance.faceStatus="稳定模式未运行";
                        latestGuidance.genericStatus="稳定模式未运行（避免原生推理风险）";
                        latestGuidance.classifierStatus="稳定模式未运行";
                        latestGuidance.depthStatus="稳定模式未运行";
                        activeDirectorPlan=plan;directorCut=1;
                        autoTreatment=classic.selectedTreatment;baseAutoTreatment=classic.selectedTreatment;
                        manualTreatment=-1;fusionEnabled=false;strengthPercent=100;
                        lastAnalysisMs=elapsed;modelDetails=report;previewMode="成片";
                        lastModelContribution=0;
                        try{activeRunId=DiagnosticStore.create(this,report);}catch(Exception ignored){activeRunId=null;}
                        releaseDiagnostic();selectedWidth=dims[0];selectedHeight=dims[1];
                        comparison.setImages(original,graded);comparison.setVisibility(View.VISIBLE);
                        spinner.setVisibility(View.GONE);chip.setVisibility(View.VISIBLE);
                        busy=false;pickButton.setEnabled(true);saveButton.setEnabled(true);saveButton.setAlpha(1);
                        labButton.setEnabled(true);maskButton.setEnabled(true);labButton.setAlpha(1);maskButton.setAlpha(1);
                        heading.setText("导演剪辑 · 自然光线（稳定模式）");
                        hint.setText("② 切换导演风格 · 想试完整 AI？在②中手动开启后重新选照片");
                        if(oldOrig!=null&&oldOrig!=original)oldOrig.recycle();
                        if(oldGraded!=null&&oldGraded!=graded)oldGraded.recycle();
                        clearStage();
                    });
                    return;
                }
                markStage("full: vision models");
                FilmCore.Analysis baseline=FilmCore.analyze(pixels,w,h);
                FilmCore.Analysis stats=FilmCore.copyAnalysis(baseline);
                LocalVision.Guidance guidance=LocalVision.inspect(this,raw,true,genericOnnxEnabled());
                FilmCore.applyGuidance(stats,guidance.subject,guidance.mapW,guidance.mapH,guidance.depth);
                final boolean trustedSubject=guidance.hasPerson||guidance.hasFace||guidance.hasGeneric;
                markStage("full: director planning");
                final DirectorCuts.Plan director=DirectorCuts.analyze(pixels,w,h,
                    guidance.subject,guidance.mapW,guidance.mapH,trustedSubject);
                StringBuilder detailed=new StringBuilder();
                detailed.append("CINEMA 65 · v0.9.5 完整模型实验记录\n")
                        .append("照片：").append(dims[0]).append('×').append(dims[1]).append('\n')
                        .append("人物分割：").append(guidance.personStatus).append(" / ").append(guidance.personMs).append(" ms\n")
                        .append("面部关键点：").append(guidance.faceStatus).append(" / ").append(guidance.faceMs).append(" ms\n")
                        .append("通用主体（U2NETP）：").append(guidance.genericStatus).append(" / ").append(guidance.genericMs).append(" ms\n")
                        .append("分类标签：").append(guidance.classifierStatus).append(" / ").append(guidance.classifierMs).append(" ms\n")
                        .append("相对深度（MiDaS Small）：").append(guidance.depthStatus).append(" / ").append(guidance.depthMs).append(" ms\n")
                        .append("环境色偏估计：").append(String.format(Locale.US,"%.2f",guidance.warmCast)).append('\n')
                        .append("传统聚焦驱动强度：").append(String.format(Locale.US,"%.2f",baseline.focusConfidence)).append("（非概率，仅决定旧版塑光力度）\n")
                        .append("显著性区分度：").append(String.format(Locale.US,"%.2f",baseline.saliencyDistinctiveness)).append("（非语义识别置信度）\n")
                        .append("初始传统算法方案：").append(baseline.treatmentName()).append('\n');
                detailed.append("分离式融合：").append(stats.fusionSummary()).append('\n')
                    .append("修图策略：").append(stats.strategyName()).append('\n');
                markStage("full: scoring and diagnostic renders");
                // Preserve the original aspect ratio in both candidate rendering
                // and quality damage checks. NIMA itself center-crops without warp.
                double evScale=Math.min(1.,320.0/Math.max(w,h));
                int ew=Math.max(1,(int)Math.round(w*evScale));
                int eh=Math.max(1,(int)Math.round(h*evScale));
                Bitmap evaluation=Bitmap.createScaledBitmap(raw,ew,eh,true);
                int[] small=new int[ew*eh];evaluation.getPixels(small,0,ew,0,0,ew,eh);
                if(evaluation!=raw)evaluation.recycle();
                ModelRiskAuditor.Decision risk=ModelRiskAuditor.calibrate(stats,baseline,small,ew,eh);
                detailed.append("场景策略协调：").append(stats.strategySummary()).append('\n')
                    .append("局部色彩密度：").append(stats.colorSummary()).append('\n');
                detailed.append("曝光分配：").append(stats.exposureSummary()).append('\n');
                detailed.append("环境塑形：").append(stats.environmentSummary()).append('\n');
                detailed.append("模型画质闸门：").append(risk.summary()).append('\n');
                int oldTreatment=stats.selectedTreatment;
                double[] aesthetics={Double.NaN,Double.NaN,Double.NaN};
                double[] technicals={Double.NaN,Double.NaN,Double.NaN};
                double[] preferences=new double[3];
                CandidateRanker.Metrics[] damage=new CandidateRanker.Metrics[3];
                boolean scored=false;
                for(int candidate=0;candidate<3;candidate++){
                    int[] output=small.clone();
                    // Candidate has the same global subject/depth map as export.
                    // Use applyTile for downscaled size only after explicit candidate render.
                    FilmCore.candidate(output,ew,eh,stats,candidate);
                    damage[candidate]=new CandidateRanker.Metrics(small,output,ew,eh);
                    Bitmap b=Bitmap.createBitmap(output,ew,eh,Bitmap.Config.ARGB_8888);
                    LocalVision.ScoreResult aes=LocalVision.scoreDetailed(this,b,"nima_aesthetic_fp16.tflite");
                    LocalVision.ScoreResult tec=LocalVision.scoreDetailed(this,b,"nima_technical_fp16.tflite");
                    double aesthetic=aes.value,technical=tec.value;
                    detailed.append("候选 ").append(candidate==0?"主体细节":candidate==1?"空间塑光":"色彩叙事")
                         .append("：审美 ").append(Double.isFinite(aesthetic)?String.format(Locale.US,"%.2f",aesthetic):aes.status)
                         .append(" (").append(aes.elapsedMs).append("ms), 技术 ")
                         .append(Double.isFinite(technical)?String.format(Locale.US,"%.2f",technical):tec.status)
                         .append(" (").append(tec.elapsedMs).append("ms)\n");
                    b.recycle();
                    detailed.append("  新增死黑 ").append(String.format(Locale.US,"%.2f%%",100*damage[candidate].newBlack))
                        .append("，新增死白 ").append(String.format(Locale.US,"%.2f%%",100*damage[candidate].newWhite))
                        .append("，平均编辑差 ").append(String.format(Locale.US,"%.1f/255",damage[candidate].meanAbsDifference))
                        .append("，疑似新硬边 ").append(String.format(Locale.US,"%.3f%%",100*damage[candidate].inventedEdgeRate)).append('\n');
                    aesthetics[candidate]=aesthetic;technicals[candidate]=technical;
                    preferences[candidate]=getPreferences(MODE_PRIVATE).getFloat("C65_TREATMENT_"+candidate,0f);
                    if(Double.isFinite(aesthetic)){
                        scored=true;guidance.hasAesthetic=true;
                        guidance.hasTechnical|=Double.isFinite(technical);
                    }
                }
                CandidateRanker.Choice selection=CandidateRanker.select(oldTreatment,
                        aesthetics,technicals,damage,preferences);
                stats.selectedTreatment=selection.selected;
                detailed.append("候选决策：").append(selection.summary()).append('\n')
                    .append("NIMA输入：保留长宽比后取中心正方形；损伤检查：整幅缩略图\n");
                detailed.append("NIMA参与选择：").append(scored?"是":"否（使用传统候选）").append('\n')
                        .append("自动方案：").append(stats.treatmentName()).append('\n');
                // Quantify the actual influence of model guidance (same candidate, identical pixels).
                // A working model with no practical effect is explicitly visible to the tester.
                int[] control=small.clone(),guided=small.clone();
                FilmCore.candidate(control,ew,eh,baseline,stats.selectedTreatment);
                FilmCore.candidate(guided,ew,eh,stats,stats.selectedTreatment);
                long abs=0;int altered=0;
                for(int i=0;i<control.length;i++){
                    int a=control[i],b=guided[i];
                    int dr=Math.abs(((a>>>16)&255)-((b>>>16)&255));
                    int dg=Math.abs(((a>>>8)&255)-((b>>>8)&255));
                    int db=Math.abs((a&255)-(b&255));
                    abs+=dr+dg+db;if(Math.max(dr,Math.max(dg,db))>3)altered++;
                }
                final double contribution=abs/(3.0*control.length);
                detailed.append("模型对像素的真实贡献：平均绝对 RGB 差 ")
                    .append(String.format(Locale.US,"%.3f/255",contribution))
                    .append("，差异超过3级的像素 ")
                    .append(String.format(Locale.US,"%.1f%%",100.0*altered/control.length)).append('\n');
                detailed.append("模型引导生效：").append(stats.hasModelGuidance()&&stats.guidanceGain()>0?"是":"否（已保护回退）").append('\n');
                // Compare the same model-guided grade with only environment context
                // disabled. The input, treatment and tone-safety remain IDENTICAL.
                FilmCore.setEnvironmentStrength(stats,0);
                int[] environmentOff=small.clone();
                FilmCore.candidate(environmentOff,ew,eh,stats,stats.selectedTreatment);
                FilmCore.setEnvironmentStrength(stats,1);
                double contextDelta=0;int contextPixels=0;
                for(int i=0;i<environmentOff.length;i++){
                    int a=environmentOff[i],b=guided[i];
                    int rr=Math.abs(((a>>>16)&255)-((b>>>16)&255));
                    int gg=Math.abs(((a>>>8)&255)-((b>>>8)&255));
                    int bb=Math.abs((a&255)-(b&255));
                    contextDelta+=(rr+gg+bb)/3.0;
                    if(Math.max(rr,Math.max(gg,bb))>1)contextPixels++;
                }
                detailed.append("环境衬托的独立影响（同图同方案）：RGB 均差 ")
                    .append(String.format(Locale.US,"%.3f/255",contextDelta/environmentOff.length))
                    .append("，变化像素 >1 级 ")
                    .append(String.format(Locale.US,"%.1f%%",100.0*contextPixels/environmentOff.length))
                    .append("；仅对可信且原本明亮的主体开放\n");

                // v0.8.9: measure coordination and chromatic density INDEPENDENTLY.
                // Same image, same treatment, same model guidance and exposure budget.
                FilmCore.setOrchestratorStrength(stats,0);
                FilmCore.setColorStrength(stats,0);
                int[] v088Only=small.clone();
                FilmCore.candidate(v088Only,ew,eh,stats,stats.selectedTreatment);
                FilmCore.setOrchestratorStrength(stats,1);
                int[] coordinatedOnly=small.clone();
                FilmCore.candidate(coordinatedOnly,ew,eh,stats,stats.selectedTreatment);
                FilmCore.setColorStrength(stats,1);
                double strategyAbs=0,chromaAbs=0;
                int changeStrategy=0,changeChroma=0;
                for(int i=0;i<small.length;i++){
                    int x=v088Only[i],y=coordinatedOnly[i],z=guided[i];
                    int dr=Math.abs(((x>>>16)&255)-((y>>>16)&255));
                    int dg=Math.abs(((x>>>8)&255)-((y>>>8)&255));
                    int db=Math.abs((x&255)-(y&255));
                    strategyAbs+=(dr+dg+db)/3.;if(Math.max(dr,Math.max(dg,db))>1)changeStrategy++;
                    dr=Math.abs(((y>>>16)&255)-((z>>>16)&255));
                    dg=Math.abs(((y>>>8)&255)-((z>>>8)&255));
                    db=Math.abs((y&255)-(z&255));
                    chromaAbs+=(dr+dg+db)/3.;if(Math.max(dr,Math.max(dg,db))>1)changeChroma++;
                }
                detailed.append("v0.8.9 独立消融（同图同方案）\n")
                    .append("  策略协调：RGB 均差 ").append(String.format(Locale.US,"%.3f/255",strategyAbs/small.length))
                    .append("，变化像素 >1 级 ").append(String.format(Locale.US,"%.1f%%",100.0*changeStrategy/small.length)).append('\n')
                    .append("  背景色彩：RGB 均差 ").append(String.format(Locale.US,"%.3f/255",chromaAbs/small.length))
                    .append("，变化像素 >1 级 ").append(String.format(Locale.US,"%.1f%%",100.0*changeChroma/small.length)).append('\n');

                // Separate traditional film-tone safety from ML contribution.
                // The exact same candidate is rendered with only the SDR safety
                // shoulder/toe disabled; user diagnostic remains reproducible.
                int[] unprotectedTone=small.clone();
                FilmCore.setToneSafetyStrength(stats,0);
                FilmCore.candidate(unprotectedTone,ew,eh,stats,stats.selectedTreatment);
                FilmCore.setToneSafetyStrength(stats,1);
                CandidateRanker.Metrics oldToneDamage=new CandidateRanker.Metrics(small,unprotectedTone,ew,eh);
                CandidateRanker.Metrics safeToneDamage=damage[stats.selectedTreatment];
                long tonePixelChanges=0;double toneDelta=0;
                for(int i=0;i<unprotectedTone.length;i++){
                    int a=unprotectedTone[i],b=guided[i];
                    int delta=Math.max(Math.abs(((a>>>16)&255)-((b>>>16)&255)),
                        Math.max(Math.abs(((a>>>8)&255)-((b>>>8)&255)),Math.abs((a&255)-(b&255))));
                    if(delta>1)tonePixelChanges++;
                    toneDelta+=(Math.abs(((a>>>16)&255)-((b>>>16)&255))+
                        Math.abs(((a>>>8)&255)-((b>>>8)&255))+Math.abs((a&255)-(b&255)))/3.0;
                }
                detailed.append("基础影调安全映射：启用（非AI）\n")
                    .append("  相同方案关闭保护时：新增死白 ")
                    .append(String.format(Locale.US,"%.3f%%",100*oldToneDamage.newWhite))
                    .append("，新增死黑 ")
                    .append(String.format(Locale.US,"%.3f%%",100*oldToneDamage.newBlack)).append('\n')
                    .append("  开启保护后：新增死白 ")
                    .append(String.format(Locale.US,"%.3f%%",100*safeToneDamage.newWhite))
                    .append("，新增死黑 ")
                    .append(String.format(Locale.US,"%.3f%%",100*safeToneDamage.newBlack)).append('\n')
                    .append("  受影响像素(通道差>1)：")
                    .append(String.format(Locale.US,"%.2f%%",100.0*tonePixelChanges/unprotectedTone.length))
                    .append("；平均 RGB 差 ")
                    .append(String.format(Locale.US,"%.3f/255",toneDelta/unprotectedTone.length)).append('\n');

                // Explicitly audit *real* Director Cuts, not just legacy NIMA candidates.
                // Cut 0 intentionally falls back if the semantic proposal is untrusted.
                detailed.append("导演剪辑规划：").append(director.summary()).append('\n')
                    .append("导演剪辑评审：手动切换；NIMA 仅选择基础影调，不自动决定导演剪辑。\n");
                for(int directorIndex=0;directorIndex<3;directorIndex++){
                    int[] previous=small.clone();
                    FilmCore.candidate(previous,ew,eh,stats,stats.selectedTreatment);
                    int[] directed=previous.clone();
                    for(int yy=0;yy<eh;yy++)for(int xx=0;xx<ew;xx++){
                        int k=yy*ew+xx;
                        directed[k]=DirectorCuts.pixel(small[k],directed[k],
                            (xx+.5)/(double)ew,(yy+.5)/(double)eh,director,directorIndex,1.0,100);
                    }
                    CandidateRanker.Metrics directorBaseDamage=new CandidateRanker.Metrics(small,previous,ew,eh);
                    CandidateRanker.Metrics directorDamage=new CandidateRanker.Metrics(small,directed,ew,eh);
                    double addedBlack=Math.max(0,directorDamage.newBlack-directorBaseDamage.newBlack);
                    double addedWhite=Math.max(0,directorDamage.newWhite-directorBaseDamage.newWhite);
                    double directorDifference=0;
                    int changed=0;
                    for(int k=0;k<directed.length;k++){
                        int a=previous[k],b=directed[k];
                        int dr=Math.abs(((a>>>16)&255)-((b>>>16)&255));
                        int dg=Math.abs(((a>>>8)&255)-((b>>>8)&255));
                        int db=Math.abs((a&255)-(b&255));
                        directorDifference+=(dr+dg+db)/3.0;
                        if(Math.max(dr,Math.max(dg,db))>3)changed++;
                    }
                    detailed.append("导演 ").append(DirectorCuts.name(directorIndex))
                        .append("：额外RGB均差 ")
                        .append(String.format(Locale.US,"%.3f/255",directorDifference/directed.length))
                        .append("，差异>3的像素 ")
                        .append(String.format(Locale.US,"%.1f%%",100.0*changed/directed.length))
                        .append("；导演额外新增黑/白 ")
                        .append(String.format(Locale.US,"%.3f%% / %.3f%%",100*addedBlack,100*addedWhite))
                        .append("；总新增黑/白 ")
                        .append(String.format(Locale.US,"%.3f%% / %.3f%%",100*directorDamage.newBlack,100*directorDamage.newWhite))
                        .append('\n');
                }
                detailed.append("ONNX Runtime Android：1.30.0；U2NETP：")
                    .append(genericOnnxEnabled()?"单独实验开启":"单独关闭").append('\n');
                detailed.append("U2NETP 候选可视化：")
                    .append(guidance.genericProposal==null?"无候选":"可在③查看；被拒绝的候选不会参与调色")
                    .append('\n');
                detailed.append("主体蒙版：").append(guidance.subject==null?"不可用":"可用")
                    .append("；深度图：").append(guidance.depth==null?"不可用":"可用").append('\n');
                detailed.append("测试说明：NIMA只选择基础影调；导演剪辑由用户手动选择。\n");
                final String diagnosticText=detailed.toString();
                final long analysisElapsed=android.os.SystemClock.elapsedRealtime()-started;
                final int chosen=stats.selectedTreatment;
                final String modelReport=guidance.report();
                markStage("full: render preview");
                Bitmap original=raw;raw=null;
                int[] originalPixels=pixels.clone();
                FilmCore.applyTile(pixels,w,h,0,0,w,h,stats);
                DirectorCuts.apply(originalPixels,pixels,w,h,director,1,1.0,100);
                Bitmap graded=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
                runOnUiThread(()->{
                    if(task!=generation){original.recycle();graded.recycle();return;}
                    Bitmap oldOrig=originalPreview,oldGraded=gradedPreview;
                    originalPreview=original;gradedPreview=graded;
                    selectedSource=uri;scene=stats;baseScene=baseline;guidedScene=stats;latestGuidance=guidance;
                    activeDirectorPlan=director;directorCut=1;
                    autoTreatment=chosen;baseAutoTreatment=baseline.selectedTreatment;manualTreatment=-1;fusionEnabled=true;strengthPercent=100;
                    lastAnalysisMs=analysisElapsed;modelDetails=diagnosticText;previewMode="成片";
                    lastModelContribution=contribution;
                    try{activeRunId=DiagnosticStore.create(this,modelDetails+
                        "分析耗时："+analysisElapsed+" ms\n自动方案："+treatment(chosen)+
                        "\n初始模式：导演剪辑 · 自然光线 / 强度100%\n");}
                    catch(Exception ex){activeRunId=null;Toast.makeText(this,"诊断记录写入失败",Toast.LENGTH_SHORT).show();}
                    releaseDiagnostic();
                    selectedWidth=dims[0];selectedHeight=dims[1];
                    comparison.setImages(original,graded);
                    comparison.setVisibility(View.VISIBLE);spinner.setVisibility(View.GONE);chip.setVisibility(View.VISIBLE);
                    busy=false;pickButton.setEnabled(true);saveButton.setEnabled(true);saveButton.setAlpha(1);
                    labButton.setEnabled(true);maskButton.setEnabled(true);labButton.setAlpha(1);maskButton.setAlpha(1);
                    heading.setText("导演剪辑 · 自然光线");
                    hint.setText("向右滑动看效果 · ②切换三种导演风格 · "+analysisElapsed+"ms");
                    if(oldOrig!=null&&oldOrig!=original)oldOrig.recycle();
                    if(oldGraded!=null&&oldGraded!=graded)oldGraded.recycle();
                    clearStage();
                });
            }catch(Throwable ex){
                if(raw!=null)raw.recycle();
                runOnUiThread(()->{
                    clearStage();
                    try{DiagnosticStore.create(this,"处理失败："+ex.getClass().getSimpleName()+
                        " / "+String.valueOf(ex.getMessage())+"\n");}catch(Exception ignored){}
                    busy=false;spinner.setVisibility(View.GONE);
                    heading.setText("这张照片暂时无法处理");
                    hint.setText("请换张 JPEG 或 HEIF 照片重试（"+ex.getClass().getSimpleName()+"）");
                    if(gradedPreview!=null){comparison.setImages(originalPreview,gradedPreview);
                        comparison.setVisibility(View.VISIBLE);chip.setVisibility(View.VISIBLE);}
                    else placeholder.setVisibility(View.VISIBLE);
                    pickButton.setEnabled(true);labButton.setEnabled(originalPreview!=null);maskButton.setEnabled(originalPreview!=null);saveButton.setEnabled(selectedSource!=null);
                    saveButton.setAlpha(selectedSource!=null?1:.5f);
                });
            }
        },"c65-image-grade").start();
    }
    private String treatment(int k){return k==0?"主体细节":k==1?"空间塑光":"色彩叙事";}
    private void releaseDiagnostic(){if(diagnosticPreview!=null){diagnosticPreview.recycle();diagnosticPreview=null;}}
    private void showResult(){
        if(originalPreview!=null&&gradedPreview!=null){releaseDiagnostic();comparison.setImages(originalPreview,gradedPreview);
            previewMode="成片";
            hint.setText("成片 · "+(directorCut>=0?"导演剪辑 · "+DirectorCuts.name(directorCut):
                (fusionEnabled?"融合模型":"纯算法")+" · "+treatment(scene.selectedTreatment))+" · "+strengthPercent+"%");}
    }
    private void switchTreatment(int mode,boolean useFusion){
        if(busy||originalPreview==null)return;
        manualTreatment=mode;fusionEnabled=useFusion;
        DiagnosticStore.event(this,activeRunId,"方案："+(directorCut>=0?
            "导演 · "+DirectorCuts.name(directorCut):(useFusion?"融合":"纯算法")+
            " · "+(mode==-1?"自动":treatment(mode)))+" · 强度"+strengthPercent+"%");
        scene=useFusion?guidedScene:baseScene;
        if(scene==null)return;
        scene.selectedTreatment=(mode==-1)?(useFusion?autoTreatment:baseAutoTreatment):mode;
        busy=true;pickButton.setEnabled(false);saveButton.setEnabled(false);
        labButton.setEnabled(false);heading.setText("正在渲染实验方案…");
        final FilmCore.Analysis treatmentScene=scene;
        final int level=strengthPercent;
        new Thread(()->{
            try{
                int w=originalPreview.getWidth(),h=originalPreview.getHeight();
                int[] input=new int[w*h];originalPreview.getPixels(input,0,w,0,0,w,h);
                int[] result=input.clone();
                FilmCore.applyTile(result,w,h,0,0,w,h,treatmentScene);
                final int finishingCut=directorCut;
                if(finishingCut>=0 && activeDirectorPlan!=null)
                    DirectorCuts.apply(input,result,w,h,activeDirectorPlan,finishingCut,1.0,strengthPercent);
                FilmCore.blendOutput(input,result,level);
                Bitmap updated=Bitmap.createBitmap(result,w,h,Bitmap.Config.ARGB_8888);
                runOnUiThread(()->{
                    Bitmap previous=gradedPreview;gradedPreview=updated;
                    busy=false;pickButton.setEnabled(true);saveButton.setEnabled(true);labButton.setEnabled(true);
                    heading.setText(directorCut>=0?"导演剪辑 · "+DirectorCuts.name(directorCut):
                        "经典影调 · "+treatment(treatmentScene.selectedTreatment));showResult();
                    if(previous!=null&&previous!=updated)previous.recycle();
                });
            }catch(Throwable error){
                runOnUiThread(()->{busy=false;pickButton.setEnabled(true);saveButton.setEnabled(true);
                    labButton.setEnabled(true);hint.setText("预览生成失败："+error.getClass().getSimpleName());});
            }
        },"c65-lab-preview").start();
    }
    private void switchDirector(int cut){
        if(busy||originalPreview==null||activeDirectorPlan==null)return;
        if(cut<0||cut>2)throw new IllegalArgumentException("Director style 0-2");
        directorCut=cut;
        switchTreatment(-1,true);
    }
    private void displayMask(String kind){
        if(busy||originalPreview==null)return;
        Bitmap m=null;
        if(kind.equals("U2NETP候选")){
            if(latestGuidance==null||latestGuidance.genericProposal==null){
                Toast.makeText(this,"当前没有 U2NETP 候选；请开启完整模式与 ONNX 单项实验",Toast.LENGTH_LONG).show();
                return;
            }
            m=DebugMaps.map(originalPreview,latestGuidance.genericProposal,
                latestGuidance.mapW,latestGuidance.mapH,0);
        }else if(kind.equals("主体")){
            if(latestGuidance==null||latestGuidance.subject==null){Toast.makeText(this,"未生成主体蒙版；请查看模型状态",Toast.LENGTH_LONG).show();return;}
            m=DebugMaps.map(originalPreview,latestGuidance.subject,latestGuidance.mapW,latestGuidance.mapH,0);
        }else if(kind.equals("深度")){
            if(latestGuidance==null||latestGuidance.depth==null){Toast.makeText(this,"深度模型没有有效输出",Toast.LENGTH_LONG).show();return;}
            m=DebugMaps.map(originalPreview,latestGuidance.depth,latestGuidance.mapW,latestGuidance.mapH,1);
        }else if(kind.equals("关注点")){
            float[] a=FilmCore.debugAttention(scene);
            m=DebugMaps.map(originalPreview,a,FilmCore.debugAttentionWidth(scene),FilmCore.debugAttentionHeight(scene),2);
        }else if(kind.equals("融合主体")){
            m=DebugMaps.map(originalPreview,FilmCore.debugSemantic(scene),
                FilmCore.debugAttentionWidth(scene),FilmCore.debugAttentionHeight(scene),0);
        }else if(kind.equals("背景纵深")){
            m=DebugMaps.map(originalPreview,FilmCore.debugDepthFar(scene),
                FilmCore.debugAttentionWidth(scene),FilmCore.debugAttentionHeight(scene),1);
        }else if(kind.equals("主体提亮预算")){
            m=DebugMaps.map(originalPreview,FilmCore.debugSubjectDodgeBudget(scene),
                FilmCore.debugAttentionWidth(scene),FilmCore.debugAttentionHeight(scene),0);
        }else if(kind.equals("背景压暗预算")){
            m=DebugMaps.map(originalPreview,FilmCore.debugBackgroundBurnBudget(scene),
                FilmCore.debugAttentionWidth(scene),FilmCore.debugAttentionHeight(scene),1);
        }else if(kind.equals("环境衬托预算")){
            m=DebugMaps.map(originalPreview,FilmCore.debugEnvironmentBudget(scene),
                FilmCore.debugAttentionWidth(scene),FilmCore.debugAttentionHeight(scene),1);
        }else if(kind.equals("背景色彩预算")){
            m=DebugMaps.map(originalPreview,FilmCore.debugColorBudget(scene),
                FilmCore.debugAttentionWidth(scene),FilmCore.debugAttentionHeight(scene),1);
        }else if(kind.equals("提亮"))m=DebugMaps.delta(originalPreview,gradedPreview,true);
        else if(kind.equals("压暗"))m=DebugMaps.delta(originalPreview,gradedPreview,false);
        if(m==null)return;
        DiagnosticStore.event(this,activeRunId,"查看诊断蒙版："+kind);
        releaseDiagnostic();diagnosticPreview=m;
        comparison.setImages(originalPreview,m);previewMode=kind;
        heading.setText("诊断："+kind);
        hint.setText(kind.equals("U2NETP候选")?
            "仅供人工检查：红色候选区域可能已被质量闸门否决，未被用于调色或导出":
            kind.equals("提亮")?"黄色：真实输出中被提亮的位置":kind.equals("压暗")?"蓝色：真实输出中被压暗的位置":
            kind.equals("关注点")?"橙色：实际参与塑光的注意力图":
            kind.equals("主体提亮预算")||kind.equals("背景压暗预算")||kind.equals("环境衬托预算")||kind.equals("背景色彩预算")?
                "该图表示模型允许调整的区域，不等于最终实际像素变化；对照实际提亮/压暗图":
                "颜色越浓表示模型输出数值越高；不代表真实照明");
    }
    private void addLabButton(LinearLayout panel,String label,Runnable action){
        Button b=makeButton(label,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(43));lp.bottomMargin=dp(7);
        panel.addView(b,lp);b.setOnClickListener(v->action.run());
    }
    private void openLab(){
        if(originalPreview==null){
            new AlertDialog.Builder(this).setTitle("② 导演风格 · 模型设置")
                .setMessage("当前："+(advancedMode()?"完整模型实验（U2NETP 可独立测试）":"稳定模式（推荐）")+
                    "\n\n稳定模式直接运行三种导演剪辑，不加载可能导致退出的原生 AI 模型。")
                .setPositiveButton(advancedMode()?"关闭完整模型":"开启完整模型",(d,w)->toggleAdvanced())
                .setNeutralButton(genericOnnxEnabled()?"关闭 U2NETP":"单独测试 U2NETP",(d,w)->toggleGenericOnnx())
                .setNegativeButton("返回",null).show();
            return;
        }
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18),dp(9),dp(18),dp(12));scroll.addView(panel);
        TextView lead=text("导演剪辑实验 · 默认自然光线",14,INK,true);panel.addView(lead);
        TextView detail=text("当前："+(directorCut>=0?"导演 · "+DirectorCuts.name(directorCut):
            (fusionEnabled?"融合模型":"纯算法"))+" / 基础 "+treatment(scene.selectedTreatment)+
            " / "+strengthPercent+"%\n模型影响："+(Double.isFinite(lastModelContribution)?
            String.format(Locale.US,"平均像素变化 %.2f/255",lastModelContribution):"尚无数据")+
            "\n完整状态及错误请点击首页【诊断记录】",12,MUTED,false);
        detail.setPadding(0,dp(8),0,dp(12));panel.addView(detail);
        TextView options=text("导演剪辑 · 选择即预览并用于保存",13,INK,true);
        options.setPadding(0,dp(8),0,dp(8));panel.addView(options);
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("② 导演剪辑 · 风格对照")
            .setView(scroll).setNegativeButton("关闭",(d,w)->{}).create();
        addLabButton(panel,"🎞 ① 主体与环境 · 强调主体、降低背景竞争",()->{dialog.dismiss();switchDirector(0);});
        addLabButton(panel,"☀ ② 自然光线 · 沿真实受光面加强明暗",()->{dialog.dismiss();switchDirector(1);});
        addLabButton(panel,"◐ ③ 冷暖叙事 · 暖高光与冷阴影",()->{dialog.dismiss();switchDirector(2);});
        TextView second=text("经典影调 · 对照模式",13,INK,true);
        second.setPadding(0,dp(13),0,dp(7));panel.addView(second);
        addLabButton(panel,"原版 · 自动融合（不使用导演剪辑）",()->{dialog.dismiss();directorCut=-1;switchTreatment(-1,true);});
        for(int i=0;i<3;i++){final int which=i;addLabButton(panel,"经典 · "+treatment(i),()->{dialog.dismiss();directorCut=-1;switchTreatment(which,true);});}
        addLabButton(panel,"纯算法 · 关闭所有模型引导和导演剪辑",()->{dialog.dismiss();directorCut=-1;switchTreatment(-1,false);});
        TextView strengthLabel=text("修图强度 "+strengthPercent+"%（50—150%）",13,INK,true);
        strengthLabel.setPadding(0,dp(10),0,0);panel.addView(strengthLabel);
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(strengthPercent-50);panel.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean fromUser){strengthLabel.setText("修图强度 "+(value+50)+"%（50—150%）");}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){int next=s.getProgress()+50;if(next!=strengthPercent){strengthPercent=next;dialog.dismiss();switchTreatment(manualTreatment,fusionEnabled);}}
        });
        TextView mode=text("本次分析："+(advancedMode()?"完整模型实验":"低内存稳定模式")+
            "，ONNX "+(genericOnnxEnabled()?"开启":"关闭")+
            "。更改模型设置后需要重新选图。",12,MUTED,false);
        mode.setPadding(0,dp(8),0,dp(10));panel.addView(mode);
        addLabButton(panel,advancedMode()?"关闭完整模型（推荐稳定）":"开启完整模型实验（U2NETP 默认关闭）",
            ()->{dialog.dismiss();toggleAdvanced();});
        addLabButton(panel,genericOnnxEnabled()?"关闭 U2NETP ONNX 单项测试":"启用 U2NETP ONNX 单项测试",
            ()->{dialog.dismiss();toggleGenericOnnx();});
        addLabButton(panel,"♡ 喜欢当前方案",()->{rememberPreference();});
        addLabButton(panel,"查看完整诊断记录",()->{dialog.dismiss();openRecordHistory();});
        dialog.show();
    }
    private void save(){
        if(busy||selectedSource==null||scene==null)return;
        busy=true;pickButton.setEnabled(false);saveButton.setEnabled(false);
        labButton.setEnabled(false);maskButton.setEnabled(false);
        heading.setText("正在输出原始分辨率");hint.setText("按条带处理，保留原图长宽与像素。请勿退出应用。");
        final Uri source=selectedSource;
        final FilmCore.Analysis params=scene;
        final DirectorCuts.Plan directorPlan=activeDirectorPlan;
        final int exportCut=directorCut;
        final int exportStrength=strengthPercent;
        new Thread(()->{
            Uri destination=null;
            Bitmap full=null;
            try{
                ImageDecoder.Source decoderSource=ImageDecoder.createSource(getContentResolver(),source);
                final int[] dimensions=new int[2];
                full=ImageDecoder.decodeBitmap(decoderSource,(decoder,info,src)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                    dimensions[0]=w;dimensions[1]=h;
                    // Never silently reduce dimensions. Reject a risky mutable decode
                    // BEFORE the decoder asks Android for the full-size allocation.
                    ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
                    ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);
                    Runtime runtime=Runtime.getRuntime();
                    long freeHeap=Math.max(0,runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory()));
                    String memoryRisk=ExportMemoryPolicy.rejection(w,h,freeHeap,mi.availMem,mi.lowMemory);
                    if(memoryRisk!=null)throw new IllegalStateException(memoryRisk);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMutableRequired(true);
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                });
                final int fw=full.getWidth(),fh=full.getHeight();
                final int stripeRows=Math.max(1,Math.min(96,1_200_000/fw));
                final int[] strip=new int[fw*stripeRows];
                for(int y=0;y<fh;y+=stripeRows){
                    int rows=Math.min(stripeRows,fh-y);
                    // Write only the active rows into a reusable temporary pixel buffer.
                    full.getPixels(strip,0,fw,0,y,fw,rows);
                    int[] ungraded=java.util.Arrays.copyOf(strip,fw*rows);
                    if(rows==stripeRows){
                        FilmCore.applyTile(strip,fw,rows,0,y,fw,fh,params);
                    } else {
                        // Last stripe can be shorter than reusable buffer: exact contiguous segment.
                        int[] remainder=new int[fw*rows];
                        System.arraycopy(strip,0,remainder,0,remainder.length);
                        FilmCore.applyTile(remainder,fw,rows,0,y,fw,fh,params);
                        System.arraycopy(remainder,0,strip,0,remainder.length);
                    }
                    // The finishing layer reads ORIGINAL pixel values and absolute
                    // normalized coordinates. Identical path for preview/full-size export.
                    if(exportCut>=0&&directorPlan!=null){
                        for(int ry=0;ry<rows;ry++)for(int x=0;x<fw;x++){
                            int k=ry*fw+x;
                            strip[k]=DirectorCuts.pixel(ungraded[k],strip[k],
                                (x+.5)/(double)fw,(y+ry+.5)/(double)fh,
                                directorPlan,exportCut,1.0,exportStrength);
                        }
                    }
                    if(exportStrength!=100){
                        FilmCore.blendOutputPrefix(ungraded,strip,fw*rows,exportStrength);
                    }
                    full.setPixels(strip,0,fw,0,y,fw,rows);
                }
                String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,"CINEMA65_"+stamp+".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/CINEMA65");
                values.put(MediaStore.Images.Media.IS_PENDING,1);
                destination=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(destination==null)throw new IOException("MediaStore unavailable");
                try(OutputStream out=getContentResolver().openOutputStream(destination)){
                    if(out==null||!full.compress(Bitmap.CompressFormat.JPEG,96,out))
                        throw new IOException("JPEG encoder failed");
                }
                ContentValues visible=new ContentValues();visible.put(MediaStore.Images.Media.IS_PENDING,0);
                getContentResolver().update(destination,visible,null,null);
                final String size=fw+" × "+fh;
                runOnUiThread(()->{
                    busy=false;pickButton.setEnabled(true);saveButton.setEnabled(true);
                    labButton.setEnabled(true);maskButton.setEnabled(true);
                    DiagnosticStore.event(this,activeRunId,"保存成功："+size+" / "+
                        (exportCut>=0?"导演剪辑·"+DirectorCuts.name(exportCut):"经典·"+treatment(params.selectedTreatment))+
                        " / 强度"+exportStrength+"%");
                    heading.setText("已保存原尺寸照片");hint.setText(size+" · 图片 / CINEMA65 · 原图保留");
                    Toast.makeText(this,"已保存 "+size,Toast.LENGTH_SHORT).show();
                });
            }catch(Throwable e){
                if(destination!=null)try{getContentResolver().delete(destination,null,null);}catch(Exception ignored){}
                final String message=(e instanceof OutOfMemoryError||
                    (e.getMessage()!=null&&e.getMessage().contains("memory")))
                    ?"内存不足，无法安全输出原尺寸。请关闭后台应用后重试；未降采样。"
                    :"原尺寸输出失败（"+e.getClass().getSimpleName()+"），未修改原片。";
                runOnUiThread(()->{
                    busy=false;pickButton.setEnabled(true);saveButton.setEnabled(true);
                    labButton.setEnabled(true);maskButton.setEnabled(true);
                    DiagnosticStore.event(this,activeRunId,"保存失败："+message);
                    heading.setText("未能完成导出");hint.setText(message);
                });
            }finally{
                if(full!=null)full.recycle();
            }
        },"c65-original-size-export").start();
    }
}
