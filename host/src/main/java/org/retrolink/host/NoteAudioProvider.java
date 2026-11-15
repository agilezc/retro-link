package org.retrolink.host;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants a read-only URI for just the recording selected for WeChat. */
public final class NoteAudioProvider extends ContentProvider {
 static final String MIME="audio/mp4";

 static Uri uri(Context context,NoteStore.VoiceNote note){
  return new Uri.Builder().scheme("content").authority(context.getPackageName()+".note-audio")
      .appendPath(note.id).appendPath(note.filename()).build();
 }

 @Override public boolean onCreate(){return true;}
 @Override public String getType(Uri uri){return MIME;}

 private String id(Uri uri){
  java.util.List<String> parts=uri.getPathSegments();
  if((parts.size()!=1 && parts.size()!=2) || !parts.get(0).matches("[a-zA-Z0-9-]{1,100}")){
   throw new IllegalArgumentException("Invalid recording URI");
  }
  return parts.get(0);
 }

 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
  NoteStore store=new NoteStore(getContext());
  NoteStore.VoiceNote note=store.voice(id(uri));
  String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
  MatrixCursor result=new MatrixCursor(columns,1);
  if(note!=null){
   File audio=NoteAudioTranscoder.attachment(getContext(),note.id);
   Object[] row=new Object[columns.length];
   for(int i=0;i<columns.length;i++){
    if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=note.filename();
    if(OpenableColumns.SIZE.equals(columns[i]) && audio.isFile())row[i]=audio.length();
   }
   result.addRow(row);
  }
  return result;
 }

 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  if(!"r".equals(mode))throw new FileNotFoundException("Recording is read-only");
  if(uri.getPathSegments().size()==2 && !uri.getLastPathSegment().endsWith(".m4a")){
   throw new FileNotFoundException("Recording not found");
  }
  File audio=NoteAudioTranscoder.attachment(getContext(),id(uri));
  return ParcelFileDescriptor.open(audio,ParcelFileDescriptor.MODE_READ_ONLY);
 }

 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
}
