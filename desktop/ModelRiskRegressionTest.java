import com.cinema65.oneclick.ModelRiskAuditor;
public class ModelRiskRegressionTest {
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 public static void main(String[] args){
  check(ModelRiskAuditor.withinLimits(.0010,.0012,.00001,.00002),"safe change incorrectly vetoed");
  check(!ModelRiskAuditor.withinLimits(.002,.001,.00001,.00002),"extra shadow crush not vetoed");
  check(!ModelRiskAuditor.withinLimits(.001,.003,.00001,.00002),"extra clipping not vetoed");
  check(!ModelRiskAuditor.withinLimits(.001,.001,.0008,.00002),"invented hard seam not vetoed");
  check(!ModelRiskAuditor.withinLimits(.001,.001,.0001,.0003),"edge contrast reversal not vetoed");
  check(!ModelRiskAuditor.withinLimits(Double.NaN,0,0,0),"invalid metric should fail closed");
  System.out.println("PASS damage criteria: clipping, black crush, invented seams and reversals vetoed");
 }
}