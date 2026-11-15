package org.retrolink.legacy;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import java.io.File;
import java.io.IOException;

/** One local voice draft. A failed recording preserves the previous draft. */
final class VoiceDraft {
 private final File saved;
 private final File temporary;
 private MediaRecorder recorder;
 private MediaPlayer player;

 VoiceDraft(Context context) {
  saved = new File(context.getFilesDir(), "voice-draft.3gp");
  temporary = new File(context.getFilesDir(), "voice-recording.3gp");
 }

 byte[] read() throws IOException {
  long length=saved.length();
  if(length<=0||length>65536)throw new IOException("录音大小超出限制");
  byte[] bytes=new byte[(int)length];
  java.io.DataInputStream in=new java.io.DataInputStream(new java.io.FileInputStream(saved));
  try{in.readFully(bytes);}finally{in.close();}
  return bytes;
 }
 boolean hasAudio() { return saved.isFile(); }
 boolean recording() { return recorder != null; }

 void start() throws IOException {
  stopPlayback();
  MediaRecorder next=new MediaRecorder();
  try {
   next.setAudioSource(MediaRecorder.AudioSource.MIC);
   next.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
   next.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
   next.setOutputFile(temporary.getPath());
   next.prepare();
   next.start();
   recorder=next;
  } catch (IOException e) {
   next.release();
   temporary.delete();
   throw e;
  } catch (RuntimeException e) {
   next.release();
   temporary.delete();
   throw e;
  }
 }

 boolean finish() {
  MediaRecorder current=recorder;
  if(current==null)return false;
  recorder=null;
  try {
   current.stop();
  } catch(RuntimeException e) {
   temporary.delete();
   return false;
  } finally {
   current.release();
  }
  return temporary.renameTo(saved);
 }

 void play() throws IOException {
  stopPlayback();
  MediaPlayer next=new MediaPlayer();
  player=next;
  try {
   next.setDataSource(saved.getPath());
   next.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
    public void onCompletion(MediaPlayer finished){if(player==finished)stopPlayback();}
   });
   next.prepare();
   next.start();
  } catch(IOException e){stopPlayback();throw e;}
  catch(RuntimeException e){stopPlayback();throw e;}
 }

 void stopPlayback(){
  if(player!=null){player.release();player=null;}
 }

 void delete(){stopPlayback();saved.delete();}
}
