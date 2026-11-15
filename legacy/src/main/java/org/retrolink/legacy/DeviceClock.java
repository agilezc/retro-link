package org.retrolink.legacy;

import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Uses the last paired host timestamp and advances it with the monotonic clock. */
final class DeviceClock {
 private static volatile Sample sample;

 private static final class Sample {
  final long epochMillis;
  final long receivedAt=SystemClock.elapsedRealtime();
  final TimeZone zone;
  Sample(long epochMillis,String zoneId){
   this.epochMillis=epochMillis;
   zone=TimeZone.getTimeZone(zoneId);
  }
  long now(){return epochMillis+SystemClock.elapsedRealtime()-receivedAt;}
 }

 static boolean sync(long epochMillis,String zoneId){
  if(epochMillis<=0 || zoneId.isEmpty())return false;
  sample=new Sample(epochMillis,zoneId);
  return true;
 }

 static String time(){
  Sample current=sample;
  SimpleDateFormat format=new SimpleDateFormat("HH:mm",Locale.US);
  if(current!=null)format.setTimeZone(current.zone);
  return format.format(new Date(current==null?System.currentTimeMillis():current.now()));
 }

 static long untilNextMinute(){
  Sample current=sample;
  long millis=current==null?System.currentTimeMillis():current.now();
  return 60000-millis%60000;
 }
}
