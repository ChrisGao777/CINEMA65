package com.cinema65.oneclick;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** Durable per-image text diagnostics; never stores a photo, content Uri, or geographic metadata. */
public final class DiagnosticStore {
    private DiagnosticStore() {}
    private static File folder(Context ctx){
        File path=new File(ctx.getFilesDir(),"lab_reports");
        if(!path.exists()&&!path.mkdirs())throw new IllegalStateException("Cannot make diagnostic folder");
        return path;
    }
    public static String create(Context ctx, String report){
        String runId=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());
        File file=new File(folder(ctx),"C65_"+runId+".txt");
        String meta="实验编号："+runId+"\n时间："+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date())+
            "\n设备："+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+"\n";
        try(FileOutputStream stream=new FileOutputStream(file)){
            stream.write((meta+report+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){throw new IllegalStateException("Failed to persist lab record",e);}
        File[] files=all(ctx);
        for(int i=50;i<files.length;i++)files[i].delete();
        return runId;
    }
    public static void event(Context ctx,String runId,String event){
        if(runId==null)return;
        File f=new File(folder(ctx),"C65_"+runId+".txt");
        if(!f.exists())return;
        String line="\n["+new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"] "+event+"\n";
        try(FileOutputStream stream=new FileOutputStream(f,true)){
            stream.write(line.getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }
    public static File[] all(Context ctx){
        File[] files=folder(ctx).listFiles((dir,name)->name.startsWith("C65_")&&name.endsWith(".txt"));
        if(files==null)return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        return files;
    }
    public static String read(File file){
        try(FileInputStream stream=new FileInputStream(file)){
            byte[] bytes=new byte[(int)Math.min(file.length(),200_000)];
            int n=stream.read(bytes);
            return n<0?"":new String(bytes,0,n,StandardCharsets.UTF_8);
        }catch(Exception ex){return "读取记录失败："+ex.getClass().getSimpleName();}
    }
    public static String combined(Context ctx){
        StringBuilder out=new StringBuilder("CINEMA 65 · 诊断记录档案（最近50次）\n\n");
        for(File f:all(ctx))out.append("══════════════════════════\n").append(read(f)).append("\n");
        return out.toString();
    }
}