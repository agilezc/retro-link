package org.retrolink.legacy;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
/** Separate editor preserves normal keyboard navigation and spaces. */
public class NoteActivity extends StyledActivity {
 private EditText input;
 private TextView status;
 private Button send;
 private View voiceButton;
 private TextView voiceInfo;
 private long startedAt;
 private final Runnable recordingTick=new Runnable(){public void run(){
  if(!voice.recording())return;
  int seconds=(int)((SystemClock.elapsedRealtime()-startedAt)/1000);
  voiceInfo.setText(String.format(java.util.Locale.US,"录音中  00:%02d",seconds));
  handler.postDelayed(this,250);
 }};
 private void redrawVoice(){
  voiceButton.setContentDescription(voice.recording()?"停止录音":"录音");
  voiceButton.invalidate();
  voiceInfo.setText(voice.recording()?"录音中  00:00":voice.hasAudio()?"录音草稿":"语音笔记");
 }
 private VoiceDraft voice;
 private final Runnable voiceLimit=new Runnable(){public void run(){finishVoice();}};
 private void finishVoice(){
  if(!voice.recording())return;
  handler.removeCallbacks(voiceLimit);
  status.setText(voice.finish()?"录音草稿已保存在索尼":"录音未保存，请至少说一秒再停止");
  handler.removeCallbacks(recordingTick);redrawVoice();
 }
 private void startVoice(){
  try{
   voice.start();startedAt=SystemClock.elapsedRealtime();redrawVoice();handler.post(recordingTick);status.setText("录音中 · 最长 30 秒");
   handler.postDelayed(voiceLimit,30000);
  }catch(Exception e){status.setText("无法使用索尼麦克风");}
 }
 private void voiceAction(){
  if(voice.recording()){finishVoice();return;}
  if(!voice.hasAudio()){startVoice();return;}
  RetroDialog.show(this,"录音草稿","录音保存在索尼",new String[]{"试听","重新录音","删除"},-1,new RetroDialog.Choice(){
   public void choose(int index){
    if(index==1)startVoice();
    else if(index==2){voice.delete();redrawVoice();status.setText("录音草稿已删除");}
    else try{voice.play();status.setText("正在试听");}catch(Exception e){status.setText("无法播放录音草稿");}
   }
  });
 }
 private String requestId="",shownAck="",sentText="";private TextView count;
 private final Handler handler=new Handler();
 private final Runnable timeout=new Runnable(){public void run(){status.setText("暂未收到回执，草稿已保留，可重试。");send.setEnabled(true);}};
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){accept(i.getStringExtra("data"));}};
 private void accept(String value){try{JSONObject j=new JSONObject(value);if(requestId.length()>0 && "note_ack".equals(j.optString("type")) && requestId.equals(j.optString("id"))){handler.removeCallbacks(timeout);status.setText(!(j.optString("result").startsWith("已保存")||j.optString("result").startsWith("已接收")||j.optString("result").startsWith("已转交"))||sentText.equals(input.getText().toString())?j.optString("result"):"上一条已接收，当前修改未发送");send.setEnabled(true);if((j.optString("result").startsWith("已保存")||j.optString("result").startsWith("已接收")||j.optString("result").startsWith("已转交")) && !requestId.equals(shownAck)){shownAck=requestId;String label="✓ "+j.optString("result");Toast toast=Toast.makeText(this,label,Toast.LENGTH_SHORT);TextView receipt=new TextView(this);receipt.setText(label);receipt.setTextSize(14);receipt.setTextColor(android.graphics.Color.WHITE);receipt.setPadding(dp(18),dp(10),dp(18),dp(10));android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(0xff20201e);shape.setCornerRadius(dp(16));receipt.setBackgroundDrawable(shape);toast.setView(receipt);toast.show();}}}catch(Exception ignored){}}
 public void onCreate(Bundle saved){setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);super.onCreate(saved);FrameLayout scroll=new FrameLayout(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=dp(20);root.setPadding(pad,dp(8),pad,dp(6));root.setBackgroundColor(0xffeeeee8);scroll.addView(root);setContentView(scroll);
  TextView heading=new TextView(this);heading.setText("速记");heading.setTextSize(16);root.addView(heading);
  TextView help=new TextView(this);help.setText("输入文字，保存到主手机。");help.setTextSize(11);help.setVisibility(View.GONE);root.addView(help);
  input=new EditText(this);input.setHint("随手记下想法");input.setTextSize(13);input.setMinLines(2);input.setGravity(Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);
  android.graphics.drawable.GradientDrawable card=new android.graphics.drawable.GradientDrawable();
  card.setColor(0xffe4e5de);card.setCornerRadius(dp(12));sheet.setBackgroundDrawable(card);
  sheet.setPadding(dp(10),dp(4),dp(10),dp(4));root.addView(sheet,new LinearLayout.LayoutParams(-1,0,1));
  input.setBackgroundColor(android.graphics.Color.TRANSPARENT);sheet.addView(input,new LinearLayout.LayoutParams(-1,0,1));
  count=new TextView(this);count.setTextSize(11);count.setTextColor(0xff777871);
  send=new Button(this);send.setText("发送笔记");send.setTag("primary");LinearLayout action=new LinearLayout(this);action.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,-2);actionParams.topMargin=dp(7);sheet.addView(action,actionParams);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=dp(14);voice=new VoiceDraft(this);
  voiceButton=new View(this){
   private final android.graphics.Paint paint=new android.graphics.Paint(3);
   protected void onDraw(android.graphics.Canvas canvas){
    float center=getWidth()/2f,size=Math.min(getWidth(),getHeight());
    paint.setColor(isFocused()?0xffab281f:0xffd73729);
    canvas.drawCircle(center,getHeight()/2f,size/2f-1,paint);
    float glyph=size*.54f,x=center-glyph/2,y=(getHeight()-glyph)/2;
    if(voice.recording()){paint.setColor(-1);canvas.drawRoundRect(new android.graphics.RectF(x,y,x+glyph,y+glyph),2,2,paint);}
    else WatchIcons.draw(canvas,0,x,y,glyph,-1);
   }
   protected void onFocusChanged(boolean focused,int direction,android.graphics.Rect previous){
    super.onFocusChanged(focused,direction,previous);invalidate();
   }
  };
  voiceButton.setFocusable(true);
  action.addView(voiceButton,new LinearLayout.LayoutParams(dp(38),dp(38)));
  voiceButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){voiceAction();}});
  voiceInfo=new TextView(this);voiceInfo.setTextSize(11);voiceInfo.setPadding(dp(8),0,0,0);
  action.addView(voiceInfo,new LinearLayout.LayoutParams(0,-2,1));redrawVoice();
  action.addView(count,cp);action.addView(send,new LinearLayout.LayoutParams(dp(92),dp(38)));
  status=new TextView(this);status.setTextSize(11);setFooterStatus(status);
  if(saved!=null){shownAck=saved.getString("ack");if(shownAck==null)shownAck="";sentText=saved.getString("sent");if(sentText==null)sentText="";input.setText(saved.getString("draft"));requestId=saved.getString("request");if(requestId==null)requestId="";status.setText(saved.getString("status"));}else {input.setText(getPreferences(0).getString("draft",""));status.setText("准备输入");}
  count.setText(input.length()+" / 2000");input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){count.setText(s.length()+" / 2000");}public void afterTextChanged(android.text.Editable e){}});
  send.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   String text=input.getText().toString();if(voice.recording()){status.setText("请先停止录音");return;}if(!voice.hasAudio()&&text.trim().length()==0){input.setError("请输入文字");return;}
   if(!LinkService.connected){status.setText("请先返回主页建立蓝牙连接，文字未转交");return;}
   String audio="";
   try{if(voice.hasAudio())audio=android.util.Base64.encodeToString(voice.read(),android.util.Base64.NO_WRAP);}
   catch(java.io.IOException e){status.setText("无法读取录音，草稿已保留");return;}
   sentText=text;
   requestId=java.util.UUID.nameUUIDFromBytes((audio.length()==0?text:audio+"\n"+text).getBytes(java.nio.charset.Charset.forName("UTF-8"))).toString();
   send.setEnabled(false);status.setText("正在发送笔记…");handler.removeCallbacks(timeout);handler.postDelayed(timeout,15000);
   startService(new Intent(NoteActivity.this,LinkService.class).setAction("note").putExtra("id",requestId).putExtra("text",text).putExtra("audio",audio));
  }});
 }
 public boolean dispatchKeyEvent(KeyEvent event){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.isAltPressed()){KeySound.onKey(event);if(event.getRepeatCount()==0&&send.isEnabled())send.performClick();return true;}return super.dispatchKeyEvent(event);}
 private int dp(int n){return UiMetrics.unit(this,n);}
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 protected void onResume(){super.onResume();registerReceiver(receiver,new IntentFilter(LinkService.EVENT));accept(LinkService.latestNoteAck);}
 protected void onPause(){finishVoice();voice.stopPlayback();getPreferences(0).edit().putString("draft",input.getText().toString()).commit();unregisterReceiver(receiver);super.onPause();}
 protected void onSaveInstanceState(Bundle out){out.putString("ack",shownAck);out.putString("sent",sentText);out.putString("draft",input.getText().toString());out.putString("request",requestId);out.putString("status",status.getText().toString());super.onSaveInstanceState(out);}
 protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
