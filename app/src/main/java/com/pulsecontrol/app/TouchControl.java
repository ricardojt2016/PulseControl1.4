package com.pulsecontrol.app;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.function.IntConsumer;
public final class TouchControl extends View {
    private final Paint paint=new Paint(3);
    private final IntConsumer listener;
    private int value=0;
    private boolean pressed=false;
    public TouchControl(Context c,IntConsumer listener){super(c);this.listener=listener;setBackgroundColor(0xff202c3e);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);
      float w=getWidth(),h=getHeight(),y=h*(1-value/100f);
      paint.setColor(0xff33445a);paint.setStrokeWidth(2);
      for(int i=1;i<5;i++)c.drawLine(0,h*i/5,w,h*i/5,paint);
      paint.setColor(0xff66dfbd);c.drawRect(0,y,w,h,paint);
      paint.setColor(0xffffffff);paint.setTextSize(45);paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(value+" %",w/2,h/2,paint);
      paint.setTextSize(24);c.drawText(pressed?"MANTENIENDO":"TOCA Y DESLIZA",w/2,h/2+35,paint);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){switch(e.getActionMasked()){
      case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_MOVE:
        pressed=true; value=Math.max(0,Math.min(100,Math.round(100*(1-e.getY()/Math.max(1,getHeight()))))); listener.accept(value);invalidate();return true;
      case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_UP:
        pressed=false;value=0;listener.accept(0);invalidate();return true;
      default:return true;
    }}
}
