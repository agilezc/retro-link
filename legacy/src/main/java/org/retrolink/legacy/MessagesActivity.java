package org.retrolink.legacy;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import org.json.*;
import java.util.*;
/** Native message screen: keyboard events belong to the editor, never to music. */
public class MessagesActivity extends StyledActivity {
 private LinearLayout list;
 private TextView status;
 private final HashSet<String> submitted=new HashSet<String>();
 private final Handler handler=new Handler();
 private Runnable timeout;
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  try{JSONObject j=new JSONObject(i.getStringExtra("data"));String type=j.optString("type");
   if(type.startsWith("notification"))render();
   if("reply_ack".equals(type)){if(timeout!=null)handler.removeCallbacks(timeout);status.setText(j.optString("result"));}
  }catch(Exception ignored){}
 }};
 public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,16);root.setBackgroundColor(0xffeeeee8);scroll.addView(root);setContentView(scroll);
  TextView heading=new TextView(this);heading.setText("微信消息");heading.setTextSize(24);root.addView(heading);
  status=new TextView(this);status.setText("通知同步 · 不含聊天历史");status.setTextSize(14);root.addView(status);
  list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);render();
 }
 private void render(){list.removeAllViews();ArrayList<String> all;
  synchronized(LinkService.messages){all=new ArrayList<String>(LinkService.messages.values());}
  if(all.isEmpty()){TextView empty=new TextView(this);empty.setText("暂无微信通知\n请在 Nothing 开启微信同步，再接收一条新消息。");empty.setPadding(0,24,0,24);list.addView(empty);return;}
  Collections.reverse(all);
  for(String value:all)try{final JSONObject j=new JSONObject(value);Button row=new Button(this);row.setText(j.optString("title")+"\n"+j.optString("body")+"\n"+(j.optBoolean("replyable")?"可回复":"仅可查看 · 微信未提供可用回复入口"));row.setTextSize(16);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){open(j);}});list.addView(row);}catch(Exception ignored){}
 }
 private void open(final JSONObject j){
  if(!j.optBoolean("replyable")){new AlertDialog.Builder(this).setTitle(j.optString("title")).setMessage(j.optString("body")+"\n\n此通知无法快捷回复，请在主手机处理。").setPositiveButton("知道了",null).show();return;}
  if(submitted.contains(j.optString("token"))){status.setText("此通知已提交过回复，请在主手机确认或等待新消息");return;}
  final EditText editor=new EditText(this);editor.setHint("使用全键盘输入回复");editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);editor.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
  final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("回复："+j.optString("title")).setView(editor).setNegativeButton("取消",null).setPositiveButton("发送",null).create();
  dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   String text=editor.getText().toString();if(text.trim().length()==0){editor.setError("请输入回复");return;}
   if(!LinkService.connected){editor.setError("蓝牙未连接，内容未发送");return;}
   submitted.add(j.optString("token"));Intent i=new Intent(MessagesActivity.this,LinkService.class).setAction("reply").putExtra("token",j.optString("token")).putExtra("text",text);startService(i);
   status.setText("正在提交回复…");dialog.dismiss();timeout=new Runnable(){public void run(){status.setText("尚未收到回执，请先在主手机确认，勿重复发送");}};handler.postDelayed(timeout,15000);
  }});}});dialog.show();editor.requestFocus();
 }
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 protected void onResume(){super.onResume();registerReceiver(receiver,new IntentFilter(LinkService.EVENT));render();}
 protected void onPause(){unregisterReceiver(receiver);super.onPause();}
 protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
