package org.retrolink.legacy;
import android.content.Context;
import android.graphics.Typeface;
/** Cached static fonts; no repeated parsing on redraw. */
final class UiFonts {
 private static Typeface body,dots;
 static Typeface body(Context c){if(body==null)try{body=Typeface.createFromAsset(c.getAssets(),"retro-sans.ttf");}catch(Exception e){body=Typeface.DEFAULT;}return body;}
 static Typeface dots(Context c){if(dots==null)try{dots=Typeface.createFromAsset(c.getAssets(),"retro-dots.ttf");}catch(Exception e){dots=Typeface.MONOSPACE;}return dots;}
}
