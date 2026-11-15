package org.retrolink.host;
import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.*;
import org.json.*;
import java.util.*;
/** Only WeChat notifications are exported, after an explicit local opt-in. */
public class MediaAccess extends NotificationListenerService {
 interface Sink { void deliver(JSONObject message); }
 static volatile Sink sink;
 static volatile String latestDiagnostics="尚未检测微信通知";
 private static MediaAccess instance;
 private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<String,Entry>();
 private static class Entry { StatusBarNotification sbn; String token; Notification.Action action; String source; }
 private boolean enabled(){return getSharedPreferences("link",0).getBoolean("wechat",false);}
 public void onListenerConnected(){instance=this;sync();}
 public void onListenerDisconnected(){instance=null;entries.clear();emit("notification_reset",null);}
 public void onDestroy(){if(instance==this)instance=null;entries.clear();super.onDestroy();}
 public void onNotificationPosted(StatusBarNotification sbn){if(enabled() && "com.tencent.mm".equals(sbn.getPackageName()))publish(sbn);}
 public void onNotificationRemoved(StatusBarNotification sbn){Entry old=entries.remove(sbn.getKey());if(old!=null)emit("notification_remove",old);}
 static void snapshot(){if(instance!=null)instance.sync();}
 private void sync(){entries.clear();emit("notification_reset",null);if(!enabled())return;try{
  StatusBarNotification[] all=getActiveNotifications();if(all==null)return;
  int count=0;for(int i=all.length-1;i>=0 && count<20;i--)if("com.tencent.mm".equals(all[i].getPackageName())){publish(all[i]);count++;}
 }catch(SecurityException ignored){}}
 private void publish(StatusBarNotification sbn){
  Notification n=sbn.getNotification();
  ReplyDetector.Result detection=ReplyDetector.inspect(n);
  latestDiagnostics=detection.summary();
  if((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0)latestDiagnostics+="\n此条为汇总通知";
  android.util.Log.i("RetroLink","WeChat reply diagnostics: "+latestDiagnostics.replace('\n',';'));
  try{JSONObject diagnostics=new JSONObject();diagnostics.put("type","reply_diagnostics");diagnostics.put("summary",latestDiagnostics);Sink target=sink;if(target!=null)target.deliver(diagnostics);}catch(Exception ignored){}
  if((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0)return;
  Entry e=new Entry();e.sbn=sbn;e.token=UUID.randomUUID().toString();e.action=detection.action;e.source=detection.source;
  entries.put(sbn.getKey(),e);while(entries.size()>20){String key=entries.keySet().iterator().next();Entry removed=entries.remove(key);emit("notification_remove",removed);}
  android.util.Log.i("RetroLink","WeChat notification detected; replyable="+(e.action!=null));
  emit("notification",e);
 }
 private String text(CharSequence s){if(s==null)return "";String v=s.toString();return v.substring(0,Math.min(v.length(),1500));}
 private void emit(String type,Entry e){try{JSONObject j=new JSONObject();j.put("type",type);
  if(e!=null){j.put("key",e.sbn.getKey());j.put("token",e.token);
   if("notification".equals(type)){Bundle b=e.sbn.getNotification().extras;j.put("title",text(b.getCharSequence(Notification.EXTRA_TITLE)));CharSequence content=b.getCharSequence(Notification.EXTRA_BIG_TEXT);if(content==null)content=b.getCharSequence(Notification.EXTRA_TEXT);j.put("body",text(content));j.put("replyable",e.action!=null);j.put("replySource",e.source);j.put("time",e.sbn.getPostTime());}}
  Sink target=sink;if(target!=null)target.deliver(j);
 }catch(Exception ignored){}}
 static String reply(String token,String text){
  MediaAccess self=instance;if(self==null || !self.enabled())return "微信同步未开启或通知权限已断开";
  if(text==null || text.trim().length()==0 || text.length()>1000)return "请输入1–1000字回复";
  Entry chosen=null;for(Entry e:self.entries.values())if(e.token.equals(token)){chosen=e;break;}
  if(chosen==null || chosen.action==null)return "通知已更新或不支持回复，请重新选择";
  try{boolean active=false;for(StatusBarNotification n:self.getActiveNotifications())if(n.getKey().equals(chosen.sbn.getKey()) && n.getPostTime()==chosen.sbn.getPostTime())active=true;
   if(!active)return "通知已失效，未发送";
   Intent fill=new Intent();Bundle results=new Bundle();for(RemoteInput r:chosen.action.getRemoteInputs())if(r.getAllowFreeFormInput())results.putCharSequence(r.getResultKey(),text);
   RemoteInput.addResultsToIntent(chosen.action.getRemoteInputs(),fill,results);if(Build.VERSION.SDK_INT>=28)RemoteInput.setResultsSource(fill,RemoteInput.SOURCE_FREE_FORM_INPUT);
   chosen.action.actionIntent.send(self,0,fill);self.entries.remove(chosen.sbn.getKey());self.emit("notification_remove",chosen);
   return "已提交微信，送达状态请在主手机确认";
  }catch(Exception e){return "微信回复入口失效，未确认发送，请查看主手机";}
 }
}
