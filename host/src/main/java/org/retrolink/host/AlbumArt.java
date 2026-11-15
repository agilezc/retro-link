package org.retrolink.host;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;

/** Bound image dimensions and frame size before sending to an Android 2.3 phone. */
final class AlbumArt {
 static final int MAX_SIDE=160, MAX_BYTES=12000;
 static byte[] encode(Bitmap source) {
  if(source==null || source.isRecycled() || source.getWidth()<1 || source.getHeight()<1)return null;
  int width=source.getWidth(), height=source.getHeight();
  float scale=Math.min(1f, MAX_SIDE/(float)Math.max(width,height));
  Bitmap small=null;
  try {
   small=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)),true);
   for(int quality=75;quality>=30;quality-=15){
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    if(small.compress(Bitmap.CompressFormat.JPEG,quality,out) && out.size()<=MAX_BYTES)return out.toByteArray();
   }
  }catch(RuntimeException e){return null;}catch(OutOfMemoryError e){return null;}
  finally{if(small!=null && small!=source)small.recycle();}
  return null;
 }
}
