package org.retrolink.host;
import android.app.*;
import android.content.*;
import java.lang.ref.WeakReference;
/** Foreground-only experiment. Does not submit a prompt or inspect answers. */
final class ChatGptRelay {
 private static WeakReference<Activity> foreground=new WeakReference<Activity>(null);
 static void attach(Activity activity){foreground=new WeakReference<Activity>(activity);}
 static void detach(Activity activity){if(foreground.get()==activity)foreground.clear();}
 static Intent intent(String text){return new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.openai.chatgpt").putExtra(Intent.EXTRA_TEXT,text);}
 static String dispatch(String text){
  if(text==null || text.trim().length()==0 || text.length()>2000)return "请输入1–2000字，未转交";
  Activity a=foreground.get();if(a==null || a.isFinishing())return "请先在 Nothing 前台打开 Retro Link 主机，再手动重试";
  if(!a.getSharedPreferences("link",0).getBoolean("chatgpt",false))return "请在 Nothing 开启 ChatGPT 文字实验";
  KeyguardManager lock=(KeyguardManager)a.getSystemService(Context.KEYGUARD_SERVICE);
  if(lock!=null && lock.isKeyguardLocked())return "本轮实验需 Nothing 解锁并显示 Retro Link 主机，未转交";
  try{a.startActivity(intent(text));return "已调用 ChatGPT 接收入口，请在 Nothing 查看文字是否填入；尚未自动发送";}
  catch(ActivityNotFoundException e){return "未找到 ChatGPT 文字接收入口，请确认已安装官方 App";}
  catch(SecurityException e){return "系统未允许打开 ChatGPT，未确认转交";}
 }
}
