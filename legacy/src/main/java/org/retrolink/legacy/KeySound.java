package org.retrolink.legacy;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.KeyEvent;

/** One preloaded cue shared by the companion's foreground screens. */
final class KeySound {
 private static SoundPool pool;
 private static AudioManager audio;
 private static int sample;
 private static int owners;
 private static boolean ready;

 static void acquire(Context context) {
  if (owners++ != 0) return;
  Context app = context.getApplicationContext();
  audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
  pool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
  pool.setOnLoadCompleteListener((source, id, status) -> {
   if (source == pool && id == sample) ready = status == 0;
  });
  sample = pool.load(app, R.raw.retro_key_click, 1);
 }

 static void release() {
  if (--owners != 0) return;
  ready = false;
  pool.release();
  pool = null;
  audio = null;
 }

 static void onKey(KeyEvent event) {
  if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) play();
 }

 static void play() {
  if (ready && audio != null && audio.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
   pool.play(sample, .0225f, .0225f, 1, 0, 1f);
  }
 }

 private KeySound() {}
}
