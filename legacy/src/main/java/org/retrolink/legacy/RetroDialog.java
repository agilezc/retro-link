package org.retrolink.legacy;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
/** Small keyboard-accessible dialog using the same paper/ink palette. */
final class RetroDialog {
 interface Choice {void choose(int index);}
 static void show(final Context context,String title,String message,final String[] options,final int selected,final Choice action){
  final Dialog dialog=new Dialog(context){
   @Override public boolean dispatchKeyEvent(KeyEvent event){
    KeySound.onKey(event);
    return super.dispatchKeyEvent(event);
   }
   @Override public boolean dispatchTouchEvent(MotionEvent event){
    if(event.getActionMasked()==MotionEvent.ACTION_UP)KeySound.play();
    return super.dispatchTouchEvent(event);
   }
  };dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);int pad=dp(context,18);root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(0xffeeeee8);
  TextView heading=label(context,title,18,0xff20201e);root.addView(heading);
  if(message.length()>0){TextView body=label(context,message,12,0xff777871);body.setPadding(0,dp(context,8),0,dp(context,10));root.addView(body);}
  final LinearLayout choices=new LinearLayout(context);choices.setOrientation(LinearLayout.VERTICAL);root.addView(choices);
  final int[] page={0};final Runnable[] render=new Runnable[1];render[0]=new Runnable(){public void run(){choices.removeAllViews();for(int i=page[0]*3;i<Math.min(options.length,page[0]*3+3);i++){final int index=i;Button button=new Button(context);button.setText((i==selected?"●  ":"")+options[i]);button.setTypeface(UiFonts.body(context));UiMetrics.text(button,13);button.setMinHeight(0);button.setMinimumHeight(0);button.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);button.setTextColor(0xff20201e);button.setPadding(dp(context,12),0,dp(context,12),0);button.setBackgroundResource(R.drawable.retro_row);choices.addView(button,new LinearLayout.LayoutParams(-1,dp(context,42)));button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();if(action!=null)action.choose(index);}});}
  }};render[0].run();if(options.length>3){LinearLayout nav=new LinearLayout(context);for(int n=0;n<2;n++){final int direction=n==0?-1:1;Button b=new Button(context);b.setText(n==0?"←":"→");b.setBackgroundResource(R.drawable.retro_row);nav.addView(b,new LinearLayout.LayoutParams(0,dp(context,28),1));b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){page[0]=Math.max(0,Math.min((options.length-1)/3,page[0]+direction));render[0].run();}});}root.addView(nav);}
  TextView cancel=label(context,"返回",12,0xff777871);cancel.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);cancel.setFocusable(true);cancel.setClickable(true);root.addView(cancel,new LinearLayout.LayoutParams(-1,dp(context,30)));cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
  dialog.setContentView(root);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(context,48),dp(context,390)),-2);}dialog.show();if(w!=null)w.setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(context,48),dp(context,390)),-2);
 }
 private static int dp(Context c,int n){return UiMetrics.unit(c,n);}
 private static TextView label(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);UiMetrics.text(t,size);t.setTextColor(color);t.setTypeface(UiFonts.body(c));return t;}
}
