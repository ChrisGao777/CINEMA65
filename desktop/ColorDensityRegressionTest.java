import com.cinema65.oneclick.*;
import java.util.Arrays;
/** Isolated chromatic competition, no subject/sky bleaching or exposure command. */
public final class ColorDensityRegressionTest {
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 static double chroma(int rgb){int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;return Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));}
 public static void main(String[] args){
  int w=240,h=176;int[] original=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   boolean object=x>74&&x<157&&y>32&&y<145;
   int v=object?184:105;
   original[y*w+x]=object?0xffbba87b:((y<15)?0xff79bddd:0xffa15da0);
  }
  float[] sem=new float[48*48];
  for(int y=0;y<48;y++)for(int x=0;x<48;x++)sem[y*48+x]=(x>15&&x<32&&y>9&&y<40)?1f:0f;
  FilmCore.Analysis a=FilmCore.analyze(original,w,h);FilmCore.applyGuidance(a,sem,48,48,null);
  check(a.colorSummary().contains("color-competition"),"color plan exists");
  FilmCore.setColorStrength(a,0);int[] off=original.clone();FilmCore.candidate(off,w,h,a,2);
  FilmCore.setColorStrength(a,1);int[] on=original.clone();FilmCore.candidate(on,w,h,a,2);
  double chromaShift=0;int count=0,subjectDif=0;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int k=y*w+x;
   if(x<71||x>158){if(y>30&&y<145){chromaShift+=chroma(on[k])-chroma(off[k]);count++;}}
   else if(x>95&&x<135&&y>60&&y<120 && on[k]!=off[k])subjectDif++;
  }
  check(chromaShift/count<0,"background chroma should reduce");
  check(subjectDif==0,"interior object may not be recolored by background muting");
  int sky=0;for(int y=0;y<12;y++)for(int x=0;x<w;x++)if(on[y*w+x]!=off[y*w+x])sky++;
  check(sky<20,"blue sky guard");
  FilmCore.Analysis noMask=FilmCore.analyze(original,w,h);FilmCore.applyGuidance(noMask,null,48,48,null);
  int[] masked=original.clone(),unmasked=original.clone();FilmCore.setColorStrength(noMask,1);FilmCore.candidate(masked,w,h,noMask,2);FilmCore.setColorStrength(noMask,0);FilmCore.candidate(unmasked,w,h,noMask,2);
  check(Arrays.equals(masked,unmasked),"no subject => no chroma edit");
  System.out.println("PASS local color budget; subject & sky protection; invalid-object exact fallback; background chroma delta="+(chromaShift/count));
 }
}