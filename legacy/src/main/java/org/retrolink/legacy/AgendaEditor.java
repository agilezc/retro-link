package org.retrolink.legacy;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

final class AgendaEditor {
 interface Save { void submit(JSONObject request); }

 static void show(Context context, JSONObject event, JSONObject snapshot, final Save save) {
  final boolean edit = event != null;
  final JSONArray calendars = snapshot.optJSONArray("calendars");
  if ((edit && !event.optBoolean("editable")) || (!edit && (calendars == null || calendars.length()==0))) {
   new AlertDialog.Builder(context).setMessage(edit ? "这项日程只能查看" :
     "请在 Nothing 授权日历读写，并启用一个可写日历后刷新。").setPositiveButton("知道了",null).show();
   return;
  }
  final JSONObject original = event;
  final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);
  format.setLenient(false);
  format.setTimeZone(TimeZone.getTimeZone(snapshot.optString("zone","Asia/Shanghai")));
  ScrollView scroll = new ScrollView(context);
  LinearLayout form = new LinearLayout(context);
  form.setOrientation(LinearLayout.VERTICAL);
  int pad=(int)(12*context.getResources().getDisplayMetrics().density);
  form.setPadding(pad,0,pad,pad);
  scroll.addView(form);
  final Spinner target = new Spinner(context);
  if (!edit) {
   String[] names = new String[calendars.length()];
   for(int i=0;i<names.length;i++) names[i]=calendars.optJSONObject(i).optString("name");
   ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,names);
   adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
   target.setAdapter(adapter);
   form.addView(target);
  }
  long now=System.currentTimeMillis(), start=edit?event.optLong("begin"):(now/900000+1)*900000;
  long end=edit?event.optLong("end"):start+3600000;
  final EditText title=field(form,"标题",edit?event.optString("title"):"",100);
  final EditText place=field(form,"地点",edit?event.optString("location"):"",80);
  final EditText begin=field(form,"开始 · 年-月-日 时:分",format.format(new Date(start)),16);
  final EditText finish=field(form,"结束 · 年-月-日 时:分",format.format(new Date(end)),16);
  final AlertDialog dialog=new AlertDialog.Builder(context).setTitle(edit?"编辑日程":"新增日程")
      .setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
  dialog.show();
  dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener(){
   public void onClick(android.view.View view){
    String name=title.getText().toString().trim();
    if(name.length()==0){title.setError("请输入标题");return;}
    long from=parse(format,begin),to=parse(format,finish);
    if(from<=0){begin.setError("请用 yyyy-MM-dd HH:mm");return;}
    if(to<=from){finish.setError("结束时间须晚于开始时间");return;}
    JSONObject request=new JSONObject();
    try{
     request.put("id",UUID.randomUUID().toString()).put("title",name)
      .put("location",place.getText().toString()).put("begin",from).put("end",to)
      .put("calendar",edit?original.getLong("calendar"):calendars.getJSONObject(target.getSelectedItemPosition()).getLong("id"));
     if(edit)request.put("event",original.getLong("event")).put("oldBegin",original.getLong("begin"))
      .put("oldEnd",original.getLong("end")).put("oldTitle",original.getString("title"))
      .put("oldLocation",original.getString("location"));
    }catch(org.json.JSONException e){title.setError("日程数据无效，请刷新后重试");return;}
    save.submit(request);
    dialog.dismiss();
   }
  });
 }
 private static EditText field(LinearLayout form,String label,String value,int limit){
  TextView caption=new TextView(form.getContext());
  caption.setText(label);
  form.addView(caption);
  EditText input=new EditText(form.getContext());
  input.setSingleLine(true);
  input.setTextSize(16);
  input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
  input.setText(value);
  form.addView(input);
  return input;
 }
 private static long parse(SimpleDateFormat format,EditText field){
  String value=field.getText().toString();
  ParsePosition position=new ParsePosition(0);
  Date date=format.parse(value,position);
  return date==null||position.getIndex()!=value.length()||!format.format(date).equals(value)?-1:date.getTime();
 }
}
