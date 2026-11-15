package org.retrolink.legacy;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
/** Separate editor preserves normal keyboard navigation and spaces. */
public class ChatGptActivity extends StyledActivity {
 private EditText input;
 private TextView status;
 private Button send;
 private String requestId="";
 private final Handler handler=new Handler();
 private final Runnable timeout=new Runnable(){public void run(){status.setText("暂未收到转交回执，请先查看 Nothing。文字仍保留，未自动重试。");send.setEnabled(true);}};
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){accept(i.getStringExtra("data"));}};
 private void accept(String value){try{JSONObject j=new JSONObject(value);if("chatgpt_ack".equals(j.optString("type")) && requestId.equals(j.optString("id"))){handler.removeCallbacks(timeout);status.setText(j.optString("result"));send.setEnabled(true);}}catch(Exception ignored){}}
 public void onCreate(Bundle saved){super.onCreate(saved);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=dp(18);root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(0xffeeeee8);scroll.addView(root);setContentView(scroll);
  TextView heading=new TextView(this);heading.setText("ChatGPT 文字");heading.setTextSize(24);root.addView(heading);
  TextView help=new TextView(this);help.setText("先让 Nothing 显示 Retro Link 主机页，并开启 ChatGPT 文字实验。\n此版只转交文字，暂不自动提问或回传回答。");help.setTextSize(14);root.addView(help);
  input=new EditText(this);input.setHint("使用全键盘输入问题");input.setTextSize(18);input.setMinLines(4);input.setGravity(Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});root.addView(input,new LinearLayout.LayoutParams(-1,-2));
  send=new Button(this);send.setText("交给 Nothing 的 ChatGPT");root.addView(send);
  status=new TextView(this);status.setTextSize(14);root.addView(status);
  if(saved!=null){input.setText(saved.getString("draft"));requestId=saved.getString("request");if(requestId==null)requestId="";status.setText(saved.getString("status"));}else status.setText("准备输入");
  send.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   String text=input.getText().toString();if(text.trim().length()==0){input.setError("请输入文字");return;}
   if(!LinkService.connected){status.setText("请先返回主页建立蓝牙连接，文字未转交");return;}
   requestId=java.util.UUID.randomUUID().toString();send.setEnabled(false);status.setText("正在交给 Nothing…");handler.removeCallbacks(timeout);handler.postDelayed(timeout,15000);
   startService(new Intent(ChatGptActivity.this,LinkService.class).setAction("chatgpt").putExtra("id",requestId).putExtra("text",text));
  }});
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 protected void onResume(){super.onResume();registerReceiver(receiver,new IntentFilter(LinkService.EVENT));accept(LinkService.latestChatAck);}
 protected void onPause(){unregisterReceiver(receiver);super.onPause();}
 protected void onSaveInstanceState(Bundle out){out.putString("draft",input.getText().toString());out.putString("request",requestId);out.putString("status",status.getText().toString());super.onSaveInstanceState(out);}
 protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
