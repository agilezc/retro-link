package org.retrolink.host;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;
/** Explicit user tap opens WeChat; no automatic foreground launch on unlock. */
public class NoteRelayActivity extends Activity {
 private boolean dispatched,preparing,resumed;
 protected void onCreate(Bundle b){super.onCreate(b);TextView label=new TextView(this);label.setText("转交微信…");label.setTextColor(0xff20201e);label.setBackgroundColor(0xffeeeee8);label.setGravity(android.view.Gravity.CENTER);setContentView(label);dispatched=b!=null&&b.getBoolean("dispatched",false);}
 protected void onResume(){super.onResume();resumed=true;if(dispatched){finish();return;}KeyguardManager lock=getSystemService(KeyguardManager.class);if(lock!=null&&lock.isKeyguardLocked())return;new Handler().post(()->dispatch());}
 @Override protected void onPause(){resumed=false;super.onPause();}
 private void dispatch(){
  if(dispatched||preparing||!resumed||isFinishing()||isDestroyed())return;
  NoteStore store=new NoteStore(this);
  NoteStore.VoiceNote voice=store.nextVoice(true);
  NoteStore.Batch batch=voice==null?store.batch(true):null;
  if(voice==null && batch.ids.isEmpty()){
   voice=store.nextVoice(false);
   if(voice==null)batch=store.batch(false);
  }
  if(voice==null && batch.ids.isEmpty()){
   Toast.makeText(this,"没有待转交速记",Toast.LENGTH_SHORT).show();
   finish();
   return;
  }
  if(voice!=null){
   NoteStore.VoiceNote recording=voice;
   preparing=true;
   new Thread(()->{
    try{
     NoteAudioTranscoder.prepare(getApplicationContext(),recording.id);
     runOnUiThread(()->{
      preparing=false;
      if(resumed&&!isFinishing()&&!isDestroyed())share(store,recording,null);
     });
    }catch(java.io.IOException|RuntimeException error){
     android.util.Log.w("RetroLink","Could not prepare M4A attachment",error);
     runOnUiThread(()->{
      preparing=false;
      if(isFinishing()||isDestroyed())return;
      Toast.makeText(this,"录音转换失败，原件已保留",Toast.LENGTH_LONG).show();
      NoteDelivery.update(this);
      finish();
     });
    }
   },"wechat-audio").start();
   return;
  }
  share(store,null,batch);
 }
 private void share(NoteStore store,NoteStore.VoiceNote voice,NoteStore.Batch batch){
  Intent share=new Intent(Intent.ACTION_SEND)
      .setComponent(new ComponentName("com.tencent.mm","com.tencent.mm.ui.tools.AddFavoriteUI"));
  if(voice==null){
   share.setType("text/plain").putExtra(Intent.EXTRA_TEXT,batch.text);
  }else{
   android.net.Uri uri=NoteAudioProvider.uri(this,voice);
   share.setType(NoteAudioProvider.MIME).putExtra(Intent.EXTRA_STREAM,uri)
       .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   share.setClipData(ClipData.newRawUri(voice.filename(),uri));
  }
  try{
   startActivity(share);
   dispatched=true;
   if(voice==null)store.presented(batch);else store.presentedVoice(voice.id);
   Toast.makeText(this,"已转交微信，请确认收藏",Toast.LENGTH_SHORT).show();
  }catch(ActivityNotFoundException|SecurityException error){
   Toast.makeText(this,"无法打开微信，速记已保留",Toast.LENGTH_LONG).show();
  }
  NoteDelivery.update(this);
  finish();
 }
 protected void onSaveInstanceState(Bundle b){b.putBoolean("dispatched",dispatched);super.onSaveInstanceState(b);}
}
