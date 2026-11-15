package org.retrolink.legacy;
import android.content.Context;
import android.util.DisplayMetrics;
/** One landscape design grid, independent of transient portrait rotation metrics. */
final class UiMetrics {
 private static final java.util.WeakHashMap<android.widget.TextView,Float> specifications=new java.util.WeakHashMap<android.widget.TextView,Float>();
 static float nominal(android.widget.TextView t){Float n=specifications.get(t);return n==null?t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity:n;}
 static int px(Context c,float n){DisplayMetrics m=c.getResources().getDisplayMetrics();return Math.round(n*Math.max(m.widthPixels,m.heightPixels)/854f);}
 static int unit(Context c,float n){return px(c,n*1.5f);}
 static void text(android.widget.TextView t,float size){specifications.put(t,size);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,unit(t.getContext(),size));t.setIncludeFontPadding(false);}
}
