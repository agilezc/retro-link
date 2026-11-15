package org.retrolink.host;
import android.app.Notification;
import android.app.RemoteInput;
import android.os.Build;
import java.util.*;
/** Inspect public actions only. Never execute PendingIntents during detection. */
final class ReplyDetector {
 static final class Result {
  Notification.Action action;
  String source="none";
  int ordinary,wearable,inputs,freeForm,choicesOnly,authRequired,missingIntent,immutable;
  boolean wearableReadError;
  String summary(){return "普通操作 "+ordinary+" · 手表操作 "+wearable+"\n输入项 "+inputs+" · 自由输入 "+freeForm+" · 仅预设回复 "+choicesOnly+"\n需解锁操作 "+authRequired+" · 无执行入口 "+missingIntent+" · 不可填入回复 "+immutable+(wearableReadError?"\n手表扩展读取失败":"")+"\n"+(action==null?"未发现可直接回复入口":"可回复入口："+("wearable".equals(source)?"手表扩展":"普通通知"));}
 }
 static Result inspect(Notification n){
  Result r=new Result();
  if(n.actions!=null){r.ordinary=n.actions.length;for(Notification.Action a:n.actions)scan(a,"ordinary",r);}
  try{List<Notification.Action> actions=new Notification.WearableExtender(n).getActions();r.wearable=actions.size();for(Notification.Action a:actions)scan(a,"wearable",r);}catch(RuntimeException e){r.wearableReadError=true;}
  return r;
 }
 private static void scan(Notification.Action a,String source,Result r){
  if(a==null)return;
  boolean auth=Build.VERSION.SDK_INT>=31 && a.isAuthenticationRequired();
  if(auth)r.authRequired++;
  boolean immutable=Build.VERSION.SDK_INT>=31 && a.actionIntent!=null && a.actionIntent.isImmutable();if(immutable)r.immutable++;
  if(a.actionIntent==null)r.missingIntent++;
  boolean free=false;
  RemoteInput[] inputs=a.getRemoteInputs();
  if(inputs!=null)for(RemoteInput input:inputs){
   if(input==null)continue;r.inputs++;
   if(input.getAllowFreeFormInput()){free=true;r.freeForm++;}
   else if(input.getChoices()!=null && input.getChoices().length>0)r.choicesOnly++;
  }
  if(r.action==null && free && !auth && !immutable && a.actionIntent!=null){r.action=a;r.source=source;}
 }
}
