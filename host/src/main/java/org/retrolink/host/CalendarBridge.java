package org.retrolink.host;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.*;
import android.net.Uri;
import android.os.Handler;
import android.provider.CalendarContract;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Calendar snapshots and serialized writes. No event titles or locations are written to logs or disk. */
final class CalendarBridge {
 interface Sink { void deliver(JSONObject value); }
 private final Context context;
 private final Handler handler;
 private final Sink sink;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private boolean closed=false, busy=false, again=false, observing=false;
 private final ContentObserver observer;
 private final Runnable changed=new Runnable(){public void run(){refresh();}};
 private final Runnable periodic=new Runnable(){public void run(){if(closed)return;refresh();handler.postDelayed(this,300000);}};
 CalendarBridge(Context c,Handler h,Sink s){context=c;handler=h;sink=s;observer=new ContentObserver(h){public void onChange(boolean self){handler.removeCallbacks(changed);handler.postDelayed(changed,1000);}};}
 void start(){handler.post(periodic);}
 void refresh(){
  if(closed)return;
  boolean allowed=context.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;
  if(allowed && !observing)try{context.getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI,true,observer);observing=true;}catch(SecurityException ignored){}
  if(!allowed && observing){context.getContentResolver().unregisterContentObserver(observer);observing=false;}
  if(busy){again=true;return;}busy=true;
  worker.execute(new Runnable(){public void run(){final JSONObject result=read();handler.post(new Runnable(){public void run(){busy=false;if(closed)return;sink.deliver(result);if(again){again=false;refresh();}}});}});
 }
 private final Map<String,JSONObject> receipts=new LinkedHashMap<String,JSONObject>();
 void write(final JSONObject request, final Sink reply){
  if(closed)return;
  worker.execute(new Runnable(){public void run(){
   String id=request.optString("id");
   JSONObject ack=receipts.get(id);
   if(ack==null){
    boolean ok=false;String message;
    try{
     if(!CalendarWrites.allowed(context))message="请在 Nothing 授权日历读写";
     else if(!id.matches("[a-f0-9-]{36}"))message="日程请求无效";
     else {ok=CalendarWrites.save(context,request);message=ok?"已保存到系统日历":"日程已变更或不可编辑，请刷新";}
    }catch(Exception e){message="系统日历保存失败";}
    ack=new JSONObject();
    try{ack.put("type","calendar_ack").put("id",id).put("ok",ok).put("result",message);}catch(JSONException ignored){}
    if(ok){receipts.put(id,ack);if(receipts.size()>100)receipts.remove(receipts.keySet().iterator().next());}
   }
   final JSONObject result=ack;
   handler.post(new Runnable(){public void run(){if(!closed){reply.deliver(result);if(result.optBoolean("ok"))refresh();}}});
  }});
 }
 private JSONObject read(){
  JSONObject j=new JSONObject();
  try{
   j.put("type","calendar");j.put("updated",System.currentTimeMillis());j.put("zone",TimeZone.getDefault().getID());
   if(context.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED){j.put("error","请在 Nothing 的 Retro Link 中授权日历读取");return j;}
   Calendar start=CalendarWindow.start(System.currentTimeMillis(),TimeZone.getDefault());
   Calendar end=(Calendar)start.clone();end.add(Calendar.DATE,7);
   // Expand query for UTC all-day dates; filter their actual local date overlap below.
   Uri.Builder uri=CalendarContract.Instances.CONTENT_URI.buildUpon();
   ContentUris.appendId(uri,start.getTimeInMillis()-86400000L);ContentUris.appendId(uri,end.getTimeInMillis()+86400000L);
   boolean writable=CalendarWrites.allowed(context);
   j.put("calendars",writable?CalendarWrites.calendars(context.getContentResolver()):new JSONArray());
   JSONArray items=new JSONArray();boolean more=false;
   String[] projection={"title","begin","end","allDay","eventLocation","event_id","calendar_id","rrule","rdate","original_id","original_sync_id","calendar_access_level"};
   try(Cursor c=context.getContentResolver().query(uri.build(),projection,"visible=1 AND deleted=0 AND (eventStatus IS NULL OR eventStatus!=2)",null,"begin ASC, end ASC")){
    if(c==null)throw new IllegalStateException("calendar unavailable");
    while(c.moveToNext()){
     boolean all=c.getInt(3)!=0;long begin=c.getLong(1),finish=c.getLong(2);
     long localBegin=all?CalendarWindow.localDay(begin,TimeZone.getDefault()):begin,localEnd=all?CalendarWindow.localDay(finish,TimeZone.getDefault()):finish;
     if(localBegin>=end.getTimeInMillis() || localEnd<=start.getTimeInMillis())continue;
     if(items.length()>=30){more=true;break;}
     JSONObject e=new JSONObject();e.put("title",cut(c.getString(0),100));e.put("begin",begin);e.put("end",finish);e.put("allDay",all);e.put("location",cut(c.getString(4),80));e.put("event",c.getLong(5));e.put("calendar",c.getLong(6));
     e.put("editable",writable && !all && c.isNull(7) && c.isNull(8) && c.isNull(9) && c.isNull(10)
       && c.getInt(11)>=500 && safe(c.getString(0)).length()<=100 && safe(c.getString(4)).length()<=80);
     items.put(e);
    }
   }
   j.put("items",items);j.put("more",more);
  }catch(SecurityException e){try{j.put("error","日历权限已撤销，请在 Nothing 重新授权");}catch(Exception ignored){}}
   catch(Exception e){try{j.put("error","系统日历读取失败，请稍后刷新");}catch(Exception ignored){}}
  return j;
 }
 private static String safe(String value){return value==null?"":value;}
 private static String cut(String s,int max){if(s==null)return "";int n=Math.min(s.length(),max);if(n>0 && Character.isHighSurrogate(s.charAt(n-1)))n--;return s.substring(0,n);}
 void close(){closed=true;handler.removeCallbacks(periodic);handler.removeCallbacks(changed);if(observing)context.getContentResolver().unregisterContentObserver(observer);worker.shutdownNow();}
}
