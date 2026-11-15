package org.retrolink.legacy;
import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Small native agenda; cache lives only in the link service process. */
public class AgendaActivity extends StyledActivity {
 private final Handler handler=new Handler();
 private final Runnable timeout=new Runnable(){public void run(){status.setText("刷新超时 · 请检查蓝牙和主手机日历授权，旧日程仍保留");}};
 private JSONObject snapshot;private int page=0;
 private TextView status;
 private LinearLayout rows;
 private boolean registered;
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(i.hasExtra("data"))try{JSONObject j=new JSONObject(i.getStringExtra("data"));if("calendar".equals(j.optString("type")))render(j);}catch(Exception ignored){}if(i.hasExtra("status") && !LinkService.connected)status.setText("连接已断开 · 下方为上次接收的日程");}};
 public void onCreate(Bundle state){setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);super.onCreate(state);android.widget.FrameLayout scroll=new android.widget.FrameLayout(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(2),dp(14),dp(2));root.setBackgroundColor(0xffeeeee8);scroll.addView(root);setContentView(scroll);
  text(root,"日程 · 未来7天",18,0xff20201e);status=text(root,"尚未同步",11,0xff777871);
  Button refresh=new Button(this);refresh.setText("刷新日程");LinearLayout nav=new LinearLayout(this);root.addView(nav);nav.addView(refresh,new LinearLayout.LayoutParams(0,dp(38),1));Button prev=new Button(this);prev.setText("上一页");nav.addView(prev,new LinearLayout.LayoutParams(0,dp(38),1));Button next=new Button(this);next.setText("下一页");nav.addView(next,new LinearLayout.LayoutParams(0,dp(38),1));prev.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(page>0){page--;if(snapshot!=null)render(snapshot);}}});next.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(snapshot!=null && snapshot.optJSONArray("items")!=null && page+1<snapshot.optJSONArray("items").length()){page++;render(snapshot);}}});refresh.setOnClickListener(new View.OnClickListener(){public void onClick(View v){request();}});
  rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);root.addView(rows);
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 private TextView text(LinearLayout root,String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));root.addView(t);return t;}
 private void request(){if(!LinkService.connected){status.setText("请先回主页连接 Nothing");return;}status.setText("正在刷新…");handler.removeCallbacks(timeout);handler.postDelayed(timeout,15000);startService(new Intent(this,LinkService.class).setAction("calendar"));}
 private String date(long value,String pattern,TimeZone zone){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.CHINA);f.setTimeZone(zone);return f.format(new Date(value));}
 private void render(JSONObject j){snapshot=j;handler.removeCallbacks(timeout);rows.removeAllViews();if(j.has("error")){status.setText(j.optString("error"));return;}
  TimeZone zone=TimeZone.getTimeZone(j.optString("zone","Asia/Shanghai"));
  status.setText((LinkService.connected?"蓝牙已连接":"离线缓存")+" · 更新 "+date(j.optLong("updated"),"MM-dd HH:mm",zone)+"");
  JSONArray items=j.optJSONArray("items");if(items==null)return;
  if(items.length()==0)text(rows,"未来7天没有日程\n仅显示 Nothing 系统日历中可见的事项",16,0xff777871);
  page=Math.max(0,Math.min(page,items.length()-1));for(int k=page;k<Math.min(page+1,items.length());k++){JSONObject e=items.optJSONObject(k);if(e==null)continue;boolean all=e.optBoolean("allDay");TimeZone tz=all?TimeZone.getTimeZone("UTC"):zone;long begin=e.optLong("begin"),end=e.optLong("end");
   String time=all?date(begin,"MM-dd",tz)+" 全天（至 "+date(end-1,"MM-dd",tz)+"）":date(begin,"MM-dd HH:mm",tz)+" — "+date(end,"MM-dd HH:mm",tz);
   text(rows,time,13,0xff777871);String title=e.optString("title");text(rows,title.length()==0?"未命名日程":title,15,0xff20201e);if(e.optString("location").length()>0)text(rows,e.optString("location"),14,0xff777871);
   View line=new View(this);line.setBackgroundColor(0xffdce2ea);rows.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  }
  if(j.optBoolean("more"))text(rows,"仅显示最早30项，请在主手机查看其余日程",13,0xff777871);
 }
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 protected void onResume(){super.onResume();registerReceiver(receiver,new IntentFilter(LinkService.EVENT));registered=true;if(LinkService.latestCalendar.length()>0)try{render(new JSONObject(LinkService.latestCalendar));}catch(Exception ignored){}request();}
 protected void onPause(){handler.removeCallbacks(timeout);if(registered){unregisterReceiver(receiver);registered=false;}super.onPause();}
}
