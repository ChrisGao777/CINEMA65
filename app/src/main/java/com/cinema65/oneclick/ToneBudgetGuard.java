package com.cinema65.oneclick;
public final class ToneBudgetGuard {
 private ToneBudgetGuard(){}
 public static double strength(double meanDrop,double newDarkFraction){
  double a=meanDrop>24?24/meanDrop:1;
  double b=newDarkFraction>.12?.12/newDarkFraction:1;
  return Math.max(0,Math.min(1,Math.min(a,b)));
 }
}
