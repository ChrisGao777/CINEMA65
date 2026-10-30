package com.cinema65.oneclick;

/** Deterministic, measured SDR guard for research A/B exports. */
public final class ToneBudgetGuard {
 private ToneBudgetGuard(){}
 public static final class Result {
  public final double strength, meanDrop, addedDark;
  Result(double s,double d,double a){strength=s;meanDrop=d;addedDark=a;}
 }
 private static double y(int c){return .2126*((c>>>16)&255)+.7152*((c>>>8)&255)+.0722*(c&255);}
 private static int mix(int a,int b,double t){
  int r=(int)Math.round(((a>>>16)&255)*(1-t)+((b>>>16)&255)*t);
  int g=(int)Math.round(((a>>>8)&255)*(1-t)+((b>>>8)&255)*t);
  int bl=(int)Math.round((a&255)*(1-t)+(b&255)*t);
  return 0xff000000|(r<<16)|(g<<8)|bl;
 }
 private static double[] measure(int[] a,int[] b,double t){
  double drop=0,dark=0;
  for(int i=0;i<a.length;i++){
   double before=y(a[i]),after=y(mix(a[i],b[i],t));
   drop+=before-after;
   if(before>=25&&after<25)dark++;
   else if(before<25&&after>=25)dark--;
  }
  return new double[]{Math.max(0,drop/a.length),Math.max(0,dark/a.length)};
 }
 public static Result apply(int[] original,int[] edited){
  if(original==null||edited==null||original.length!=edited.length||original.length==0)
   throw new IllegalArgumentException("Mismatched pixel buffers");
  double lo=0,hi=1;
  for(int j=0;j<16;j++){
   double mid=(lo+hi)/2;
   double[] m=measure(original,edited,mid);
   if(m[0]<=24&&m[1]<=.12)lo=mid;else hi=mid;
  }
  for(int i=0;i<edited.length;i++)edited[i]=mix(original[i],edited[i],lo);
  double[] m=measure(original,edited,1);
  return new Result(lo,m[0],m[1]);
 }
}
