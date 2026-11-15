package org.retrolink.legacy;
import android.app.*;
import android.content.*;
import android.os.*;
import android.bluetooth.*;
import org.json.*;
import java.io.*;
import java.util.*;

// Bluetooth access is gated by MainActivity permissions; legacy APK targets pre-31 Android.
@android.annotation.SuppressLint("MissingPermission")
public class LinkService extends Service {
 public static final java.util.LinkedHashMap<String,String> messages=new java.util.LinkedHashMap<String,String>();
 public static volatile String latestPhoneStatus="";
 public static volatile String latestCalendar="";
 public static volatile String latestNoteAck="";
 public static volatile String latestChatAck="";
 public static volatile boolean connected=false;
 public static volatile String latestStatus="尚未连接", latestData="";
 public static volatile android.graphics.Bitmap albumArt;
 public static volatile long latestStateAt;
 private static volatile String albumArtKey="",wantedArtKey="";
 private final java.util.concurrent.ThreadPoolExecutor artWorker=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(1),new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
 public static final String EVENT="org.retrolink.legacy.EVENT";
 private static final UUID UUID_LINK=UUID.fromString("f763c920-dcd1-48a8-bbea-620879e6f823");
 private final Handler handler=new Handler();
 private volatile boolean running=false;
 private volatile BluetoothSocket socket;
 private volatile BluetoothServerSocket server;
 private DataOutputStream output;
 private volatile long lastRead=0;
 private final java.util.concurrent.ThreadPoolExecutor writer=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(32));
 private PhotoSync photos;
 private String peer="", session="";
 private long sequence=0;
 private final LinkedHashSet<String> seen=new LinkedHashSet<String>();

 public void onCreate(){super.onCreate(); Notification n=new Notification(android.R.drawable.ic_media_play,"Retro Link 已启动",System.currentTimeMillis());
  Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,0);
  try{Notification.class.getMethod("setLatestEventInfo",Context.class,CharSequence.class,CharSequence.class,PendingIntent.class).invoke(n,this,"Retro Link","音乐遥控连接服务",pi);}catch(Exception e){}
  startForeground(1,n); }
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null && "stop".equals(intent.getAction())){getSharedPreferences("link",0).edit().putBoolean("connection_enabled",false).commit();stopSelf();return START_NOT_STICKY;}
  if(intent!=null && "command".equals(intent.getAction())){command(intent.getStringExtra("op"));return START_STICKY;}
  if(intent!=null && ("phone_status_request".equals(intent.getAction())||
      "phone_ring".equals(intent.getAction())||"phone_ring_stop".equals(intent.getAction())||"phone_mode".equals(intent.getAction()))){
   try{send(new JSONObject().put("type",intent.getAction()).put("session",session).put("id",intent.getStringExtra("id")).put("mode",intent.getIntExtra("mode",-1)));}
   catch(Exception e){event("主手机未连接");}
   return START_STICKY;
  }
  if(intent!=null && "calendar_write".equals(intent.getAction())){
   JSONObject request=null;
   try{request=new JSONObject(intent.getStringExtra("data"));request.put("type","calendar_write");request.put("session",session);send(request);}
   catch(Exception e){if(request!=null)try{data(new JSONObject().put("type","calendar_ack").put("id",request.optString("id")).put("result","蓝牙未连接，未保存"));}catch(Exception ignored){}}
   return START_STICKY;
  }
  if(intent!=null && "calendar".equals(intent.getAction())){try{JSONObject j=new JSONObject();j.put("type","calendar_request");j.put("session",session);send(j);}catch(Exception e){event("日历未刷新：蓝牙未连接");}return START_STICKY;}
  if(intent!=null && "reply".equals(intent.getAction())){try{JSONObject j=new JSONObject();j.put("type","reply");j.put("id",java.util.UUID.randomUUID().toString());j.put("session",session);j.put("token",intent.getStringExtra("token"));j.put("text",intent.getStringExtra("text"));send(j);}catch(Exception e){JSONObject ack=new JSONObject();try{ack.put("type","reply_ack");ack.put("result","手机未连接，未发送");data(ack);}catch(Exception ignored){}}return START_STICKY;}
  if(intent!=null && "note".equals(intent.getAction())){String id=intent.getStringExtra("id");try{JSONObject j=new JSONObject();j.put("type","note");j.put("id",id);j.put("session",session);j.put("text",intent.getStringExtra("text"));j.put("audio",intent.getStringExtra("audio"));send(j);}catch(Exception e){try{JSONObject ack=new JSONObject();ack.put("type","note_ack");ack.put("id",id);ack.put("result","蓝牙未连接，未保存");latestNoteAck=ack.toString();data(ack);}catch(Exception ignored){}}return START_STICKY;}
  if(intent!=null && "chatgpt".equals(intent.getAction())){String id=intent.getStringExtra("id");try{JSONObject j=new JSONObject();j.put("type","chatgpt");j.put("id",id);j.put("session",session);j.put("text",intent.getStringExtra("text"));send(j);}catch(Exception e){try{JSONObject ack=new JSONObject();ack.put("type","chatgpt_ack");ack.put("id",id);ack.put("result","蓝牙未连接，未转交");latestChatAck=ack.toString();data(ack);}catch(Exception ignored){}}return START_STICKY;}
  if(!running){
   peer=getSharedPreferences("link",0).getString("peer","");
   if(peer.length()==0){event("请先选择配对手机");stopSelf();return START_NOT_STICKY;}
   getSharedPreferences("link",0).edit().putBoolean("connection_enabled",true).commit();running=true;
   new Thread(new Runnable(){public void run(){loop();}},"retro-link").start();
   photos=new PhotoSync(this);photos.start();
   handler.post(heartbeat);
  }
  return START_STICKY;
 }
 private void event(String text){latestStatus=text;android.util.Log.i("RetroLink",text);sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("status",text));}
 private void data(JSONObject obj){if("state".equals(obj.optString("type"))){latestData=obj.toString();latestStateAt=android.os.SystemClock.elapsedRealtime();wantedArtKey=obj.optString("art_key","");if(!wantedArtKey.equals(albumArtKey)){albumArt=null;albumArtKey="";}}sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("data",obj.toString()));}
 private final Runnable heartbeat=new Runnable(){public void run(){
  if(!running)return;
  if(socket!=null && output!=null){
   if(android.os.SystemClock.elapsedRealtime()-lastRead>90000)closeConnection();
   else try{JSONObject j=new JSONObject();j.put("type","ping");send(j);}catch(Exception e){closeConnection();}
  }
  handler.postDelayed(this,30000);
 }};
 private void loop(){int delay=2000;
  while(running){try{
   BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
   if(adapter==null || !adapter.isEnabled())throw new IOException("请打开蓝牙");
   event("正在连接主手机");BluetoothSocket next=adapter.getRemoteDevice(peer).createRfcommSocketToServiceRecord(UUID_LINK);socket=next;adapter.cancelDiscovery();next.connect();
   socket=next;
   synchronized(this){output=new DataOutputStream(next.getOutputStream());}
   lastRead=android.os.SystemClock.elapsedRealtime();
   session=java.util.UUID.randomUUID().toString();seen.clear();
   JSONObject hello=new JSONObject();hello.put("type","hello");hello.put("version",1);hello.put("session",session);send(hello);
   connected=true;event("蓝牙已连接"); delay=2000;
   DataInputStream input=new DataInputStream(next.getInputStream());
   while(running && socket==next){
    byte[] bytes=FrameCodec.read(input);lastRead=android.os.SystemClock.elapsedRealtime();
    final JSONObject message=new JSONObject(new String(bytes,"UTF-8"));
    handler.post(new Runnable(){public void run(){if(socket==next)receive(message);}});
   }
  }catch(Exception e){if(running)event("连接中断，等待重连："+e.getMessage());}
  finally{closeConnection();}
  if(running){try{Thread.sleep(delay);}catch(InterruptedException e){}delay=Math.min(delay*2,30000);}
 }}
 private void send(JSONObject obj)throws Exception{
  final DataOutputStream target;final BluetoothSocket connection;
  synchronized(this){target=output;connection=socket;}
  if(target==null || connection==null)throw new IOException("手机未连接");
  final byte[] bytes=obj.toString().getBytes("UTF-8");if(bytes.length>98304)throw new IOException("消息过长");
  writer.execute(new Runnable(){public void run(){
   if(socket!=connection)return;
   try{target.writeInt(bytes.length);target.write(bytes);target.flush();}
   catch(Exception e){if(socket==connection){event("连接中断：发送失败");closeConnection();}}
  }});
 }
 private void receive(JSONObject j){try{
  String type=j.optString("type");
  if("ping".equals(type)){JSONObject pong=new JSONObject();pong.put("type","pong");send(pong);return;}
  if("hello".equals(type)){if(j.optInt("version")!=1){closeConnection();return;} session=j.optString("session");
   if(DeviceClock.sync(j.optLong("time_ms",0),j.optString("time_zone"))){
    android.util.Log.i("RetroLink","Clock synchronized: "+DeviceClock.time());
    sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("clock_sync",true));
   }
   return;}
  if("album_art".equals(type)){receiveArt(j);return;}
  if("phone_status".equals(type)){latestPhoneStatus=j.toString();data(j);return;}
  if("phone_status_ack".equals(type)){data(j);return;}
  if("calendar_ack".equals(type)){data(j);return;}
  if("calendar".equals(type)){latestCalendar=j.toString();data(j);return;}
  if("note_ack".equals(type)){latestNoteAck=j.toString();data(j);return;}
  if("chatgpt_ack".equals(type)){latestChatAck=j.toString();data(j);return;}
  if(type.startsWith("notification")){
   synchronized(messages){if("notification_reset".equals(type))messages.clear();else if("notification_remove".equals(type))messages.remove(j.optString("key"));else{messages.put(j.optString("key"),j.toString());while(messages.size()>20)messages.remove(messages.keySet().iterator().next());}}
   data(j);return;
  }
  if("state".equals(type)||"ack".equals(type)||"reply_ack".equals(type))data(j);
 }catch(Exception e){event("处理失败："+e.getMessage());}}
 private void receiveArt(JSONObject j){
  final String key=j.optString("key"),encoded=j.optString("jpeg");
  if(key.length()==0 || !key.equals(wantedArtKey) || encoded.length()>16000){android.util.Log.i("RetroArt","image rejected");return;}
  artWorker.execute(new Runnable(){public void run(){
   try{
    byte[] bytes=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);
    if(bytes.length==0 || bytes.length>12000)return;
    android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
    if(bounds.outWidth<1 || bounds.outHeight<1 || bounds.outWidth>160 || bounds.outHeight>160)return;
    final android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);
    if(image==null)return;
    handler.post(new Runnable(){public void run(){if(!key.equals(wantedArtKey)){image.recycle();return;}albumArt=image;albumArtKey=key;android.util.Log.i("RetroArt","image ready");sendBroadcast(new Intent(EVENT).setPackage(getPackageName()));}});
   }catch(IllegalArgumentException ignored){}catch(OutOfMemoryError ignored){}
  }});
 }
 private void command(String op){ try{JSONObject j=new JSONObject();j.put("type","command");j.put("op",op);j.put("id",Long.toString(++sequence));j.put("session",session);send(j);event("指令已发送，等待播放器");}catch(Exception e){event("未发送：手机未连接");} }
 private synchronized void closeConnection(){connected=false;output=null;try{if(socket!=null)socket.close();}catch(Exception e){}socket=null;try{if(server!=null)server.close();}catch(Exception e){}server=null;}
 public void onDestroy(){running=false;artWorker.shutdownNow();if(photos!=null)photos.close();handler.removeCallbacksAndMessages(null);closeConnection();writer.shutdownNow();  stopForeground(true);super.onDestroy();}
 public IBinder onBind(Intent i){return null;}

}
