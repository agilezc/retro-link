package org.retrolink.host;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.util.*;

/** One primary action, two secondary choices. Transport results drive all states. */
@SuppressLint({"MissingPermission","UnspecifiedRegisterReceiverFlag"})
public class MainActivity extends Activity {
 private static final int PAPER=0xffeeeee8,INK=0xff20201e,MUTED=0xff777871,RED=0xffd73729;
 private Home home;private Button main,more;private PopupWindow menu;private Dialog sheet;
 private Typeface body,dots;private boolean registered,pending,external,asking;private String notice="",peerName="选择手机";
 private final Handler handler=new Handler();
 private final Runnable repaint=new Runnable(){public void run(){refresh();}};
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){notice="";refresh();}};
 protected void onCreate(Bundle saved){super.onCreate(saved);
  body=Typeface.createFromAsset(getAssets(),"retro-sans.ttf");dots=Typeface.createFromAsset(getAssets(),"retro-dots.ttf");
  getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(PAPER);
  frame.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets insets){v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;}});
  home=new Home();frame.addView(home,new FrameLayout.LayoutParams(-1,-1));
  more=new Button(this);more.setText("⋮");more.setTextSize(24);more.setContentDescription("更多");more.setBackgroundColor(Color.TRANSPARENT);more.setTextColor(INK);more.setPadding(0,0,0,0);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.RIGHT);mp.topMargin=dp(16);mp.rightMargin=dp(16);frame.addView(more,mp);more.setOnClickListener(v->showMenu());
  main=new Button(this);main.setTypeface(body);main.setTextSize(18);main.setAllCaps(false);main.setMinHeight(0);main.setMinimumHeight(0);main.setPadding(0,0,0,0);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(212),dp(52),Gravity.TOP|Gravity.CENTER_HORIZONTAL);frame.addView(main,bp);home.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)main.getLayoutParams();p.topMargin=Math.round(home.getHeight()*.79f);main.setLayoutParams(p);});main.setOnClickListener(v->primary());setContentView(frame);refresh();
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private boolean allowed(String permission){return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 private boolean nearby(){return Build.VERSION.SDK_INT<31||allowed(Manifest.permission.BLUETOOTH_CONNECT);}
 private boolean music(){String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");if(enabled!=null)for(String value:enabled.split(":")){ComponentName c=ComponentName.unflattenFromString(value);if(new ComponentName(this,MediaAccess.class).equals(c))return true;}return false;}
 private String address(){return getSharedPreferences("link",0).getString("peer","");}
 private String deviceName(){if(!nearby())return "选择手机";try{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a!=null&&address().length()>0){String name=a.getRemoteDevice(address()).getName();if(name!=null)return name;}}catch(Exception ignored){}return "选择手机";}
 private boolean busy(){return !"idle".equals(LinkService.phase)&&!"failed".equals(LinkService.phase)&&!LinkService.connected;}
 private void primary(){notice="";if(LinkService.connected){confirmDisconnect();return;}if(busy()||pending){pending=false;stopService(new Intent(this,LinkService.class));refresh();return;}pending=true;advance();}
 private void advance(){if(!pending||asking||external)return;BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null){pending=false;notice="不支持蓝牙";refresh();return;}
  if(!nearby()){if(getPreferences(0).getBoolean("denied_nearby",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)){external=true;startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())),16);}else{asking=true;requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},10);}return;}
  if(!a.isEnabled()){external=true;try{startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),11);}catch(Exception e){external=false;pending=false;notice="请打开蓝牙";}return;}
  Set<BluetoothDevice> paired=a.getBondedDevices();boolean found=false;for(BluetoothDevice d:paired)if(d.getAddress().equals(address()))found=true;
  if(!found){if(paired.size()==1){savePeer(paired.iterator().next());}else{choosePhone();return;}}
  if(!music()&&!getPreferences(0).getBoolean("asked_music",false)){getPreferences(0).edit().putBoolean("asked_music",true).apply();openMusic();return;}
  if(!calendarAccess()&&!getPreferences(0).getBoolean("asked_calendar",false)){getPreferences(0).edit().putBoolean("asked_calendar",true).apply();asking=true;requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},12);return;}
  if(Build.VERSION.SDK_INT>=33&&!allowed(Manifest.permission.POST_NOTIFICATIONS)&&!getPreferences(0).getBoolean("asked_notifications",false)){getPreferences(0).edit().putBoolean("asked_notifications",true).apply();asking=true;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},13);return;}
  pending=false;dismissSheet();try{startForegroundService(new Intent(this,LinkService.class));}catch(Exception e){notice="连接未启动";}refresh();handler.postDelayed(repaint,150);
 }
 private void savePeer(BluetoothDevice device){getSharedPreferences("link",0).edit().putString("peer",device.getAddress()).apply();}
 private void refreshService(){if(!"idle".equals(LinkService.phase)&&!"failed".equals(LinkService.phase))startService(new Intent(this,LinkService.class).setAction("refresh"));}
 private void openMusic(){external=true;try{startActivityForResult(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),14);}catch(Exception e){external=false;pending=false;notice="请在系统设置授权";refresh();}}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);external=false;if(request==11&&result!=RESULT_OK){pending=false;notice="蓝牙未开启";}if(request==16&&!nearby()){pending=false;notice="需要蓝牙授权";}if(request==15){BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null||!nearby()||a.getBondedDevices().isEmpty()){pending=false;choosePhone();return;}}if(pending)advance();else if((request==14||request==16||request==17||request==18))showGrants();refreshService();NoteDelivery.update(this);refresh();}
 public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);asking=false;if(request==10&&!nearby()){pending=false;getPreferences(0).edit().putBoolean("denied_nearby",true).apply();notice="需要蓝牙授权";}if(pending)advance();else if(request>=10)showGrants();if(request==12&&!calendarAccess())getPreferences(0).edit().putBoolean("denied_calendar",true).apply();refreshService();NoteDelivery.update(this);refresh();}
 private GradientDrawable shape(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setTypeface(body);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 private void showMenu(){if(menu!=null&&menu.isShowing()){menu.dismiss();return;}LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(18),dp(5),dp(18),dp(5));GradientDrawable bg=shape(PAPER,18);bg.setStroke(dp(1),0xffc9cac2);rows.setBackground(bg);
  menuRow(rows,"选择手机",R.drawable.ic_device,()->choosePhone());View line=new View(this);line.setBackgroundColor(0xffd2d3cb);rows.addView(line,new LinearLayout.LayoutParams(-1,1));menuRow(rows,"授权",R.drawable.ic_settings,()->showGrants());
  if(new NoteStore(this).count(false)>0){View rule=new View(this);rule.setBackgroundColor(0xffd2d3cb);rows.addView(rule,new LinearLayout.LayoutParams(-1,1));menuRow(rows,"转交微信",R.drawable.ic_device,()->startActivity(new Intent(this,NoteRelayActivity.class)));}
  menuRow(rows,"试听最新录音",R.drawable.ic_device,()->playLatestVoice());
  menu=new PopupWindow(rows,dp(228),-2,true);menu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));menu.setOutsideTouchable(true);menu.setOnDismissListener(()->refresh());menu.setElevation(0);menu.showAsDropDown(more,-dp(184),-dp(4));enter(rows,.94f);
 }
 private void playLatestVoice(){
  byte[] audio=new NoteStore(this).latestVoice();
  if(audio==null){Toast.makeText(this,"还没有录音笔记",Toast.LENGTH_SHORT).show();return;}
  java.io.File file=null;
  android.media.MediaPlayer player=new android.media.MediaPlayer();
  try{
   file=java.io.File.createTempFile("voice-preview",".3gp",getCacheDir());
   try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(audio);}
   try(java.io.FileInputStream in=new java.io.FileInputStream(file)){player.setDataSource(in.getFD());}
   android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("试听最新录音")
     .setPositiveButton("关闭",null).create();
   dialog.setOnDismissListener(d->player.release());
   player.setOnCompletionListener(p->dialog.dismiss());
   player.setOnErrorListener((p,what,extra)->{dialog.dismiss();return true;});
   player.prepare();
   player.start();
   dialog.show();
  }catch(Exception e){player.release();Toast.makeText(this,"录音无法播放",Toast.LENGTH_SHORT).show();}
  finally{if(file!=null)file.delete();}
 }
 private void menuRow(LinearLayout rows,String label,int icon,final Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setFocusable(true);row.setClickable(true);row.setBackground(selectable());ImageView image=new ImageView(this);image.setImageResource(icon);row.addView(image,new LinearLayout.LayoutParams(dp(23),dp(23)));TextView t=text(label,16,INK);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(14);row.addView(t,tp);row.addView(text("›",20,MUTED));rows.addView(row,new LinearLayout.LayoutParams(-1,dp(64)));row.setOnClickListener(v->{menu.dismiss();action.run();});}
 private android.graphics.drawable.Drawable selectable(){android.util.TypedValue v=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,v,true);return getDrawable(v.resourceId);}
 private LinearLayout sheet(String title){dismissSheet();if(menu!=null)menu.dismiss();sheet=new Dialog(this);sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(26),dp(16),dp(26),dp(28));root.setBackground(shape(PAPER,26));LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("←");back.setTextSize(24);back.setBackgroundColor(Color.TRANSPARENT);back.setPadding(0,0,0,0);back.setContentDescription("返回");heading.addView(back,new LinearLayout.LayoutParams(dp(40),dp(48)));heading.addView(text(title,19,INK));root.addView(heading);back.setOnClickListener(v->{pending=false;dismissSheet();refresh();});sheet.setContentView(root);Window w=sheet.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=.2f;w.setAttributes(p);}sheet.setOnCancelListener(d->{pending=false;refresh();});sheet.setOnDismissListener(d->refresh());return root;}
 private void displaySheet(LinearLayout root){sheet.show();Window w=sheet.getWindow();if(w!=null)w.setLayout(-1,-2);enter(root,1f);}
 private void dismissSheet(){if(sheet!=null){sheet.dismiss();sheet=null;}}
 private void divider(LinearLayout root){View v=new View(this);v.setBackgroundColor(0xffd2d3cb);root.addView(v,new LinearLayout.LayoutParams(-1,1));}
 private void choosePhone(){LinearLayout root=sheet("选择手机");if(!nearby()){Button grant=button("授权蓝牙",()->{dismissSheet();asking=true;requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},10);});root.addView(grant,new LinearLayout.LayoutParams(-1,dp(48)));displaySheet(root);return;}
  BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a!=null&&a.isEnabled()){ArrayList<BluetoothDevice> devices=new ArrayList<>(a.getBondedDevices());Collections.sort(devices,(x,y)->String.valueOf(x.getName()).compareToIgnoreCase(String.valueOf(y.getName())));
   // Bound the sheet to three visible devices; page rather than forcing a scroll.
   if(devices.isEmpty()){TextView empty=text("未配对手机",15,MUTED);empty.setPadding(0,dp(18),0,dp(18));root.addView(empty);}else devicePage(root,devices,0);
  }
  Button pair=button("配对其他手机 ↗",()->{external=true;dismissSheet();startActivityForResult(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),15);});pair.setTextColor(MUTED);root.addView(pair,new LinearLayout.LayoutParams(-1,dp(48)));displaySheet(root);
 }
 private void devicePage(LinearLayout parent,ArrayList<BluetoothDevice> devices,int page){LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);parent.addView(group);fillDevices(group,devices,page);}
 private void fillDevices(LinearLayout group,ArrayList<BluetoothDevice> devices,int page){group.removeAllViews();for(int n=page*3;n<Math.min(devices.size(),page*3+3);n++){final BluetoothDevice d=devices.get(n);divider(group);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setFocusable(true);row.setClickable(true);row.setBackground(selectable());ImageView image=new ImageView(this);image.setImageResource(R.drawable.ic_device);row.addView(image,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView name=text(d.getName()==null?d.getAddress():d.getName(),16,INK);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=dp(14);row.addView(name,np);row.addView(text(address().equals(d.getAddress())?"●":"○",20,address().equals(d.getAddress())?RED:MUTED));group.addView(row,new LinearLayout.LayoutParams(-1,dp(76)));row.setOnClickListener(v->{boolean changed=!address().equals(d.getAddress());if(changed)stopService(new Intent(this,LinkService.class));savePeer(d);dismissSheet();if(pending)advance();refresh();});}
  if(devices.size()>3){LinearLayout nav=new LinearLayout(this);group.addView(nav);Button prev=button("←",()->fillDevices(group,devices,page-1)),next=button("→",()->fillDevices(group,devices,page+1));prev.setEnabled(page>0);next.setEnabled((page+1)*3<devices.size());nav.addView(prev,new LinearLayout.LayoutParams(0,dp(42),1));nav.addView(next,new LinearLayout.LayoutParams(0,dp(42),1));}}
 private Button button(String label,final Runnable action){Button b=new Button(this);b.setTypeface(body);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(INK);b.setBackground(selectable());b.setOnClickListener(v->action.run());return b;}
 private boolean calendarAccess(){return allowed(Manifest.permission.READ_CALENDAR)&&allowed(Manifest.permission.WRITE_CALENDAR);}
 private void showGrants(){LinearLayout root=sheet("授权");grantRow(root,"附近设备",nearby(),()->{asking=true;if(Build.VERSION.SDK_INT>=31&&!shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)&&getPreferences(0).getBoolean("denied_nearby",false)){external=true;asking=false;startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())),16);}else requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},10);});grantRow(root,"音乐访问",music(),()->openMusic());grantRow(root,"勿扰访问",getSystemService(android.app.NotificationManager.class).isNotificationPolicyAccessGranted(),()->{external=true;startActivityForResult(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),18);});grantRow(root,"日历读写",calendarAccess(),()->{if(getPreferences(0).getBoolean("denied_calendar",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)){external=true;startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())),16);}else{asking=true;requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},12);}});if(Build.VERSION.SDK_INT>=33)grantRow(root,"速记通知",allowed(Manifest.permission.POST_NOTIFICATIONS),()->{if(getPreferences(0).getBoolean("asked_notifications",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){external=true;startActivityForResult(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()),17);}else{getPreferences(0).edit().putBoolean("asked_notifications",true).apply();asking=true;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},13);}});displaySheet(root);}
 private void grantRow(LinearLayout root,String label,boolean granted,final Runnable action){divider(root);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);PulseDot dot=new PulseDot(granted);row.addView(dot,new LinearLayout.LayoutParams(dp(26),dp(26)));row.addView(text(label,16,INK),new LinearLayout.LayoutParams(0,-2,1));row.addView(text(granted?"已就绪":"去授权 ›",13,granted?MUTED:RED));root.addView(row,new LinearLayout.LayoutParams(-1,dp(66)));if(!granted){row.setFocusable(true);row.setClickable(true);row.setBackground(selectable());row.setOnClickListener(v->{dismissSheet();action.run();});}}
 private void confirmDisconnect(){LinearLayout root=sheet("断开连接");Button stop=button("断开",()->{pending=false;stopService(new Intent(this,LinkService.class));dismissSheet();refresh();});stop.setBackground(shape(INK,26));stop.setTextColor(PAPER);root.addView(stop,new LinearLayout.LayoutParams(-1,dp(52)));displaySheet(root);}
 private boolean motion(){return Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0;}
 private void enter(View view,float scale){if(!motion())return;AnimationSet set=new AnimationSet(true);set.addAnimation(new AlphaAnimation(0,1));if(scale<1)set.addAnimation(new ScaleAnimation(scale,1,scale,1,Animation.RELATIVE_TO_SELF,1,Animation.RELATIVE_TO_SELF,0));else set.addAnimation(new TranslateAnimation(0,0,dp(18),0));set.setDuration(180);set.setInterpolator(new DecelerateInterpolator());view.startAnimation(set);}
 private void refresh(){if(home==null)return;peerName=deviceName();boolean connected=LinkService.connected,busy=busy()||pending;main.setText(connected?"●":busy?"取消":"failed".equals(LinkService.phase)?"重试":"连接");main.setContentDescription(connected?"已连接，断开连接":main.getText());main.setTextColor(connected?RED:PAPER);main.setBackground(shape(connected?Color.TRANSPARENT:busy?RED:INK,26));home.invalidate();}
 protected void onResume(){super.onResume();ChatGptRelay.attach(this);IntentFilter f=new IntentFilter(LinkService.EVENT);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);registered=true;NoteDelivery.update(this);refresh();}
 protected void onPause(){if(menu!=null)menu.dismiss();handler.removeCallbacks(repaint);home.removeCallbacks(home.animate);if(registered){unregisterReceiver(receiver);registered=false;}ChatGptRelay.detach(this);super.onPause();}
 protected void onDestroy(){handler.removeCallbacksAndMessages(null);dismissSheet();super.onDestroy();}
 private class PulseDot extends View {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean granted;final Runnable tick=()->invalidate();
  PulseDot(boolean allowed){super(MainActivity.this);granted=allowed;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  protected void onDraw(Canvas c){float alpha=granted||!motion()?1f:.4f+.6f*(float)((Math.sin(SystemClock.uptimeMillis()/260d)+1)/2);paint.setColor(granted?INK:RED);paint.setAlpha(Math.round(alpha*255));paint.setStyle(granted?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(dp(1));c.drawCircle(dp(4),getHeight()/2f,dp(granted?2.5f:4),paint);removeCallbacks(tick);if(!granted&&registered&&motion())postDelayed(tick,70);}
  protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
 }
 private class Home extends View {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private boolean wasConnected;private long settledAt;private float lastGap=34,startGap=34;final Runnable animate=()->invalidate();
  Home(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
  private void label(Canvas c,String s,float x,float y,float size,Typeface face,int color,boolean center){p.setTypeface(face);p.setTextSize(size);p.setColor(color);if(center)x-=p.measureText(s)/2;c.drawText(s,x,y,p);}
  protected void onDraw(Canvas c){float scale=getWidth()/390f,h=getHeight()/scale; c.save();c.scale(scale,scale);c.drawColor(PAPER);label(c,"RETRO LINK",26,49,22,dots,INK,false);p.setColor(0xffd2d3cb);p.setStrokeWidth(1);c.drawLine(26,81,364,81,p);
   boolean connected=LinkService.connected,loading=busy()||pending;if(connected&&!wasConnected){settledAt=SystemClock.uptimeMillis();startGap=lastGap;}wasConnected=connected;float settle=motion()?Math.min(1f,(SystemClock.uptimeMillis()-settledAt)/320f):1f;float cy=h*.4f;float phase=motion()?SystemClock.uptimeMillis()%1600/1600f:0;
   for(int i=0;i<72;i++){double a=i*Math.PI/36-Math.PI/2;p.setColor(connected?INK:loading&&(i/72f-phase+1)%1<.18?INK:0xffd2d3cb);c.drawCircle(195+(float)Math.cos(a)*112,cy+(float)Math.sin(a)*112,1.5f,p);}
   float gap=connected?startGap*(float)Math.pow(1-settle,3):loading&&motion()?27+6*(float)Math.sin(phase*Math.PI*2):34;lastGap=gap;for(int sign=-1;sign<=1;sign+=2)for(int y=-3;y<=3;y++)for(int x=-2;x<=2;x++)if(Math.abs(y)==3||Math.abs(x)==2){p.setColor(connected?RED:INK);c.drawCircle(195+sign*gap+x*5,cy+y*5,1,p);}
   String status=connected?"LINKED":loading?"LINKING":"failed".equals(LinkService.phase)?"RETRY":"READY";label(c,status,195,h*.625f,30,dots,INK,true);String name=peerName;while(name.length()>2){p.setTypeface(body);p.setTextSize(15);if(p.measureText(name)<=330)break;name=name.substring(0,name.length()-2)+"…";}label(c,name,195,h*.67f,15,body,MUTED,true);if(notice.length()>0)label(c,notice,195,h*.9f,13,body,MUTED,true);else if("waiting".equals(LinkService.phase))label(c,"等待手机",195,h*.9f,13,body,MUTED,true);
   setContentDescription(peerName+"，"+(connected?"已连接":loading?"连接中":"未连接"));c.restore();removeCallbacks(animate);if((loading||(connected&&settle<1))&&registered&&motion()&&(menu==null||!menu.isShowing())&&(sheet==null||!sheet.isShowing()))postDelayed(animate,32);
  }
 }
}
