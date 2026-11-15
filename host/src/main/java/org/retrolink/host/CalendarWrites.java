package org.retrolink.host;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class CalendarWrites {
 private static final String WRITABLE = "visible=1 AND calendar_access_level>=500";

 static boolean allowed(Context context) {
  return context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
 }

 static JSONArray calendars(ContentResolver resolver) throws JSONException {
  JSONArray result = new JSONArray();
  try (Cursor cursor = resolver.query(Calendars.CONTENT_URI,
    new String[]{"_id","calendar_displayName","account_name"}, WRITABLE, null, "calendar_displayName ASC")) {
   while (cursor != null && cursor.moveToNext() && result.length() < 20) {
    result.put(new JSONObject().put("id", cursor.getLong(0))
      .put("name", trim(cursor.getString(1), 64) + " · " + trim(cursor.getString(2), 72)));
   }
  }
  return result;
 }

 private static String trim(String value, int limit) {
  return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
 }

 static boolean save(Context context, JSONObject request) throws JSONException {
  ContentResolver resolver = context.getContentResolver();
  long calendar = request.getLong("calendar");
  long event = request.optLong("event");
  String title = request.getString("title").trim();
  String location = request.getString("location").trim();
  long start = request.getLong("begin"), end = request.getLong("end");
  if (title.isEmpty() || title.length() > 100 || location.length() > 80 || start <= 0 || end <= start)
   throw new IllegalArgumentException();
  try (Cursor writable = resolver.query(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendar),
    new String[]{"_id"}, WRITABLE, null, null)) {
   if (writable == null || !writable.moveToFirst()) return false;
  }
  ContentValues values = new ContentValues();
  values.put(Events.TITLE, title);
  values.put(Events.EVENT_LOCATION, location);
  values.put(Events.DTSTART, start);
  values.put(Events.DTEND, end);
  if (event > 0) {
   return resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, event), values,
     "calendar_id=? AND deleted=0 AND allDay=0 AND rrule IS NULL AND rdate IS NULL AND original_id IS NULL AND original_sync_id IS NULL AND dtstart=? AND dtend=? AND COALESCE(title,'')=? AND COALESCE(eventLocation,'')=?",
     new String[]{String.valueOf(calendar),request.getString("oldBegin"),request.getString("oldEnd"),
      request.getString("oldTitle"),request.getString("oldLocation")}) == 1;
  }
  values.put(Events.CALENDAR_ID, calendar);
  values.put(Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
  return resolver.insert(Events.CONTENT_URI, values) != null;
 }
}
