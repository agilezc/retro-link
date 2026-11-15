package org.retrolink.legacy;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/** Separate RFCOMM stream keeps large images out of the command queue. */
@android.annotation.SuppressLint("MissingPermission")
final class PhotoSync {
 private static final UUID CHANNEL=UUID.fromString("f763c920-dcd1-48a8-bbea-620879e6f824");
 private static final long MAX_BYTES=64L*1024*1024;
 private static final String FILE_INDEX="photo_file_index";
 private final Context context;
 private final SharedPreferences prefs;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private volatile boolean running=true;
 private volatile BluetoothSocket socket;
 private Thread worker;

 PhotoSync(Context context) {
  this.context=context;
  prefs=preferences(context);
 }

 void start() {
  worker=new Thread(() -> {
   while(running) {
    try {
     scan();
    } catch(Exception error) {
     if(running) Log.w("RetroPhotos","Transfer will retry",error);
    }
    try {
     Thread.sleep(10000);
    } catch(InterruptedException stopped) {
     return;
    }
   }
  },"photo-sync");
  worker.start();
 }

 private boolean canSend() {
  return running && enabled(context) && LinkService.connected && mounted();
 }

 private void scan() throws Exception {
  if(!canSend())return;
  SharedPreferences index=context.getSharedPreferences(FILE_INDEX,Context.MODE_PRIVATE);
  if(!index.getBoolean("initialized",false) && !migrateIndex(index))return;
  for(File photo:cameraPhotos()) {
   if(!canSend())return;
   long size=photo.length(), modified=photo.lastModified();
   if(size<=0 || size>MAX_BYTES || System.currentTimeMillis()-modified<3000)continue;
   String stamp=stamp(size,modified);
   if(stamp.equals(index.getString(photo.getAbsolutePath(),"")))continue;
   if(!send(photo,size,modified))return;
   if(!index.edit().putString(photo.getAbsolutePath(),stamp).commit()){
    throw new IOException("Photo progress could not be saved");
   }
   Log.i("RetroPhotos","Photo saved on host: "+photo.getName());
  }
 }

 private boolean migrateIndex(SharedPreferences index) {
  Cursor rows=context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      new String[]{"_data"},"_id<=?",new String[]{Long.toString(prefs.getLong("after",0))},null);
  if(rows==null)return false;
  SharedPreferences.Editor edit=index.edit();
  try {
   while(rows.moveToNext()) {
    String path=rows.getString(0);
    if(isCameraJpeg(path)){
     File photo=new File(path);
     if(photo.isFile())edit.putString(photo.getCanonicalPath(),stamp(photo.length(),photo.lastModified()));
    }
   }
   return edit.putBoolean("initialized",true).commit();
  } catch(IOException invalidPath) {
   Log.w("RetroPhotos","Could not migrate backup index",invalidPath);
   return false;
  } finally {
   rows.close();
  }
 }

 private static ArrayList<File> cameraPhotos() throws IOException {
  ArrayList<File> photos=new ArrayList<File>();
  File dcim=new File(Environment.getExternalStorageDirectory(),"DCIM").getCanonicalFile();
  collectPhotos(dcim,photos);
  return photos;
 }

 private static void collectPhotos(File directory,ArrayList<File> photos) {
  File[] children=directory.listFiles();
  if(children==null)return;
  for(File child:children) {
   if(child.getName().startsWith("."))continue;
   if(child.isDirectory())collectPhotos(child,photos);
   else if(isCameraJpeg(child.getAbsolutePath()))photos.add(child);
  }
 }

 private static String stamp(long size,long modified) {
  return size+":"+modified;
 }

 private static boolean isCameraJpeg(String path) {
  if(path==null)return false;
  String lower=path.toLowerCase(Locale.US);
  return lower.contains("/dcim/") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
 }

 private boolean send(File photo,long size,long modified) throws Exception {
  byte[] buffer=new byte[16384];
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(FileInputStream source=new FileInputStream(photo)) {
   int count;
   while(canSend() && (count=source.read(buffer))!=-1)digest.update(buffer,0,count);
  }
  if(!canSend() || photo.length()!=size || photo.lastModified()!=modified)return false;
  StringBuilder hash=new StringBuilder();
  for(byte value:digest.digest())hash.append(String.format(Locale.US,"%02x",value&255));

  String peer=context.getSharedPreferences("link",0).getString("peer","");
  BluetoothSocket next=BluetoothAdapter.getDefaultAdapter().getRemoteDevice(peer)
      .createRfcommSocketToServiceRecord(CHANNEL);
  socket=next;
  Runnable timeout=() -> closeSocket(next);
  handler.postDelayed(timeout,180000);
  try {
   if(!canSend())return false;
   next.connect();
   DataOutputStream out=new DataOutputStream(next.getOutputStream());
   DataInputStream in=new DataInputStream(next.getInputStream());
   out.writeUTF(hash.toString());
   out.writeLong(size);
   out.flush();
   String response=in.readUTF();
   if("SEND".equals(response)) {
    try(FileInputStream source=new FileInputStream(photo)) {
     long remaining=size;
     while(remaining>0) {
      if(!canSend())return false;
      int count=source.read(buffer,0,(int)Math.min(remaining,buffer.length));
      if(count<0)throw new IOException("Photo changed during transfer");
      out.write(buffer,0,count);
      remaining-=count;
     }
    }
    out.flush();
    response=in.readUTF();
   }
   return "SAVED".equals(response);
  } finally {
   handler.removeCallbacks(timeout);
   closeSocket(next);
   socket=null;
  }
 }

 private static SharedPreferences preferences(Context context) {
  return context.getSharedPreferences("photos",Context.MODE_PRIVATE);
 }

 static boolean enabled(Context context) {
  return preferences(context).getBoolean("enabled",false);
 }

 private static boolean mounted() {
  return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
 }

 static void options(Activity activity,Runnable changed) {
  boolean isEnabled=enabled(activity);
  boolean hasCard=mounted();
  String message=!hasCard?"请先插入存储卡。":isEnabled
      ?"用系统相机拍照，连接主手机后自动保存到相册「Retro Link」。"
      :"开启后，新拍的照片会自动保存到主手机相册「Retro Link」。";
  RetroDialog.show(activity,"照片备份",message,
      isEnabled?new String[]{"关闭自动备份"}:hasCard?new String[]{"开启自动备份"}:new String[]{"知道了"},
      -1,index -> {
       if(!isEnabled && !mounted())return;
       if(!enable(activity,!isEnabled)) {
        android.widget.Toast.makeText(activity,"无法更新照片备份，请检查存储卡",android.widget.Toast.LENGTH_SHORT).show();
        return;
       }
       if(!isEnabled && !LinkService.connected
           && activity.getSharedPreferences("link",0).getBoolean("connection_enabled",true)
           && !activity.getSharedPreferences("link",0).getString("peer","").isEmpty()) {
        activity.startService(new android.content.Intent(activity,LinkService.class));
       }
       if(changed!=null)changed.run();
      });
 }

 private static boolean enable(Context context,boolean value) {
  SharedPreferences prefs=preferences(context);
  if(value && !prefs.getBoolean("enabled",false)) {
   SharedPreferences index=context.getSharedPreferences(FILE_INDEX,Context.MODE_PRIVATE);
   SharedPreferences.Editor baseline=index.edit().clear();
   try {
    for(File photo:cameraPhotos()) {
     baseline.putString(photo.getAbsolutePath(),stamp(photo.length(),photo.lastModified()));
    }
   } catch(IOException error) {
    return false;
   }
   if(!baseline.putBoolean("initialized",true).commit())return false;
  }
  return prefs.edit().putBoolean("enabled",value).commit();
 }

 void close() {
  running=false;
  if(worker!=null)worker.interrupt();
  closeSocket(socket);
 }

 private static void closeSocket(BluetoothSocket connection) {
  if(connection!=null)try{connection.close();}catch(IOException ignored){}
 }
}
