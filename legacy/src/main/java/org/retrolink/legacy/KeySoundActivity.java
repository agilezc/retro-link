package org.retrolink.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class KeySoundActivity extends Activity {
 @Override protected void onCreate(Bundle state) {
  super.onCreate(state);
  KeySound.acquire(this);
 }

 @Override protected void onDestroy() {
  KeySound.release();
  super.onDestroy();
 }

 @Override public boolean dispatchTouchEvent(MotionEvent event) {
  if (event.getActionMasked() == MotionEvent.ACTION_UP) KeySound.play();
  return super.dispatchTouchEvent(event);
 }

 @Override public boolean dispatchKeyEvent(KeyEvent event) {
  KeySound.onKey(event);
  return super.dispatchKeyEvent(event);
 }
}
