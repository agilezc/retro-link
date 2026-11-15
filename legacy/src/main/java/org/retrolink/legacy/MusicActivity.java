package org.retrolink.legacy;
import android.app.*;
import android.os.*;
import android.content.*;
import android.bluetooth.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
// Bluetooth access is gated by MainActivity permissions; legacy APK targets pre-31 Android.
@android.annotation.SuppressLint("MissingPermission")
public class MusicActivity extends StyledActivity {
 private TextView status,title,artist,result;
 private Button play;
 private boolean registered=false;
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  if(i.hasExtra("status")){String s=i.getStringExtra("status");status.setText(s);if(s.indexOf("中断")>=0 || s.indexOf("正在连接")>=0)result.setText("连接恢复后可继续控制");}
  if(i.hasExtra("data"))try{JSONObject j=new JSONObject(i.getStringExtra("data"));
   if("state".equals(j.optString("type"))){title.setText(j.optString("title"));artist.setText(j.optString("artist"));play.setText(j.optBoolean("playing")?"暂停":"播放");}
   else if("ack".equals(j.optString("type")))result.setText(j.optString("result"));
  }catch(Exception e){result.setText("状态读取失败");}
 }};
 public void onCreate(Bundle b){setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);super.onCreate(b);
  android.widget.FrameLayout scroll=new android.widget.FrameLayout(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(4),dp(14),dp(4));root.setBackgroundColor(0xffeeeee8);scroll.addView(root);setContentView(scroll);


  status=label(root,"选择配对手机后开始连接",14,0xff777871);
  title=label(root,"等待音乐",20,0xff20201e);title.setPadding(0,dp(2),0,dp(2));
  artist=label(root,"",13,0xff777871);
  LinearLayout controls=new LinearLayout(this);root.addView(controls);
  button(controls,"上一首",new Runnable(){public void run(){command("previous");}});
  play=button(controls,"播放",new Runnable(){public void run(){command("toggle");}});
  button(controls,"下一首",new Runnable(){public void run(){command("next");}});
  if(false)controls.setVisibility(View.GONE);
  result=label(root,false?"声音由主手机或其耳机播放":"左右切歌 · 空格播放/暂停",14,0xff777871);

 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 private TextView label(LinearLayout p,String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(2),0,dp(2));p.addView(v);return v;}
 private Button button(LinearLayout p,String text,final Runnable r){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){r.run();}});p.addView(b,p.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,dp(40),1):new LinearLayout.LayoutParams(-1,dp(40)));return b;}
 private boolean permissions(){ return true;}
 private void selectPeer(){if(!permissions())return;BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null||!a.isEnabled()){status.setText("请先打开蓝牙");return;}
  final ArrayList<BluetoothDevice> list=new ArrayList<BluetoothDevice>(a.getBondedDevices());if(list.size()==0){status.setText("请先在蓝牙设置中配对两台手机");return;}
  String[] labels=new String[list.size()];for(int k=0;k<labels.length;k++)labels[k]=list.get(k).getName()+"\n"+list.get(k).getAddress();
  new AlertDialog.Builder(this).setTitle("选择另一台手机").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int index){
   stopService(new Intent(MusicActivity.this,LinkService.class));getSharedPreferences("link",0).edit().putString("peer",list.get(index).getAddress()).apply();status.setText("已选择："+list.get(index).getName());
  }}).show();
 }
 private void startLink(){if(!permissions())return;
  Intent i=new Intent(this,LinkService.class);startService(i);
 }
 private void command(String op){if(!false){Intent i=new Intent(this,LinkService.class);i.setAction("command");i.putExtra("op",op);startService(i);}}
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 branch uses the legacy overload.
 protected void onResume(){super.onResume();IntentFilter f=new IntentFilter(LinkService.EVENT);registerReceiver(receiver,f); registered=true;receiver.onReceive(this,new Intent().putExtra("status",LinkService.latestStatus));if(LinkService.latestData.length()>0)receiver.onReceive(this,new Intent().putExtra("data",LinkService.latestData));}
 protected void onPause(){if(registered){unregisterReceiver(receiver);registered=false;}super.onPause();}
 public boolean onKeyDown(int key,KeyEvent e){if(!false && LinkService.connected && (key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_SPACE)){
  if(e.getRepeatCount()==0)command(key==KeyEvent.KEYCODE_DPAD_LEFT?"previous":key==KeyEvent.KEYCODE_DPAD_RIGHT?"next":"toggle");return true;
 }return super.onKeyDown(key,e);}
 public boolean onKeyUp(int key,KeyEvent e){if(!false && LinkService.connected && (key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_SPACE))return true;return super.onKeyUp(key,e);}
}
