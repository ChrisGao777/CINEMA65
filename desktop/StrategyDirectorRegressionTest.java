import com.cinema65.oneclick.*;
import java.util.Arrays;
/** Contract: independent scene policy, reproducible zero-strength ablation and tile parity. */
public final class StrategyDirectorRegressionTest {
 static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] argv){
  int w=240,h=170;int[] src=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   boolean object=x>78&&x<159&&y>27&&y<145;
   int v=object?177:85+((x+y)%9);
   src[y*w+x]=0xff000000|((v+8)<<16)|(v<<8)|(Math.max(0,v-8));
  }
  float[] sem=new float[48*48],depth=new float[48*48];
  for(int y=0;y<48;y++)for(int x=0;x<48;x++){
   sem[y*48+x]=(x>15&&x<32&&y>8&&y<40)?1f:0f;
   depth[y*48+x]=x>24?.2f:.8f;
  }
  FilmCore.Analysis guided=FilmCore.analyze(src,w,h);FilmCore.applyGuidance(guided,sem,48,48,depth);
  yes(guided.hasModelGuidance(),"model evidence required");
  yes(guided.strategySummary().contains("responsive"),"scene policy active");
  for(int style=0;style<3;style++){
   FilmCore.setOrchestratorStrength(guided,0);FilmCore.setColorStrength(guided,0);
   int[] old=src.clone();FilmCore.candidate(old,w,h,guided,style);
   FilmCore.setOrchestratorStrength(guided,1);FilmCore.setColorStrength(guided,0);
   int[] directed=src.clone();FilmCore.candidate(directed,w,h,guided,style);
   FilmCore.setColorStrength(guided,1);
   int[] newVersion=src.clone();FilmCore.candidate(newVersion,w,h,guided,style);
   int[] stripe=src.clone();guided.selectedTreatment=style;
   for(int top=0;top<h;top+=31){int chunk=Math.min(31,h-top);
    int[] tile=Arrays.copyOfRange(stripe,top*w,(top+chunk)*w);
    FilmCore.applyTile(tile,w,chunk,0,top,w,h,guided);
    System.arraycopy(tile,0,stripe,top*w,tile.length);
   }
   yes(Arrays.equals(stripe,newVersion),"stripe parity "+style);
   int changed=0;for(int i=0;i<old.length;i++)if(old[i]!=newVersion[i])changed++;
   yes(changed>0,"model-guided strategy must have nonzero effect "+style);
  }
  FilmCore.Analysis none=FilmCore.analyze(src,w,h);
  FilmCore.applyGuidance(none,null,48,48,null);
  int[] a=src.clone(),b=src.clone();FilmCore.candidate(a,w,h,none,2);
  FilmCore.setColorStrength(none,0);FilmCore.setOrchestratorStrength(none,0);
  FilmCore.candidate(b,w,h,none,2);
  yes(Arrays.equals(a,b),"no model must preserve baseline");
  System.out.println("PASS strategy coordinator: scene allocation; ablation; original-preserving fallback; tile parity");
 }
}