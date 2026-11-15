package org.retrolink.host;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import org.json.JSONException;
import org.json.JSONObject;

final class PhoneStatus {
 private final Context context;
 private final Handler handler;
 private MediaPlayer player;
 private boolean playing;
 private Runnable notifyStopped;
 private final Runnable stopTask = () -> {
  Runnable changed=notifyStopped;
  stop();
  if(changed!=null)changed.run();
 };

 PhoneStatus(Context context, Handler handler) {
  this.context = context;
  this.handler = handler;
 }

 boolean isDoNotDisturb(){
  android.app.NotificationManager manager=(android.app.NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
  int filter=manager.getCurrentInterruptionFilter();
  return filter!=android.app.NotificationManager.INTERRUPTION_FILTER_ALL
      && filter!=android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
 }

 String setSoundMode(int mode) {
  if (mode < AudioManager.RINGER_MODE_SILENT || mode > AudioManager.RINGER_MODE_NORMAL) {
   return "声音模式无效";
  }
  NotificationManager manager = context.getSystemService(NotificationManager.class);
  AudioManager audio = context.getSystemService(AudioManager.class);
  SharedPreferences preferences = context.getSharedPreferences("link", Context.MODE_PRIVATE);
  boolean appScopedDnd = Build.VERSION.SDK_INT >= 35;
  boolean ownsDnd = preferences.getBoolean("sound_mode_dnd", false);
  try {
   if (mode == AudioManager.RINGER_MODE_SILENT) {
    if (isDoNotDisturb()) return "主手机勿扰已开启";
    if (!manager.isNotificationPolicyAccessGranted()) return "请在主手机的授权页面开启勿扰访问";
    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    preferences.edit().putBoolean("sound_mode_dnd", true).apply();
    return "已请求开启勿扰";
   }
   if (ownsDnd) {
    if (!manager.isNotificationPolicyAccessGranted()) return "请在主手机的授权页面开启勿扰访问";
    if (appScopedDnd || manager.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
     manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    }
    preferences.edit().remove("sound_mode_dnd").apply();
   }
   if (isDoNotDisturb() && !ownsDnd) return "主手机的其他勿扰模式仍开启";
   audio.setRingerMode(mode);
   return audio.getRingerMode() == mode ? "声音模式已切换" : "系统未允许切换";
  } catch (SecurityException e) {
   return "请在主手机的授权页面开启勿扰访问";
  }
 }

 JSONObject snapshot() throws JSONException {
  Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  JSONObject result = new JSONObject().put("type","phone_status").put("ringing",playing).put("dnd",isDoNotDisturb());
  android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();
  ((android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
  result.put("ramTotal",memory.totalMem).put("ramAvailable",memory.availMem);
  android.os.StatFs disk=new android.os.StatFs(context.getFilesDir().getAbsolutePath());
  result.put("storageTotal",disk.getTotalBytes()).put("storageAvailable",disk.getAvailableBytes());
  if(android.os.Build.VERSION.SDK_INT>=29){
   android.os.PowerManager power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);
   result.put("thermal",power.getCurrentThermalStatus());
  }
  AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
  if(audio!=null)result.put("ringer",audio.getRingerMode());
  if (battery == null) return result;
  int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
  int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,0);
  int status=battery.getIntExtra(BatteryManager.EXTRA_STATUS,0);
  if(level>=0 && scale>0)result.put("battery",Math.min(100,Math.round(level*100f/scale)));
  String charging="未充电";
  if(status==BatteryManager.BATTERY_STATUS_FULL)charging="已充满";
  else if(status==BatteryManager.BATTERY_STATUS_CHARGING)charging="充电中";
  else if(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0)charging="已接电源";
  return result.put("charging",charging);
 }

 String ring(Runnable changed) {
  if(player!=null)return playing?"正在响铃":"正在准备响铃";
  AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
  if(audio==null || audio.getStreamVolume(AudioManager.STREAM_ALARM)==0)return "主手机的闹钟音量为零";
  try{
   MediaPlayer next=new MediaPlayer();
   player=next;
   notifyStopped=changed;
   next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
   next.setDataSource(context,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
   next.setLooping(true);
   next.setOnPreparedListener(ready->{
    if(player!=ready)return;
    try {ready.start();playing=true;handler.removeCallbacks(stopTask);handler.postDelayed(stopTask,15000);}
    catch(RuntimeException e){stop();}
    changed.run();
   });
   next.setOnErrorListener((failed,what,extra)->{if(player==failed){stop();changed.run();}return true;});
   handler.postDelayed(stopTask,20000);
   next.prepareAsync();
   return "主手机已收到响铃请求";
  }catch(Exception e){stop();return "主手机无法启动响铃";}
 }

 void stop(){
  handler.removeCallbacks(stopTask);
  MediaPlayer current=player;
  player=null;
  playing=false;
  notifyStopped=null;
  if(current!=null)current.release();
 }
}
