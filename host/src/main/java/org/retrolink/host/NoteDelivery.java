package org.retrolink.host;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.Build;
final class NoteDelivery {
 static final int ID=32;
 static void update(Context c){
  int pending=new NoteStore(c).count(true);NotificationManager manager=c.getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("notes","速记转交",NotificationManager.IMPORTANCE_DEFAULT));
  if(pending==0){manager.cancel(ID);return;}
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  PendingIntent open=PendingIntent.getActivity(c,32,new Intent(c,NoteRelayActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(c,"notes").setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("有 "+pending+" 项速记内容待转交微信").setContentText("点击转交").setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();manager.notify(ID,n);
 }
}
