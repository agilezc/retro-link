package org.retrolink.host;
import android.app.*;
import android.content.*;
import android.os.*;
public class ChatGptRelayChecks extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle b=new Bundle();try{
  String text="中文输入\nLine 2 🐱";Intent i=ChatGptRelay.intent(text);
  if(!Intent.ACTION_SEND.equals(i.getAction()) || !"text/plain".equals(i.getType()) || !"com.openai.chatgpt".equals(i.getPackage()) || !text.equals(i.getStringExtra(Intent.EXTRA_TEXT)))throw new AssertionError("text intent");
  if(!ChatGptRelay.dispatch("").contains("未转交"))throw new AssertionError("empty input");
  if(!ChatGptRelay.dispatch(new String(new char[2001]).replace('\0','x')).contains("未转交"))throw new AssertionError("length limit");
  if(!ChatGptRelay.dispatch("test").contains("前台打开"))throw new AssertionError("foreground gate");
  b.putString("result","PASS: Unicode payload/explicit target, empty/oversize rejection, foreground gate; no activity launched");finish(Activity.RESULT_OK,b);
 }catch(Throwable e){b.putString("result","FAIL: "+e);finish(Activity.RESULT_CANCELED,b);}}
}
