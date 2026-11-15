package org.retrolink.host;
import java.util.*;
public class CalendarWindowTest {
 static long at(String zone,int y,int month,int day,int hour){Calendar c=Calendar.getInstance(TimeZone.getTimeZone(zone));c.clear();c.set(y,month-1,day,hour,0);return c.getTimeInMillis();}
 static void equal(long actual,long expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
  TimeZone sh=TimeZone.getTimeZone("Asia/Shanghai"),la=TimeZone.getTimeZone("America/Los_Angeles");
  equal(CalendarWindow.start(at("Asia/Shanghai",2026,11,3,15),sh).getTimeInMillis(),at("Asia/Shanghai",2026,11,3,0));
  equal(CalendarWindow.localDay(at("UTC",2026,11,3,0),sh),at("Asia/Shanghai",2026,11,3,0));
  equal(CalendarWindow.localDay(at("UTC",2026,11,3,0),la),at("America/Los_Angeles",2026,11,3,0));
  Calendar c=CalendarWindow.start(at("America/Los_Angeles",2026,10,31,12),la);long begin=c.getTimeInMillis();c.add(Calendar.DATE,7);
  equal(c.getTimeInMillis()-begin,169L*3600000L);
  c=CalendarWindow.start(at("America/Los_Angeles",2026,3,7,12),la);begin=c.getTimeInMillis();c.add(Calendar.DATE,7);equal(c.getTimeInMillis()-begin,167L*3600000L);
  System.out.println("PASS: midnight, all-day east/west timezones, both DST transitions");
 }
}
