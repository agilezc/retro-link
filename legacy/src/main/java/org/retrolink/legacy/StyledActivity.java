package org.retrolink.legacy;
import android.app.*;import android.os.*;import android.view.*;import android.view.animation.*;import android.widget.*;import android.graphics.Color;import android.content.*;
/** Shared visual system for the legacy screens; animations only run on navigation. */
public class StyledActivity extends KeySoundActivity {
 private final java.util.WeakHashMap<TextView,Float> sizes=new java.util.WeakHashMap<TextView,Float>();
 private TextView connection,clock;private LinearLayout footer;private boolean registered;
 private final Handler clockHandler=new Handler();private final Runnable tick=new Runnable(){public void run(){if(clock!=null)clock.setText(DeviceClock.time());clockHandler.postDelayed(this,DeviceClock.untilNextMinute());}};
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(i.getBooleanExtra("clock_sync",false)){clockHandler.removeCallbacks(tick);clockHandler.post(tick);}updateConnection();}};
 protected void onCreate(Bundle state){super.onCreate(state);setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);getWindow().setFlags(1024,1024);if(android.os.Build.VERSION.SDK_INT>=19)getWindow().getDecorView().setSystemUiVisibility(5894);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
 public void setContentView(View content){
  style(content);LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(0xffeeeee8);
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(px(30),0,px(30),0);
  TextView back=new TextView(this);back.setText("←");back.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(27));back.setTextColor(0xff20201e);back.setGravity(Gravity.CENTER);back.setFocusable(true);back.setContentDescription("返回");back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});bar.addView(back,new LinearLayout.LayoutParams(px(38),px(62)));
  ImageView brand=new ImageView(this);try{brand.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(getAssets().open("brand.png"),null));brand.setColorFilter(0xff20201e);}catch(Exception ignored){}brand.setScaleType(ImageView.ScaleType.FIT_START);bar.addView(brand,new LinearLayout.LayoutParams(px(174),px(27)));
  clock=new TextView(this);clock.setText(DeviceClock.time());try{clock.setTypeface(android.graphics.Typeface.createFromAsset(getAssets(),"retro-dots.ttf"));}catch(Exception ignored){}clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(21));clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);bar.addView(clock,new LinearLayout.LayoutParams(0,px(62),1));shell.addView(bar,new LinearLayout.LayoutParams(-1,px(62)));View topRule=new View(this);topRule.setBackgroundColor(0xffd2d3cb);shell.addView(topRule,new LinearLayout.LayoutParams(-1,1));
  shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));connection=new TextView(this);connection.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));connection.setTextColor(0xff565952);connection.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);connection.setSingleLine(true);connection.setEllipsize(android.text.TextUtils.TruncateAt.END);connection.setMaxWidth(px(310));View bottomRule=new View(this);bottomRule.setBackgroundColor(0xffd2d3cb);shell.addView(bottomRule,new LinearLayout.LayoutParams(-1,1));footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(px(30),0,px(30),0);View spacer=new View(this);footer.addView(spacer,new LinearLayout.LayoutParams(0,1,1));footer.addView(connection,new LinearLayout.LayoutParams(-2,px(58)));shell.addView(footer,new LinearLayout.LayoutParams(-1,px(58)));super.setContentView(shell);updateConnection();
  TranslateAnimation enter=new TranslateAnimation(Animation.RELATIVE_TO_SELF,.035f,Animation.RELATIVE_TO_SELF,0,Animation.RELATIVE_TO_SELF,0,Animation.RELATIVE_TO_SELF,0);enter.setDuration(180);enter.setInterpolator(new DecelerateInterpolator());content.startAnimation(enter);
 }
 private int px(int n){return UiMetrics.px(this,n);}
 protected int unit(int n){return dp(n);}
 private int dp(int n){return UiMetrics.unit(this,n);}
 protected void setFooterStatus(TextView status){if(footer==null)return;footer.removeViewAt(0);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setGravity(Gravity.CENTER_VERTICAL);status.setTextColor(0xff777871);footer.addView(status,0,new LinearLayout.LayoutParams(0,px(58),1));}
 private void style(View v){
  if(v instanceof TextView && v!=clock){TextView t=(TextView)v;Float original=sizes.get(t);if(original==null){original=UiMetrics.nominal(t);sizes.put(t,original);}UiMetrics.text(t,original);t.setTypeface(UiFonts.body(this));}
  if(v instanceof EditText){v.setBackgroundColor(0xffeeeee8);((TextView)v).setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.NORMAL));((TextView)v).setTextColor(0xff20201e);((TextView)v).setHintTextColor(0xff777871);v.setPadding(0,dp(10),0,dp(10));}
  else if(v instanceof Button){Button b=(Button)v;boolean row="row".equals(b.getTag());boolean primary="primary".equals(b.getTag());b.setBackgroundResource(primary?R.drawable.retro_primary:row?R.drawable.retro_row:R.drawable.retro_button);UiMetrics.text(b,13);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(10),0,dp(10),0);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_pressed},new int[]{android.R.attr.state_focused},new int[]{}},new int[]{Color.WHITE,row?0xff20201e:Color.WHITE,primary?Color.WHITE:0xff20201e}));}

  if((v instanceof LinearLayout || v instanceof ScrollView) && !"setting-row".equals(v.getTag()))v.setBackgroundColor(0xffeeeee8);
  if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)style(group.getChildAt(i));}
 }
 @android.annotation.SuppressLint("MissingPermission")
 private void updateConnection(){if(connection==null)return;String name="主手机";try{String address=getSharedPreferences("link",0).getString("peer","");if(address.length()>0){String n=android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address).getName();if(n!=null)name=n;}}catch(Exception ignored){}android.text.SpannableString label=new android.text.SpannableString("●  "+name+" · "+(LinkService.connected?"已连接":"已断开"));label.setSpan(new android.text.style.ForegroundColorSpan(LinkService.connected?0xff77a43c:0xffd73729),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);connection.setText(label);}
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 protected void onResume(){super.onResume();style(findViewById(android.R.id.content));registerReceiver(receiver,new IntentFilter(LinkService.EVENT));registered=true;updateConnection();clockHandler.removeCallbacks(tick);clockHandler.post(tick);}
 protected void onPause(){clockHandler.removeCallbacks(tick);if(registered){unregisterReceiver(receiver);registered=false;}super.onPause();}
 public void finish(){super.finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
}
