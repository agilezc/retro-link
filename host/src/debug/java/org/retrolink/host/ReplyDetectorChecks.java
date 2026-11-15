package org.retrolink.host;
import android.app.*;
import android.content.*;
import android.os.*;
/** Constructs synthetic notifications; never posts them or invokes an action. */
public class ReplyDetectorChecks extends Instrumentation {
 private int checks;
 public void onCreate(Bundle args){super.onCreate(args);start();}
 private void expect(boolean value,String test){if(!value)throw new AssertionError(test);checks++;}
 private Notification.Builder builder(){return new Notification.Builder(getTargetContext(),"link").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Synthetic detector check");}
 private Notification.Action action(boolean free,boolean intent,boolean auth){
  PendingIntent pi=intent?PendingIntent.getBroadcast(getTargetContext(),91,new Intent("org.retrolink.host.TEST_UNUSED").setPackage(getTargetContext().getPackageName()),(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0)):null;
  RemoteInput input=new RemoteInput.Builder("reply").setAllowFreeFormInput(free).setChoices(new CharSequence[]{"Test"}).build();
  Notification.Action.Builder b=new Notification.Action.Builder(android.R.drawable.ic_menu_send,"Reply",pi).addRemoteInput(input);
  if(Build.VERSION.SDK_INT>=31)b.setAuthenticationRequired(auth);
  return b.build();
 }
 public void onStart(){Bundle result=new Bundle();try{
  ReplyDetector.Result empty=ReplyDetector.inspect(builder().build());expect(empty.action==null && empty.ordinary==0 && empty.wearable==0,"empty");
  Notification.Action ordinary=action(true,true,false);
  ReplyDetector.Result normal=ReplyDetector.inspect(builder().addAction(ordinary).build());expect(normal.action!=null && "ordinary".equals(normal.source),"ordinary reply");
  Notification wearable=builder().extend(new Notification.WearableExtender().addAction(action(true,true,false))).build();
  ReplyDetector.Result wear=ReplyDetector.inspect(wearable);expect(wear.action!=null && wear.ordinary==0 && wear.wearable==1 && "wearable".equals(wear.source),"wearable-only reply");
  Parcel parcel=Parcel.obtain();wearable.writeToParcel(parcel,0);parcel.setDataPosition(0);Notification restored=Notification.CREATOR.createFromParcel(parcel);parcel.recycle();expect(ReplyDetector.inspect(restored).action!=null,"parcel roundtrip wearable");
  ReplyDetector.Result choices=ReplyDetector.inspect(builder().addAction(action(false,true,false)).build());expect(choices.action==null && choices.choicesOnly==1,"choices only not free text");
  ReplyDetector.Result absent=ReplyDetector.inspect(builder().addAction(action(true,false,false)).build());expect(absent.action==null && absent.missingIntent==1,"missing pending intent");
  if(Build.VERSION.SDK_INT>=31){ReplyDetector.Result auth=ReplyDetector.inspect(builder().addAction(action(true,true,true)).build());expect(auth.action==null && auth.authRequired==1,"authentication gate");}
  ReplyDetector.Result fallback=ReplyDetector.inspect(builder().addAction(action(false,true,false)).extend(new Notification.WearableExtender().addAction(action(true,true,false))).build());expect(fallback.action!=null && "wearable".equals(fallback.source),"fallback to wearable");
  ReplyDetector.Result prefer=ReplyDetector.inspect(builder().addAction(ordinary).extend(new Notification.WearableExtender().addAction(action(true,true,false))).build());expect("ordinary".equals(prefer.source) && prefer.wearable==1,"prefer ordinary but inspect both");
  result.putString("result","PASS: "+checks+" reply detection checks; no actions executed");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("result","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
