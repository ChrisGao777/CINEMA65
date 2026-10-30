import com.cinema65.oneclick.GenericSubjectGate;

/** Rejected-but-connected masks must remain rejected until the user visually
 *  validates them. This is a test of the current gate, not its accuracy. */
public final class GenericProposalAuditRegressionTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static float[] proposal(int w,int h,int x0,int y0,int x1,int y1){
        float[] a=new float[w*h];
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)a[y*w+x]=1.0f;
        return a;
    }
    public static void main(String[] args){
        int w=100,h=100;
        GenericSubjectGate.Review small=GenericSubjectGate.assess(proposal(w,h,20,20,70,31),w,h);
        require(small.coverage>.05 && small.coverage<.06,"5.5% test mask geometry");
        require(small.connectedShare>.99,"small mask is coherent, not fragmented");
        require(!small.usable,"small masks must remain diagnostic-only");
        require(small.reason.contains("6.0%"),"clear threshold-based rejection reason");
        GenericSubjectGate.Review acceptable=GenericSubjectGate.assess(proposal(w,h,20,20,70,33),w,h);
        require(acceptable.usable,"6.5% coherent bounded mask should pass existing research gate");
        require(acceptable.borderOccupancy==0,"expected bounded mask");
        System.out.println("Generic proposal audit PASS: visual-only small proposal and unchanged 6% confidence gate");
    }
}
