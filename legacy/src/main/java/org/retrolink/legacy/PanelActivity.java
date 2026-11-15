package org.retrolink.legacy;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.view.*;import org.json.*;import java.text.*;import java.util.*;
import android.widget.Toast;
/** Shared 854x480 design grid for keyboard and touch. */
@android.annotation.SuppressLint("MissingPermission")
public class PanelActivity extends KeySoundActivity {
 private Panel panel;private String screen;private int page=0,focus=0;private JSONObject detail;private String message="";
 private JSONObject phone=new JSONObject();
 private String phoneRequest="",phoneMessage="";
 private final Runnable phoneTimeout=new Runnable(){public void run(){phoneMessage="未收到主手机回执";phoneRequest="";panel.invalidate();}};
 private boolean phonePage(){return "phone".equals(screen);}
 private void requestPhone(String action){
  if(!LinkService.connected){phoneMessage="请先连接 Nothing";panel.invalidate();return;}
  if(!"phone_status_request".equals(action)){
   phoneRequest=java.util.UUID.randomUUID().toString();
   phoneMessage="正在等待主手机…";
   clockHandler.removeCallbacks(phoneTimeout);
   clockHandler.postDelayed(phoneTimeout,8000);
  }
  startService(new Intent(this,LinkService.class).setAction(action).putExtra("id",phoneRequest));
  panel.invalidate();
 }
 private String calendarRequest="",calendarResult="";
 private final Runnable calendarTimeout=new Runnable(){public void run(){
  calendarResult="未收到保存回执，请先刷新核对";
  calendarRequest="";
  panel.invalidate();
 }};
 private void editAgenda(JSONObject item){
  if(!LinkService.connected || calendarRequest.length()>0){Toast.makeText(this,LinkService.connected?"正在保存":"请先连接 Nothing",Toast.LENGTH_SHORT).show();return;}
  try{AgendaEditor.show(this,item,new JSONObject(LinkService.latestCalendar),new AgendaEditor.Save(){
   public void submit(JSONObject request){
    calendarRequest=request.optString("id");calendarResult="正在保存…";
    clockHandler.postDelayed(calendarTimeout,15000);
    startService(new Intent(PanelActivity.this,LinkService.class).setAction("calendar_write").putExtra("data",request.toString()));
    panel.invalidate();
   }
  });}catch(JSONException e){Toast.makeText(this,"请先刷新日程",Toast.LENGTH_SHORT).show();}
 }
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(i.getBooleanExtra("clock_sync",false)){clockHandler.removeCallbacks(tick);clockHandler.post(tick);}if(i.hasExtra("data"))try{JSONObject j=new JSONObject(i.getStringExtra("data"));if("music".equals(screen)&&"ack".equals(j.optString("type")))message=j.optString("result");
 if(phonePage()&&"phone_status".equals(j.optString("type"))){phone=j;clockHandler.removeCallbacks(tick);clockHandler.post(tick);}
 if(phonePage()&&"phone_status_ack".equals(j.optString("type"))&&phoneRequest.equals(j.optString("id"))){
  phoneRequest="";clockHandler.removeCallbacks(phoneTimeout);phoneMessage=j.optString("result");
 }
 if("calendar_ack".equals(j.optString("type"))&&calendarRequest.equals(j.optString("id"))){
  calendarRequest="";clockHandler.removeCallbacks(calendarTimeout);calendarResult=j.optString("result");
  if(j.optBoolean("ok"))detail=null;
 }}catch(Exception ignored){}panel.invalidate();}};
 public void onCreate(Bundle b){super.onCreate(b);setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);getWindow().setFlags(1024,1024);if(android.os.Build.VERSION.SDK_INT>=19)getWindow().getDecorView().setSystemUiVisibility(5894);screen=getIntent().getStringExtra("screen");panel=new Panel();setContentView(panel);panel.setFocusableInTouchMode(true);panel.requestFocus();android.view.animation.TranslateAnimation enter=new android.view.animation.TranslateAnimation(android.view.animation.Animation.RELATIVE_TO_SELF,.035f,android.view.animation.Animation.RELATIVE_TO_SELF,0,android.view.animation.Animation.RELATIVE_TO_SELF,0,android.view.animation.Animation.RELATIVE_TO_SELF,0);enter.setDuration(180);enter.setInterpolator(new android.view.animation.DecelerateInterpolator());panel.startAnimation(enter);}
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") protected void onResume(){super.onResume();registerReceiver(receiver,new IntentFilter(LinkService.EVENT));if(phonePage()){try{phone=new JSONObject(LinkService.latestPhoneStatus);}catch(JSONException ignored){}requestPhone("phone_status_request");}panel.invalidate();clockHandler.post(tick);}
 protected void onPause(){clockHandler.removeCallbacks(tick);unregisterReceiver(receiver);super.onPause();}
 private void command(String op){if(!LinkService.connected){message="等待连接";panel.invalidate();return;}message="已发送";panel.invalidate();startService(new Intent(this,LinkService.class).setAction("command").putExtra("op",op));}
 private final Handler clockHandler=new Handler();private final Runnable tick=new Runnable(){public void run(){panel.invalidate();clockHandler.postDelayed(this,phonePage()&&LinkService.connected&&"充电中".equals(phone.optString("charging"))?120:"music".equals(screen)?1000:DeviceClock.untilNextMinute());}};
 public boolean dispatchKeyEvent(KeyEvent event){if("music".equals(screen)&&(event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_UP||event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_DOWN)){KeySound.onKey(event);if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)command(event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_UP?"volume_up":"volume_down");return true;}return super.dispatchKeyEvent(event);}
 protected void onDestroy(){clockHandler.removeCallbacksAndMessages(null);super.onDestroy();}
 private void back(){if(detail!=null){detail=null;panel.invalidate();}else finish();}
 public void onBackPressed(){back();}
 private class Panel extends View {
  Paint p=new Paint(3);Bitmap brand;Typeface dots;JSONArray items=new JSONArray();JSONObject state=new JSONObject();
  Panel(){super(PanelActivity.this);setFocusable(true);try{dots=Typeface.createFromAsset(getAssets(),"retro-dots.ttf");brand=BitmapFactory.decodeStream(getAssets().open("brand.png"));}catch(Exception ignored){}}
  void text(Canvas c,String s,float x,float y,int size,int color){p.setColor(color);p.setTextSize(size);p.setTypeface(s.matches("[A-Z0-9:/ ]+") && dots!=null?dots:UiFonts.body(PanelActivity.this));if(p.measureText(s)>824-x){int count=p.breakText(s,true,Math.max(0,824-x-p.measureText("…")),null);s=s.substring(0,count)+"…";}c.drawText(s,x,y,p);}
  void line(Canvas c,float x,float y,float xx,float yy){p.setColor(0xffc9cac2);p.setStrokeWidth(1);c.drawLine(x,y,xx,yy,p);}
  void button(Canvas c,String s,int x,int y,int w){p.setColor(0xff20201e);c.drawRoundRect(new RectF(x,y,x+w,y+38),19,19,p);p.setTypeface(UiFonts.body(PanelActivity.this));p.setTextSize(18);text(c,s,x+(w-p.measureText(s))/2,y+25,18,Color.WHITE);}
  String duration(long ms){if(ms<0)return "--:--";long seconds=ms/1000;return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
  void mediaIcon(Canvas c,int x,int y,boolean previous){p.setColor(0xff20201e);Path triangle=new Path();int direction=previous?-1:1;triangle.moveTo(x+direction*10,y);triangle.lineTo(x-direction*7,y-11);triangle.lineTo(x-direction*7,y+11);triangle.close();c.drawPath(triangle,p);c.drawRect(x+direction*13-2,y-11,x+direction*13+2,y+11,p);}
  String cut(String s,int n){return s.length()>n?s.substring(0,n-1)+"…":s;}
  String date(long value,String fmt,boolean all){SimpleDateFormat f=new SimpleDateFormat(fmt,Locale.CHINA);f.setTimeZone(TimeZone.getTimeZone(all?"UTC":zone));return f.format(new Date(value));}String zone="Asia/Shanghai";
  protected void onDraw(Canvas c){c.save();float scale=Math.min(getWidth()/854f,getHeight()/480f);c.translate((getWidth()-854*scale)/2,(getHeight()-480*scale)/2);c.scale(scale,scale);c.drawColor(0xffeeeee8);text(c,"←",30,41,27,0xff20201e);if(brand!=null){p.setColorFilter(new PorterDuffColorFilter(0xff20201e,PorterDuff.Mode.SRC_IN));c.drawBitmap(brand,68,19,p);p.setColorFilter(null);}text(c,DeviceClock.time(),744,40,21,0xff20201e);line(c,30,63,824,63);line(c,30,421,824,421);
   String peer="主手机";try{String address=getSharedPreferences("link",0).getString("peer","");String n=android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address).getName();if(n!=null)peer=n;}catch(Exception ignored){}String connection=cut(peer,24)+" · "+(LinkService.connected?"已连接":"已断开");p.setTypeface(UiFonts.body(PanelActivity.this));p.setTextSize(14);float width=p.measureText(connection);p.setColor(LinkService.connected?0xff77a43c:0xffd73729);c.drawCircle(824-width-11,450,3.5f,p);text(c,connection,824-width,455,14,0xff565952);
   if(phonePage()){
    text(c,"主手机状态",30,100,23,0xff20201e);
    p.setColor(0xffdadbd3);c.drawRoundRect(new RectF(30,115,476,202),12,12,p);
    p.setColor(0xffb8c9a5);c.save();c.clipRect(30,115,30+446*Math.max(0,Math.min(100,phone.optInt("battery",0)))/100f,202);c.drawRoundRect(new RectF(30,115,476,202),12,12,p);
    if(LinkService.connected&&"充电中".equals(phone.optString("charging"))){
     float phase=(SystemClock.uptimeMillis()%3200)/3200f;
     p.setColor(0x55f1ffe0);float glow=30+phase*446;c.drawCircle(glow,155,38,p);
     for(int i=0;i<7;i++){float x=40+i*59;float y=135+(float)(Math.sin(phase*6.28+i*1.7)*8)+(i%2)*35;p.setColor(0x99ffffff);c.drawCircle(x,y,1.8f,p);}
    }c.restore();
    String battery=phone.has("battery")?String.valueOf(phone.optInt("battery")):"--";
    text(c,battery,45,168,49,0xff20201e);
    float percentX=49+p.measureText(battery);
    String[] dotsPercent={"11001","11010","00100","01000","10110","00110"};
    for(int row=0;row<dotsPercent.length;row++)for(int col=0;col<5;col++)
     if(dotsPercent[row].charAt(col)=='1')c.drawCircle(percentX+col*4,145+row*4,1.3f,p);
    text(c,phone.optString("charging","等待数据"),45,188,14,0xff565952);
    p.setColor(0xffe2e3dd);c.drawRoundRect(new RectF(486,115,824,202),12,12,p);
    try{state=new JSONObject(LinkService.latestData);}catch(Exception ignored){}
    text(c,"NOW PLAYING",500,138,13,0xff777871);
    Bitmap thumbnail=LinkService.albumArt;
    RectF coverRect=new RectF(500,148,544,192);
    p.setColor(0xff20201e);c.drawRoundRect(coverRect,6,6,p);
    if(thumbnail!=null&&!thumbnail.isRecycled()){p.setFilterBitmap(true);c.drawBitmap(thumbnail,null,coverRect,p);}
    else text(c,"♫",510,179,24,Color.WHITE);
    text(c,cut(state.optString("title","等待音乐"),13),557,169,20,0xff20201e);
    text(c,cut(state.optString("artist",""),17),557,188,14,0xff777871);
    p.setColor(0xffe2e3dd);c.drawRoundRect(new RectF(30,212,824,316),12,12,p);
    line(c,288,224,288,304);line(c,546,224,546,304);
    gauge(c,55,264,"内存",phone.optLong("ramTotal"),phone.optLong("ramAvailable"));
    gauge(c,313,264,"存储",phone.optLong("storageTotal"),phone.optLong("storageAvailable"));
    int thermal=phone.optInt("thermal",-1);p.setColor(0xffc5c7bd);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);c.drawArc(new RectF(568,232,628,292),135,270,false,p);p.setColor(thermal<0?0xff999999:thermal<2?0xff77a43c:thermal<4?0xffd9a42d:0xffd73729);c.drawArc(new RectF(568,232,628,292),135,thermal<0?0:Math.min(270,(thermal+1)*38),false,p);p.setStyle(Paint.Style.FILL);
    text(c,"设备温控",644,248,14,0xff777871);text(c,thermal<0?"不可用":thermal==0?"正常":thermal<3?"轻度受热":"温控限制",644,271,18,0xff20201e);
    text(c,"系统温控状态",644,290,12,0xff777871);
    p.setColor(0xffd73729);c.drawRoundRect(new RectF(30,330,250,395),12,12,p);if(phone.optBoolean("ringing")){p.setColor(Color.WHITE);c.drawRoundRect(new RectF(72,353,90,371),2,2,p);}
    else WatchIcons.draw(c,1,71,351,21,Color.WHITE);
    text(c,phone.optBoolean("ringing")?"停止响铃":"找手机",102,370,20,Color.WHITE);
    int mode=phone.optBoolean("dnd")?0:phone.optInt("ringer",-1);
    p.setColor(0xffd1d2ca);c.drawRoundRect(new RectF(260,330,824,395),12,12,p);
    if(mode>=0&&mode<=2){
     int x=263+(2-mode)*188;p.setColor(0xff20201e);
     c.drawRoundRect(new RectF(x,333,x+182,392),12,12,p);
    }
    String[] modes={"响铃","振动","静音"};
    for(int i=0;i<3;i++){int x=260+i*188;int tint=mode==2-i?Color.WHITE:0xff20201e;
     WatchIcons.draw(c,i==0?1:i==1?3:2,x+51,351,21,tint);
     text(c,modes[i],x+81,370,20,tint);}
    text(c,phoneMessage.length()>0?phoneMessage:phone.optBoolean("dnd")?"主手机勿扰已开启":"",30,415,13,0xff565952);
   }else if("music".equals(screen)){try{state=new JSONObject(LinkService.latestData);}catch(Exception ignored){}text(c,"NOW PLAYING",310,116,15,0xff777871);text(c,cut(state.optString("title","等待音乐"),20),310,167,28,0xff20201e);text(c,cut(state.optString("artist"),30),310,204,18,0xff777871);
    Bitmap cover=LinkService.albumArt;
    if(cover!=null && !cover.isRecycled()){
     p.setColor(Color.WHITE);p.setColorFilter(null);p.setFilterBitmap(true);
     int side=Math.min(cover.getWidth(),cover.getHeight());int left=(cover.getWidth()-side)/2,top=(cover.getHeight()-side)/2;
     c.drawBitmap(cover,new Rect(left,top,left+side,top+side),new RectF(30,103,260,333),p);
    }else{p.setColor(0xff20201e);c.drawRoundRect(new RectF(30,103,260,333),8,8,p);for(int y=115;y<324;y+=6)for(int x=42;x<250;x+=6){if(y>230-35*Math.sin(x*.04)){p.setColor(0xffeeeee8);c.drawCircle(x,y,1.2f,p);}}}
    long duration=state.optLong("duration_ms",-1),position=state.optLong("position_ms",-1);if(position>=0&&state.optBoolean("playing"))position+=Math.max(0,SystemClock.elapsedRealtime()-LinkService.latestStateAt);if(duration>0)position=Math.min(position,duration);
    p.setColor(0xffa4a59c);c.drawRect(310,232,824,235,p);if(duration>0&&position>=0){float x=310+514*Math.min(1f,position/(float)duration);p.setColor(0xff20201e);c.drawRect(310,232,x,235,p);p.setColor(0xffd73729);c.drawCircle(x,233.5f,6,p);}text(c,duration(position),310,263,18,0xff20201e);text(c,duration(duration),760,263,18,0xff20201e);
    mediaIcon(c,390,320,true);mediaIcon(c,744,320,false);p.setColor(0xffd73729);c.drawCircle(565,320,30,p);p.setColor(Color.WHITE);if(state.optBoolean("playing")){c.drawRoundRect(new RectF(556,309,562,331),2,2,p);c.drawRoundRect(new RectF(568,309,574,331),2,2,p);}else{Path play=new Path();play.moveTo(559,308);play.lineTo(577,320);play.lineTo(559,332);play.close();c.drawPath(play,p);}text(c,message.length()>0?message:"← → 切歌 · 空格 播放/暂停",30,455,14,0xff565952);
   }else{try{JSONObject snapshot=new JSONObject(LinkService.latestCalendar);zone=snapshot.optString("zone","Asia/Shanghai");items=snapshot.optJSONArray("items");if(items==null)items=new JSONArray();message=snapshot.optString("error","");}catch(Exception ignored){}
    text(c,detail==null?"日程":"日程详情",30,110,24,0xff20201e);
    if(detail==null){button(c,"新增",640,81,83);text(c,"刷新",760,108,16,0xff777871);int pages=Math.max(1,(items.length()+1)/2);page=Math.min(page,pages-1);focus=Math.max(0,Math.min(focus,items.length()-page*2-1));if(items.length()==0)text(c,message.length()>0?message:"未来7天没有日程",30,205,18,0xff777871);
     for(int i=0;i<2;i++){JSONObject e=items.optJSONObject(page*2+i);if(e==null)continue;int y=150+i*105;if(focus==i){p.setColor(0xffdedfd8);c.drawRoundRect(new RectF(30,y,824,y+82),12,12,p);p.setColor(0xffd73729);c.drawRect(30,y+12,33,y+70,p);}text(c,date(e.optLong("begin"),"MM/dd",e.optBoolean("allDay")),60,y+23,14,0xff777871);text(c,e.optBoolean("allDay")?"全天":date(e.optLong("begin"),"HH:mm",false),60,y+59,25,0xff20201e);line(c,186,y+15,186,y+63);text(c,cut(e.optString("title"),26),215,y+32,22,0xff20201e);text(c,cut(e.optString("location"),35),215,y+62,16,0xff777871);}
     text(c,(page+1)+" / "+pages,760,394,14,0xff777871);text(c,calendarResult.length()>0?calendarResult:"← → 翻页 · Enter 详情 · N 新增",30,455,14,0xff565952);
    }else{boolean all=detail.optBoolean("allDay");text(c,all?"全天":date(detail.optLong("begin"),"HH:mm",false),50,205,36,0xff20201e);text(c,all?"全天日程":date(detail.optLong("end"),"MM-dd HH:mm",false),50,256,20,0xff777871);line(c,260,150,260,365);text(c,cut(detail.optString("title"),22),300,185,26,0xff20201e);text(c,"日期   "+date(detail.optLong("begin"),"MM月dd日 E",all),300,240,18,0xff20201e);text(c,"地点   "+cut(detail.optString("location"),24),300,285,18,0xff20201e);if(detail.optBoolean("editable"))button(c,"编辑",720,350,100);text(c,calendarResult.length()>0?calendarResult:detail.optBoolean("editable")?"E 编辑":"只读同步",30,455,14,0xff565952);}
   }c.restore();}
  void gauge(Canvas c,int x,int y,String name,long total,long available){
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xffc5c7bd);
   RectF rect=new RectF(x,y-30,x+60,y+30);c.drawArc(rect,135,270,false,p);
   long occupied=total>0?total-Math.max(0L,Math.min(total,available)):0;
   if(total>0){
    float fraction=occupied/(float)total;
    p.setColor(fraction<.6f?0xff77a43c:fraction<.85f?0xffd9a42d:0xffd73729);
    c.drawArc(rect,135,270*fraction,false,p);
   }
   p.setStyle(Paint.Style.FILL);
   text(c,name,x+75,y-16,14,0xff777871);
   text(c,total>0?"已占用 "+capacity(occupied):"等待数据",x+75,y+7,16,0xff20201e);
   if(total>0)text(c,"共 "+capacity(total),x+75,y+26,12,0xff777871);
  }
  String capacity(long bytes){
   String amount=String.format(Locale.US,"%.1f",bytes/1073741824.0);
   if(amount.endsWith(".0"))amount=amount.substring(0,amount.length()-2);
   return amount+" GB";
  }

  void select(){JSONObject e=items.optJSONObject(page*2+focus);if(e!=null)detail=e;invalidate();}
  public boolean onKeyDown(int key,KeyEvent e){if(phonePage()){
   if(key==66||key==23||key==62){if(e.getRepeatCount()==0)requestPhone(phone.optBoolean("ringing")?"phone_ring_stop":"phone_ring");return true;}
   return super.onKeyDown(key,e);
  }if("music".equals(screen)){if(key==21||key==22||key==62||key==23||key==66){command(key==21?"previous":key==22?"next":"toggle");return true;}}else{if(key==KeyEvent.KEYCODE_N&&detail==null){editAgenda(null);return true;}if(key==KeyEvent.KEYCODE_E&&detail!=null){editAgenda(detail);return true;}if(key==21||key==22){page=Math.max(0,Math.min((items.length()-1)/2,page+(key==22?1:-1)));detail=null;invalidate();return true;}if(key==19||key==20){focus=key==19?0:Math.min(1,Math.max(0,items.length()-page*2-1));invalidate();return true;}if(key==66||key==23){select();return true;}}return super.onKeyDown(key,e);}
  public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=1)return true;float scale=Math.min(getWidth()/854f,getHeight()/480f);float x=(e.getX()-(getWidth()-854*scale)/2)/scale,y=(e.getY()-(getHeight()-480*scale)/2)/scale;if(y<63&&x<68){back();return true;}if(phonePage()){
   if(y>=115&&y<=202&&x>=486){startActivity(new Intent(PanelActivity.this,PanelActivity.class).putExtra("screen","music"));return true;}
   if(y>=330&&y<=395){
    if(x>=30&&x<=250)requestPhone(phone.optBoolean("ringing")?"phone_ring_stop":"phone_ring");
    else if(x>=260&&x<824&&LinkService.connected){int mode=2-Math.min(2,(int)((x-260)/188));phoneRequest=java.util.UUID.randomUUID().toString();clockHandler.postDelayed(phoneTimeout,8000);startService(new Intent(PanelActivity.this,LinkService.class).setAction("phone_mode").putExtra("id",phoneRequest).putExtra("mode",mode));}
   }
   return true;
  }if("music".equals(screen)){if(y>=285&&y<=355){if(x>=340&&x<=440)command("previous");else if(x>=515&&x<=615)command("toggle");else if(x>=694&&x<=794)command("next");}}else if(detail!=null){if(x>=720&&y>=350&&y<=388&&detail.optBoolean("editable"))editAgenda(detail);}else{if(y>=81&&y<=119&&x>=640&&x<=723)editAgenda(null);else if(y<125&&x>730)startService(new Intent(PanelActivity.this,LinkService.class).setAction("calendar"));else if((y>=150&&y<=232)||(y>=255&&y<=337)){focus=y<250?0:1;select();}else if(y>421&&x<400){page=Math.max(0,Math.min((items.length()-1)/2,page+(x<170?-1:1)));}}invalidate();return true;}
 }
}
