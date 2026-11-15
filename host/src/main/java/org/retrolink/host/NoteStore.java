package org.retrolink.host;
import android.content.*;import android.database.Cursor;import android.database.sqlite.*;import java.util.*;
/** Durable local copy; opening WeChat is deliberately not a saved-to-WeChat receipt. */
final class NoteStore extends SQLiteOpenHelper {
 NoteStore(Context c){this(c,"notes.db");}NoteStore(Context c,String name){super(c,name,null,4);}
 private void createVoice(SQLiteDatabase db){db.execSQL("CREATE TABLE voice_notes(id TEXT PRIMARY KEY,body TEXT NOT NULL,audio BLOB NOT NULL,created INTEGER NOT NULL,relay TEXT NOT NULL DEFAULT 'pending')");}
 public void onCreate(SQLiteDatabase db){createVoice(db);db.execSQL("CREATE TABLE notes(id TEXT PRIMARY KEY,body TEXT NOT NULL,created INTEGER NOT NULL,relay TEXT NOT NULL DEFAULT 'pending')");}
 public void onUpgrade(SQLiteDatabase db,int old,int next){if(old<2)db.execSQL("ALTER TABLE notes ADD COLUMN relay TEXT NOT NULL DEFAULT 'local'");if(old<3)createVoice(db);else if(old<4)db.execSQL("ALTER TABLE voice_notes ADD COLUMN relay TEXT NOT NULL DEFAULT 'local'");}
 byte[] latestVoice(){
  try(Cursor cursor=getReadableDatabase().rawQuery("SELECT audio FROM voice_notes ORDER BY created DESC,rowid DESC LIMIT 1",null)){
   return cursor.moveToFirst()?cursor.getBlob(0):null;
  }finally{close();}
 }
 synchronized String saveVoice(String id,String body,String encoded){
  if(!id.matches("[a-zA-Z0-9-]{1,100}")||body.length()>2000||encoded.length()>87400)
   return "笔记格式无效，草稿已保留";
  try{
   byte[] audio=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);
   if(audio.length<12||audio.length>65536||audio[4]!='f'||audio[5]!='t'||audio[6]!='y'||audio[7]!='p')
    return "录音格式无效，草稿已保留";
   long created=System.currentTimeMillis();
   ContentValues values=new ContentValues();
   values.put("id",id);values.put("body",body);values.put("audio",audio);
   values.put("created",created);values.put("relay","pending");
   SQLiteDatabase db=getWritableDatabase();
   db.beginTransaction();
   try{
    boolean audioPresented=false;
    if(db.insertWithOnConflict("voice_notes",null,values,SQLiteDatabase.CONFLICT_IGNORE)<0){
     try(Cursor cursor=db.query("voice_notes",new String[]{"body","audio","relay","created"},"id=?",
         new String[]{id},null,null,null)){
      if(!cursor.moveToFirst()||!body.equals(cursor.getString(0))||!Arrays.equals(audio,cursor.getBlob(1)))
       return "笔记编号冲突，草稿已保留";
      audioPresented="presented".equals(cursor.getString(2));
      created=cursor.getLong(3);
     }
     db.execSQL("UPDATE voice_notes SET relay='pending' WHERE id=? AND relay='local'",new Object[]{id});
    }
    boolean textPending=false;
    if(!body.trim().isEmpty()){
     ContentValues text=new ContentValues();
     text.put("id",id);text.put("body",body);text.put("created",created);text.put("relay","pending");
     if(db.insertWithOnConflict("notes",null,text,SQLiteDatabase.CONFLICT_IGNORE)>=0){
      textPending=true;
     }else{
      try(Cursor existing=db.query("notes",new String[]{"body","relay"},"id=?",new String[]{id},null,null,null)){
       if(!existing.moveToFirst()||!body.equals(existing.getString(0)))return "笔记编号冲突，草稿已保留";
       textPending=!"presented".equals(existing.getString(1));
      }
      db.execSQL("UPDATE notes SET relay='pending' WHERE id=? AND relay='local'",new Object[]{id});
     }
    }
    db.setTransactionSuccessful();
    return !audioPresented||textPending?"已接收，待转交微信":"已转交微信，请确认收藏";
   }finally{db.endTransaction();}
  }catch(Exception e){return "录音保存失败，草稿已保留";}finally{close();}
 }
 synchronized String save(String id,String text){
  if(id==null||text==null||!id.matches("[a-zA-Z0-9-]{1,100}")||text.trim().length()==0||text.length()>2000)return "文字或编号无效，未保存";
  try{SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT body,relay FROM notes WHERE id=?",new String[]{id})){if(c.moveToFirst()){if(!text.equals(c.getString(0)))return "编号冲突，未保存";if("presented".equals(c.getString(1)))return "已转交微信，请确认收藏";if("local".equals(c.getString(1)))db.execSQL("UPDATE notes SET relay='pending' WHERE id=?",new Object[]{id});return "已接收，待存入微信";}}
   ContentValues v=new ContentValues();v.put("id",id);v.put("body",text);v.put("created",System.currentTimeMillis());v.put("relay","pending");db.insertOrThrow("notes",null,v);return "已接收，待存入微信";
  }catch(Exception e){return "保存失败，文字仍在索尼，请重试";}finally{close();}
 }
 int count(boolean pending){
  String states=pending?"('pending')":"('pending','presented')";
  String where=" WHERE relay IN "+states;
  try(Cursor c=getReadableDatabase().rawQuery(
      "SELECT (SELECT count(*) FROM notes"+where+")+(SELECT count(*) FROM voice_notes"+where+")",null)){
   c.moveToFirst();return c.getInt(0);
  }finally{close();}
 }
 static final class VoiceNote {
  final String id,body;
  final long created;
  VoiceNote(String id,String body,long created){this.id=id;this.body=body;this.created=created;}
  String filename(){return new java.text.SimpleDateFormat("yyyy-MM-dd HHmm",Locale.US)
      .format(new Date(created))+" 语音笔记.m4a";}
 }
 VoiceNote nextVoice(boolean pending) {
  try(Cursor c=getReadableDatabase().query("voice_notes",new String[]{"id","body","created"},
      "relay=?",new String[]{pending?"pending":"presented"},null,null,
      pending?"created ASC,rowid ASC":"created DESC,rowid DESC","1")){
   return c.moveToFirst()?new VoiceNote(c.getString(0),c.getString(1),c.getLong(2)):null;
  }finally{close();}
 }
 VoiceNote voice(String id) {
  try(Cursor c=getReadableDatabase().query("voice_notes",new String[]{"id","body","created"},
      "id=?",new String[]{id},null,null,null)){
   return c.moveToFirst()?new VoiceNote(c.getString(0),c.getString(1),c.getLong(2)):null;
  }finally{close();}
 }
 byte[] voiceAudio(String id) {
  try(Cursor c=getReadableDatabase().query("voice_notes",new String[]{"audio"},
      "id=?",new String[]{id},null,null,null)){
   return c.moveToFirst()?c.getBlob(0):null;
  }finally{close();}
 }
 void presentedVoice(String id) {
  try{getWritableDatabase().execSQL("UPDATE voice_notes SET relay='presented' WHERE id=?",new Object[]{id});}
  finally{close();}
 }
 static final class Batch{final ArrayList<String> ids=new ArrayList<>();String text="";}
 Batch batch(){Batch pending=batch(true);return pending.ids.isEmpty()?batch(false):pending;}
 Batch batch(boolean pending){Batch batch=new Batch();StringBuilder body=new StringBuilder();try{SQLiteDatabase db=getReadableDatabase();String condition=pending?"pending":"presented";try(Cursor c=db.rawQuery("SELECT id,body,created FROM notes WHERE relay=? ORDER BY created ASC LIMIT 10",new String[]{condition})){while(c.moveToNext()){batch.ids.add(c.getString(0));if(body.length()>0)body.append("\n\n────────\n\n");body.append(android.text.format.DateFormat.format("MM-dd HH:mm",c.getLong(2))).append("\n").append(c.getString(1));}}batch.text=body.toString();return batch;}finally{close();}}
 void presented(Batch b){try{SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String id:b.ids)db.execSQL("UPDATE notes SET relay='presented' WHERE id=?",new Object[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}}finally{close();}}
 String list(){StringBuilder s=new StringBuilder();try(Cursor c=getReadableDatabase().rawQuery("SELECT body,created FROM notes ORDER BY created DESC LIMIT 50",null)){while(c.moveToNext())s.append(android.text.format.DateFormat.format("MM-dd HH:mm",c.getLong(1))).append("\n").append(c.getString(0)).append("\n\n");}finally{close();}return s.length()==0?"还没有速记":s.toString();}
}
