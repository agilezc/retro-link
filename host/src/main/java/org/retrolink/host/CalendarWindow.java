package org.retrolink.host;
import java.util.Calendar;
import java.util.TimeZone;
final class CalendarWindow {
 static Calendar start(long now,TimeZone zone){Calendar c=Calendar.getInstance(zone);c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c;}
 static long localDay(long utcDate,TimeZone zone){Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));utc.setTimeInMillis(utcDate);Calendar local=Calendar.getInstance(zone);local.clear();local.set(utc.get(Calendar.YEAR),utc.get(Calendar.MONTH),utc.get(Calendar.DATE));return local.getTimeInMillis();}
}
