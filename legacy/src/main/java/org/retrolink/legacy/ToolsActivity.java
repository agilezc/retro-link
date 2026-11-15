package org.retrolink.legacy;
import android.os.*;import android.content.*;import android.bluetooth.*;import android.view.*;import android.widget.*;import java.util.*;
/** Three settings per page; touch and keyboard share the same focus targets. */
@android.annotation.SuppressLint("MissingPermission")
public class ToolsActivity extends StyledActivity {
 private int page;private LinearLayout list;private TextView status;private Button previous,next;
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(8),dp(20),dp(6));
  LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);root.addView(heading);label(heading,"设置",16,0xff20201e);status=label(heading,"",10,0xff777871);status.setPadding(dp(14),0,0,0);
  list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout nav=new LinearLayout(this);root.addView(nav);previous=nav(nav,"← 上一页",new Runnable(){public void run(){page=Math.max(0,page-1);showPage();}});next=nav(nav,"下一页 →",new Runnable(){public void run(){page=Math.min(1,page+1);showPage();}});setContentView(root);showPage();
 }
 private void row(String name,String detail,String icon,final Runnable action){LinearLayout item=new LinearLayout(this);item.setTag("setting-row");item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(10),0,dp(10),0);item.setFocusable(true);item.setClickable(true);item.setBackgroundResource(R.drawable.retro_row);
  ImageView image=new ImageView(this);try{image.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(getAssets().open(("settings".equals(icon)?"settings-round":"camera".equals(icon)?"camera-round":"note".equals(icon)?"note-round":icon)+".png"),null));image.setColorFilter(0xff20201e);}catch(Exception ignored){}item.addView(image,new LinearLayout.LayoutParams(dp(24),dp(24)));
  LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,dp(12),0);label(words,name,14,0xff20201e);label(words,detail,10,0xff777871);item.addView(words,new LinearLayout.LayoutParams(0,-2,1));label(item,"›",20,0xff777871);list.addView(item,new LinearLayout.LayoutParams(-1,0,1));View line=new View(this);line.setBackgroundColor(0xffd2d3cb);list.addView(line,new LinearLayout.LayoutParams(-1,1));item.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});
 }
 private void showPage(){list.removeAllViews();if(page==0){
  row("连接设备","选择已配对的主手机","settings",new Runnable(){public void run(){selectPeer();}});
  row("蓝牙设置","系统配对与连接","settings",new Runnable(){public void run(){startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));}});
  row("照片备份",PhotoSync.enabled(this)?"自动发送新拍照片":"已关闭","camera",new Runnable(){public void run(){PhotoSync.options(ToolsActivity.this,ToolsActivity.this::showPage);}});
 }else{
  row("恢复连接","重新连接已选主手机","settings",new Runnable(){public void run(){startService(new Intent(ToolsActivity.this,LinkService.class));status.setText("正在连接…");}});
  row("实验功能","消息查看 / 文字转交","note",new Runnable(){public void run(){RetroDialog.show(ToolsActivity.this,"实验功能","辅助功能，不代表锁屏下可自动回复。",new String[]{"消息（仅查看）","ChatGPT 文字转交"},-1,new RetroDialog.Choice(){public void choose(int n){startActivity(new Intent(ToolsActivity.this,n==0?MessagesActivity.class:ChatGptActivity.class));}});}});
  row("断开连接","暂停主手机连接","settings",new Runnable(){public void run(){getSharedPreferences("link",0).edit().putBoolean("connection_enabled",false).commit();stopService(new Intent(ToolsActivity.this,LinkService.class));status.setText("已断开");}});
 }previous.setEnabled(page>0);next.setEnabled(page<1);}
 private void selectPeer(){BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null||!a.isEnabled()){status.setText("请先打开蓝牙");return;}final ArrayList<BluetoothDevice> devices=new ArrayList<BluetoothDevice>(a.getBondedDevices());if(devices.size()==0){status.setText("请先配对两台手机");return;}String[] labels=new String[devices.size()];String current=getSharedPreferences("link",0).getString("peer","");int selected=-1;for(int n=0;n<labels.length;n++){String name=devices.get(n).getName();labels[n]=name==null?devices.get(n).getAddress():name;if(current.equals(devices.get(n).getAddress()))selected=n;}RetroDialog.show(this,"选择主手机","选择后建立连接",labels,selected,new RetroDialog.Choice(){public void choose(int n){stopService(new Intent(ToolsActivity.this,LinkService.class));getSharedPreferences("link",0).edit().putString("peer",devices.get(n).getAddress()).commit();startService(new Intent(ToolsActivity.this,LinkService.class));status.setText("正在连接…");}});}
 public boolean onKeyDown(int key,KeyEvent e){if(key==21||key==22){page=Math.max(0,Math.min(1,page+(key==21?-1:1)));showPage();return true;}return super.onKeyDown(key,e);}
 private int dp(int n){return UiMetrics.unit(this,n);}
 private TextView label(LinearLayout p,String text,int size,int color){TextView t=new TextView(this);t.setText(text);UiMetrics.text(t,size);t.setTextColor(color);t.setTypeface(UiFonts.body(this));t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);p.addView(t);return t;}
 private Button nav(LinearLayout p,String text,final Runnable action){Button b=new Button(this);b.setText(text);b.setTag("row");b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});p.addView(b,new LinearLayout.LayoutParams(0,dp(32),1));return b;}
}
