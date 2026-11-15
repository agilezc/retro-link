package org.retrolink.host;
import android.content.*;
import android.bluetooth.*;
import android.provider.MediaStore;
import android.net.Uri;
import java.io.*;
import java.security.*;
import java.util.*;
final class PhotoReceiver {
 private final Context context;private volatile boolean running=true;private volatile BluetoothServerSocket server;private volatile BluetoothSocket socket;
 PhotoReceiver(Context c){context=c;}
 @android.annotation.SuppressLint("MissingPermission")
 void start(){new Thread(new Runnable(){public void run(){while(running){try{server=BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord("RetroPhotos",UUID.fromString("f763c920-dcd1-48a8-bbea-620879e6f824"));while(running){socket=server.accept();final BluetoothSocket bounded=socket;new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){try{bounded.close();}catch(Exception ignored){}}},180000);try{if(context.getSharedPreferences("link",0).getString("peer","").equals(socket.getRemoteDevice().getAddress()))receive(socket);}finally{socket.close();socket=null;}}}catch(Exception ignored){}finally{try{if(server!=null)server.close();}catch(Exception ignored){}}try{Thread.sleep(3000);}catch(Exception ignored){}}}},"photo-receiver").start();}
 private void receive(BluetoothSocket socket)throws Exception{
  DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());String hash=in.readUTF();long size=in.readLong();if(!hash.matches("[a-f0-9]{64}")||size<=0||size>64*1024*1024)return;
  ContentResolver cr=context.getContentResolver();String name="Retro_"+hash+".jpg";
  try(android.database.Cursor c=cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},"_display_name=? AND is_pending=0",new String[]{name},null)){if(c!=null&&c.moveToFirst()){out.writeUTF("SAVED");out.flush();return;}}
  out.writeUTF("SEND");out.flush();File tmp=new File(context.getCacheDir(),"photo-upload.tmp");Uri uri=null;
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[16384];long left=size;
   try(OutputStream file=new FileOutputStream(tmp)){while(left>0){int n=in.read(b,0,(int)Math.min(b.length,left));if(n<0)throw new EOFException();file.write(b,0,n);digest.update(b,0,n);left-=n;}}
   StringBuilder actual=new StringBuilder();for(byte value:digest.digest())actual.append(String.format(Locale.US,"%02x",value&255));if(!hash.equals(actual.toString()))throw new IOException("checksum");
   android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(tmp.getPath(),opts);if(opts.outWidth<=0||opts.outHeight<=0||!"image/jpeg".equals(opts.outMimeType))throw new IOException("JPEG required");
   ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Retro Link");v.put(MediaStore.Images.Media.IS_PENDING,1);uri=cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException();
   try(InputStream src=new FileInputStream(tmp);OutputStream dest=cr.openOutputStream(uri)){int n;while((n=src.read(b))!=-1)dest.write(b,0,n);}
   v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);if(cr.update(uri,v,null,null)!=1)throw new IOException();uri=null;out.writeUTF("SAVED");out.flush();
  }finally{tmp.delete();if(uri!=null)cr.delete(uri,null,null);}
 }
 void close(){running=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}try{if(server!=null)server.close();}catch(Exception ignored){}}
}
