package org.retrolink.host;
import android.app.*;
import android.content.*;
import android.os.*;
import android.bluetooth.*;
import org.json.*;
import java.io.*;
import java.util.*;

// Bluetooth access is gated by MainActivity permissions; legacy APK targets pre-31 Android.
@android.annotation.SuppressLint({"MissingPermission","UnspecifiedRegisterReceiverFlag"})
public class LinkService extends Service {
 public static volatile boolean connected=false;
 public static volatile String phase="idle";
 public static volatile String latestStatus="尚未连接", latestData="";
 public static final String EVENT="org.retrolink.host.EVENT";
 private static final UUID UUID_LINK=UUID.fromString("f763c920-dcd1-48a8-bbea-620879e6f823");
 private final Handler handler=new Handler();
 private boolean unlockRegistered;private final BroadcastReceiver unlocked=new BroadcastReceiver(){public void onReceive(Context c,Intent i){NoteDelivery.update(c);}};
 private static volatile LinkService active;
 private volatile boolean running=false;
 private volatile BluetoothSocket socket;
 private volatile BluetoothServerSocket server;
 private DataOutputStream output;
 private volatile long lastRead=0;
 private final java.util.concurrent.ThreadPoolExecutor writer=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(32));
 private CalendarBridge calendar;
 private final PhoneStatus phoneStatus=new PhoneStatus(this,handler);
 private boolean batteryRegistered;
 private final BroadcastReceiver batteryChanged=new BroadcastReceiver(){
  public void onReceive(Context context,Intent intent){sendPhoneStatus();}
 };
 private void sendPhoneStatus(){if(connected)try{send(phoneStatus.snapshot());}catch(Exception ignored){}}

 private PhotoReceiver photos;
 private String peer="", session="";
 private long sequence=0;private volatile int failures=0;
 private final LinkedHashSet<String> seen=new LinkedHashSet<String>();
 private android.media.session.MediaSessionManager manager;
 private android.media.session.MediaController controller;
 private final java.util.concurrent.ThreadPoolExecutor artWorker=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(1),new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
 private android.graphics.Bitmap artSource;
 private String artIdentity="",artKey="",artPayload="",artSent="";
 private int artGeneration=0;
 private final android.media.session.MediaController.Callback mediaCallback=new android.media.session.MediaController.Callback(){
  public void onMetadataChanged(android.media.MediaMetadata m){artIdentity="";publish();}
  public void onPlaybackStateChanged(android.media.session.PlaybackState s){publish();}
  public void onSessionDestroyed(){refresh();}
 };
 private final android.media.session.MediaSessionManager.OnActiveSessionsChangedListener sessionsListener=new android.media.session.MediaSessionManager.OnActiveSessionsChangedListener(){public void onActiveSessionsChanged(java.util.List<android.media.session.MediaController> list){choose(list);}};

 public void onCreate(){super.onCreate();active=this;if(Build.VERSION.SDK_INT>=33)registerReceiver(unlocked,new IntentFilter(Intent.ACTION_USER_PRESENT),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(unlocked,new IntentFilter(Intent.ACTION_USER_PRESENT));unlockRegistered=true; NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("link","手机连接",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(this,"link").setSmallIcon(R.drawable.ic_device).setContentTitle("Retro Link").setContentText("手机连接服务运行中").setContentIntent(pi).build();startForeground(1,n); }
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null && "refresh".equals(intent.getAction())){if(running){try{manager.removeOnActiveSessionsChangedListener(sessionsListener);manager.addOnActiveSessionsChangedListener(sessionsListener,access(),handler);refresh();}catch(SecurityException ignored){}if(calendar!=null)calendar.refresh();}return START_STICKY;}
  if(intent!=null && "stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(intent!=null && "command".equals(intent.getAction())){command(intent.getStringExtra("op"));return START_STICKY;}
  if(!running){
   peer=getSharedPreferences("link",0).getString("peer","");
   if(peer.length()==0){event("请先选择配对手机");stopSelf();return START_NOT_STICKY;}
   running=true; transition("waiting","等待手机连接"); MediaAccess.sink=new MediaAccess.Sink(){public void deliver(JSONObject j){try{data(j);if(connected)send(j);}catch(Exception e){event("消息同步失败，重连后恢复");}}}; manager=(android.media.session.MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);try{manager.addOnActiveSessionsChangedListener(sessionsListener,access(),handler);refresh();}catch(SecurityException e){event("请开启通知访问权限后重新连接");}
   new Thread(new Runnable(){public void run(){loop();}},"retro-link").start();
   calendar=new CalendarBridge(this,handler,new CalendarBridge.Sink(){public void deliver(JSONObject j){if(connected)try{send(j);}catch(Exception ignored){}}});calendar.start();
   IntentFilter statusChanges=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
   statusChanges.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
   registerReceiver(batteryChanged,statusChanges);batteryRegistered=true;
   photos=new PhotoReceiver(this);photos.start();
   handler.post(heartbeat);
  }
  return START_STICKY;
 }
 private void transition(String value,String text){if(active!=this)return;phase=value;connected="connected".equals(value);event(text);}
 private void event(String text){if(active!=this)return;latestStatus=text;android.util.Log.i("RetroLink",text);sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("status",text));}
 private void data(JSONObject obj){if(active!=this)return;if("state".equals(obj.optString("type")))latestData=obj.toString();sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("data",obj.toString()));}
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
   transition("waiting","等待手机连接");server=adapter.listenUsingRfcommWithServiceRecord("RetroLink",UUID_LINK);BluetoothSocket next=server.accept();server.close();server=null;
   if(!peer.equals(next.getRemoteDevice().getAddress())){next.close();throw new IOException("拒绝未选择的手机");}
   socket=next;
   synchronized(this){output=new DataOutputStream(next.getOutputStream());}
   lastRead=android.os.SystemClock.elapsedRealtime();
   session=java.util.UUID.randomUUID().toString();seen.clear();artSent="";
   JSONObject hello=new JSONObject();hello.put("type","hello");hello.put("version",1);hello.put("session",session);
   hello.put("time_ms",System.currentTimeMillis());hello.put("time_zone",TimeZone.getDefault().getID());send(hello);
   transition("handshake","正在确认连接"); delay=2000;
   final BluetoothSocket handshakeSocket=next;
   handler.postDelayed(new Runnable(){public void run(){if(socket==handshakeSocket && !connected){event("连接确认超时");closeConnection();}}},12000);
   DataInputStream input=new DataInputStream(next.getInputStream());
   while(running && socket==next){
    byte[] bytes=FrameCodec.read(input);lastRead=android.os.SystemClock.elapsedRealtime();
    final JSONObject message=new JSONObject(new String(bytes,"UTF-8"));
    handler.post(new Runnable(){public void run(){if(socket==next)receive(message);}});
   }
  }catch(Exception e){if(running){transition("retrying","连接中断，等待重连："+e.getMessage());}}
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
  if("hello".equals(type)){if(j.optInt("version")!=1){closeConnection();return;} failures=0;transition("connected","蓝牙已连接"); publish(); sendPhoneStatus(); MediaAccess.snapshot(); if(calendar!=null)calendar.refresh(); return;}
  if("phone_status_request".equals(type)||"phone_ring".equals(type)||"phone_ring_stop".equals(type)||"phone_mode".equals(type)){
   if(!connected||!session.equals(j.optString("session")))return;
   if(!"phone_status_request".equals(type)){
    String result;
    if("phone_mode".equals(type)){
     result=phoneStatus.setSoundMode(j.optInt("mode",-1));
    }else if("phone_ring_stop".equals(type)){phoneStatus.stop();result="响铃已停止";}
    else result=phoneStatus.ring(this::sendPhoneStatus);
    send(new JSONObject().put("type","phone_status_ack").put("id",j.optString("id")).put("result",result));
   }
   sendPhoneStatus();return;
  }
  if("calendar_write".equals(type)){
   if(connected && session.equals(j.optString("session")) && calendar!=null){
    final String active=session;
    calendar.write(j,new CalendarBridge.Sink(){public void deliver(JSONObject ack){
     if(connected && active.equals(session))try{send(ack);}catch(Exception ignored){}
    }});
   }
   return;
  }
  if("calendar_request".equals(type)){if(session.equals(j.optString("session")) && calendar!=null)calendar.refresh();return;}
  if("note".equals(type)){if(!session.equals(j.optString("session")))return;JSONObject ack=new JSONObject();ack.put("type","note_ack");ack.put("id",j.optString("id"));String audio=j.optString("audio","");
   NoteStore store=new NoteStore(this);
   ack.put("result",audio.isEmpty()?store.save(j.optString("id"),j.optString("text")):
     store.saveVoice(j.optString("id"),j.optString("text"),audio));
   send(ack);NoteDelivery.update(this);return;}
  if("chatgpt".equals(type)){
   if(!session.equals(j.optString("session")))return;
   JSONObject ack=new JSONObject();ack.put("type","chatgpt_ack");ack.put("id",j.optString("id"));
   String key="chatgpt:"+j.optString("id");
   if(j.optString("id").length()==0 || j.optString("id").length()>100)ack.put("result","请求编号无效，未转交");
   else if(seen.contains(key))ack.put("result","重复请求已忽略，请查看 Nothing");
   else{seen.add(key);if(seen.size()>128)seen.remove(seen.iterator().next());ack.put("result",ChatGptRelay.dispatch(j.optString("text")));}
   send(ack);data(ack);return;
  }
  if("reply".equals(type)){
   if(!session.equals(j.optString("session")))return;
   JSONObject ack=new JSONObject();ack.put("type","reply_ack");ack.put("id",j.optString("id"));
   String id="reply:"+j.optString("id");
   if(seen.contains(id))ack.put("result","重复请求已忽略，请在主手机确认状态");
   else{seen.add(id);if(seen.size()>128)seen.remove(seen.iterator().next());ack.put("result",MediaAccess.reply(j.optString("token"),j.optString("text")));}
   send(ack);return;
  }
  if("command".equals(type))execute(j);
 }catch(Exception e){event("处理失败："+e.getMessage());}}
 private void command(String op){  }
 private synchronized void closeConnection(){if(active==this)connected=false;output=null;try{if(socket!=null)socket.close();}catch(Exception e){}socket=null;try{if(server!=null)server.close();}catch(Exception e){}server=null;transition(running?"retrying":"failed".equals(phase)?"failed":"idle",running?"等待重连":"failed".equals(phase)?"连接失败，请重试":"已断开");}
 public void onDestroy(){phoneStatus.stop();if(batteryRegistered){unregisterReceiver(batteryChanged);batteryRegistered=false;}if(unlockRegistered){unregisterReceiver(unlocked);unlockRegistered=false;}running=false;artWorker.shutdownNow();if(photos!=null)photos.close();if(calendar!=null)calendar.close();MediaAccess.sink=null;handler.removeCallbacksAndMessages(null);closeConnection();writer.shutdownNow(); if(controller!=null)controller.unregisterCallback(mediaCallback);if(manager!=null)try{manager.removeOnActiveSessionsChangedListener(sessionsListener);}catch(SecurityException ignored){} stopForeground(true);super.onDestroy();}
 public IBinder onBind(Intent i){return null;}

 private android.content.ComponentName access(){return new android.content.ComponentName(this,MediaAccess.class);}
 private void refresh(){try{choose(manager.getActiveSessions(access()));}catch(SecurityException e){event("请开启 Retro Link 通知访问权限");}}
 private void choose(java.util.List<android.media.session.MediaController> list){
  android.media.session.MediaController next=null;
  for(android.media.session.MediaController c:list){if(next==null)next=c;android.media.session.PlaybackState s=c.getPlaybackState();if(s!=null && s.getState()==android.media.session.PlaybackState.STATE_PLAYING){next=c;break;}}
  if(controller!=null)controller.unregisterCallback(mediaCallback);controller=next;
  if(controller!=null)controller.registerCallback(mediaCallback,handler);publish();
 }
 private void publish(){try{
  JSONObject j=new JSONObject();j.put("type","state");j.put("title","请在主手机播放音乐");j.put("artist","");j.put("playing",false);j.put("actions",0);
  if(controller!=null){
   android.media.MediaMetadata m=controller.getMetadata();android.media.session.PlaybackState s=controller.getPlaybackState();
   j.put("app",controller.getPackageName());
   android.graphics.Bitmap cover=null;
   if(m!=null){j.put("title",cut(m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)));j.put("artist",cut(m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)));cover=m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);if(cover==null)cover=m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);if(cover==null)cover=m.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);}
   android.util.Log.i("RetroArt","bitmap="+(cover!=null)+" uri="+(m!=null&&(m.containsKey(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART_URI)||m.containsKey(android.media.MediaMetadata.METADATA_KEY_ART_URI)||m.containsKey(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)))+" connected="+connected);
   prepareArt(cover,controller.getPackageName()+"\n"+j.optString("title")+"\n"+j.optString("artist"));
   if(m!=null)j.put("duration_ms",Math.max(0,m.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)));
   if(s!=null){j.put("playing",s.getState()==android.media.session.PlaybackState.STATE_PLAYING);j.put("actions",s.getActions());long position=s.getPosition();if(position>=0){if(s.getState()==android.media.session.PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime()>0)position+=(long)((android.os.SystemClock.elapsedRealtime()-s.getLastPositionUpdateTime())*s.getPlaybackSpeed());j.put("position_ms",Math.max(0,position));}}
  }else prepareArt(null,"");
  j.put("art_key",artKey);
  data(j);if(output!=null){send(j);sendArt();}
 }catch(Exception e){event("状态发送失败："+e.getMessage());}}
 private void prepareArt(final android.graphics.Bitmap bitmap,String identity){
  if(identity.equals(artIdentity) && (bitmap==null)==(artSource==null))return;
  artSource=bitmap;artIdentity=identity;artPayload="";artSent="";final int generation=++artGeneration;
  artKey=bitmap==null?"":java.util.UUID.randomUUID().toString();
  if(bitmap==null)return;
  artWorker.execute(new Runnable(){public void run(){final byte[] bytes=AlbumArt.encode(bitmap);handler.post(new Runnable(){public void run(){
   if(!running || generation!=artGeneration)return;
   if(bytes==null){android.util.Log.i("RetroArt","encode failed");artKey="";publish();return;}
   artPayload=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);try{sendArt();}catch(Exception ignored){}
  }});}});
 }
 private void sendArt()throws Exception{
  if(!connected || artKey.length()==0 || artPayload.length()==0 || artKey.equals(artSent))return;
  JSONObject image=new JSONObject();image.put("type","album_art");image.put("key",artKey);image.put("jpeg",artPayload);send(image);artSent=artKey;android.util.Log.i("RetroArt","image queued");
 }
 private String cut(String s){return s==null?"":s.substring(0,Math.min(s.length(),300));}
 private void execute(JSONObject j)throws Exception{
  String id=j.optString("id");if(!session.equals(j.optString("session")))return;
  JSONObject ack=new JSONObject();ack.put("type","ack");ack.put("id",id);
  if(seen.contains(id)){ack.put("result","重复指令已忽略");send(ack);return;}
  seen.add(id);if(seen.size()>128)seen.remove(seen.iterator().next());
  try{
   String op=j.optString("op");
   if("volume_up".equals(op)||"volume_down".equals(op)){android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);if(audio==null)throw new IOException("音量控制不可用");audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,"volume_up".equals(op)?android.media.AudioManager.ADJUST_RAISE:android.media.AudioManager.ADJUST_LOWER,0);ack.put("result","音量指令已发送");send(ack);return;}
   if(controller==null)throw new IOException("请先在主手机播放音乐");
   android.media.session.PlaybackState s=controller.getPlaybackState();long actions=s==null?0:s.getActions();
   android.media.session.MediaController.TransportControls t=controller.getTransportControls();
   if("toggle".equals(op)){
    boolean playing=s!=null && s.getState()==android.media.session.PlaybackState.STATE_PLAYING;
    long required=playing?android.media.session.PlaybackState.ACTION_PAUSE:android.media.session.PlaybackState.ACTION_PLAY;
    if((actions & (required|android.media.session.PlaybackState.ACTION_PLAY_PAUSE))==0)throw new IOException("播放器不支持此操作");
    if(playing)t.pause();else t.play();
   }else if("next".equals(op)){if((actions & android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)==0)throw new IOException("播放器不支持下一首");t.skipToNext();}
   else if("previous".equals(op)){if((actions & android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)==0)throw new IOException("播放器不支持上一首");t.skipToPrevious();}
   else throw new IOException("未知操作");
   ack.put("result","已交给播放器");
  }catch(Exception e){ack.put("result",e.getMessage());}
  send(ack);data(ack);publish();
 }

}
