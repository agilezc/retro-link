package org.retrolink.host;
import android.app.*;import android.content.*;import android.database.sqlite.*;import android.os.*;
/** Uses its own disposable DB; never touches the user's notes or WeChat. */
public class NoteQueueChecks extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 private void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public void onStart(){Context c=getTargetContext();String name="note-queue-check.db";Bundle result=new Bundle();try{c.deleteDatabase(name);SQLiteDatabase old=c.openOrCreateDatabase(name,0,null);old.execSQL("CREATE TABLE notes(id TEXT PRIMARY KEY,body TEXT NOT NULL,created INTEGER NOT NULL)");old.execSQL("INSERT INTO notes VALUES('old','历史测试',1)");old.setVersion(1);old.close();
  NoteStore store=new NoteStore(c,name);check(store.count(false)==0,"old notes must not auto queue");check(store.list().contains("历史测试"),"old body preserved");check(store.save("old","历史测试").startsWith("已接收"),"explicit resend old note");check(store.count(true)==1,"queued old");
  check(store.save("old","历史测试").startsWith("已接收"),"retry accepted");check(store.count(true)==1,"retry deduplicated");check(store.save("old","冲突").contains("冲突"),"conflict rejected");check(store.count(true)==1,"conflict not inserted");
  for(int i=0;i<12;i++)store.save("new-"+i,"测试文字"+i);NoteStore.Batch batch=store.batch();check(batch.ids.size()==10,"batch limit");check(batch.text.contains("历史测试"),"oldest first preserved");store.presented(batch);check(store.count(true)==3,"only chosen ids updated");check(store.count(false)==13,"handoff retains originals");check(store.save("old","历史测试").startsWith("已转交"),"no false WeChat saved result");check(store.count(true)==3,"presented retry not auto duplicated");store.presented(store.batch());check(store.count(true)==0,"drained pending");check(store.batch().ids.size()==10,"retained batch can be reopened");
  SQLiteDatabase voiceSchema=c.openOrCreateDatabase(name,0,null);
  voiceSchema.execSQL("DROP TABLE voice_notes");
  voiceSchema.execSQL("CREATE TABLE voice_notes(id TEXT PRIMARY KEY,body TEXT NOT NULL,audio BLOB NOT NULL,created INTEGER NOT NULL)");
  voiceSchema.execSQL("INSERT INTO voice_notes VALUES('old-voice','',X'000000006674797000000000',1)");
  voiceSchema.setVersion(3);voiceSchema.close();
  String audio=android.util.Base64.encodeToString(new byte[]{0,0,0,0,'f','t','y','p',0,0,0,0},android.util.Base64.NO_WRAP);
  check(store.count(true)==0,"old recordings are not automatically queued");
  check(store.saveVoice("new-voice","测试文字",audio).startsWith("已接收"),"new recording accepted after migration");
  check(store.count(true)==2,"mixed recording and body queued after migration");
  check(store.batch(true).text.endsWith("测试文字"),"mixed body available in the text handoff");
  check(store.nextVoice(true).id.equals("new-voice"),"new recording ready for handoff");
  check(NoteAudioProvider.uri(c,store.nextVoice(true)).getLastPathSegment().endsWith(" 语音笔记.m4a"),"readable attachment URI");
  store.presentedVoice("new-voice");
  check(store.count(true)==1,"body stays queued after voice handoff");
  store.presented(store.batch(true));
  check(store.saveVoice("new-voice","测试文字",audio).startsWith("已转交"),"repeated voice dispatch is deduplicated");
  check(store.count(true)==0,"no repeat notification after dispatch");
  check(store.saveVoice("old-voice","",audio).startsWith("已接收"),"local recording can be requeued by resending");
  check(store.count(true)==1,"resent local recording queued");
  java.io.ByteArrayOutputStream source=new java.io.ByteArrayOutputStream();
  try(java.io.InputStream fixture=c.getResources().openRawResource(R.raw.note_amr_fixture)){
   byte[] buffer=new byte[1024];int size;
   while((size=fixture.read(buffer))!=-1)source.write(buffer,0,size);
  }
  java.io.File encoded=java.io.File.createTempFile("m4a-check-", ".m4a", c.getCacheDir());
  android.media.MediaExtractor extractor=new android.media.MediaExtractor();
  try{
   NoteAudioTranscoder.transcode(source.toByteArray(),encoded);
   extractor.setDataSource(encoded.getPath());
   check(extractor.getTrackCount()==1,"one track in M4A");
   check("audio/mp4a-latm".equals(extractor.getTrackFormat(0).getString(android.media.MediaFormat.KEY_MIME)),
       "AAC track in M4A");
   extractor.selectTrack(0);
   check(extractor.readSampleData(java.nio.ByteBuffer.allocate(4096),0)>0,"M4A contains audio samples");
  }finally{extractor.release();encoded.delete();}
  result.putString("result","PASS: migration, preserved history, explicit queue, duplicate/conflict, batch bound, partial handoff, retained originals, truthful receipts, reopen, 3GP AMR to M4A AAC");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("result","FAIL: "+e);finish(Activity.RESULT_CANCELED,result);}finally{c.deleteDatabase(name);}}
}
