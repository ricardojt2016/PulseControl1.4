package com.pulsecontrol.app;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

/** User-defined periodic envelope, locally sampled to 64 points. */
public final class DrawWaveView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] samples=new float[64];
    private boolean touched=false;
    public DrawWaveView(Context context) {
        super(context);
        for(int i=0;i<64;i++) samples[i]=.5f;
        setBackgroundColor(0xff202c3e);
    }
    public float[] getSamples(){ return samples.clone(); }
    public void reset(){ for(int i=0;i<64;i++)samples[i]=.5f; invalidate(); }
    @Override protected void onDraw(Canvas canvas){ super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        p.setColor(0xff42566d); p.setStrokeWidth(1);
        for(int k=1;k<=4;k++)canvas.drawLine(0,h*k/5,w,h*k/5,p);
        Path line=new Path();
        for(int i=0;i<samples.length;i++){
            float x=w*i/(samples.length-1f);
            float y=(1-samples[i])*h;
            if(i==0)line.moveTo(x,y); else line.lineTo(x,y);
        }
        p.setColor(0xff66dfbd);p.setStrokeWidth(5);p.setStyle(Paint.Style.STROKE);
        canvas.drawPath(line,p);p.setStyle(Paint.Style.FILL);
        p.setTextSize(24);p.setColor(0xffdae5ee);
        canvas.drawText(touched?"DIBUJANDO":"DIBUJA TU ONDA",16,32,p);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_MOVE:
                touched=true;
                int end=Math.max(0,Math.min(63,Math.round(63*event.getX()/Math.max(1,getWidth()))));
                float value=Math.max(0,Math.min(1,1-event.getY()/Math.max(1,getHeight())));
                // Interpolate missing points, so a fast stroke remains continuous.
                if(lastIndex>=0 && lastIndex!=end) {
                    int step=end>lastIndex?1:-1;
                    for(int i=lastIndex+step;i!=end;i+=step){
                        float f=(i-lastIndex)/(float)(end-lastIndex);
                        samples[i]=lastValue+(value-lastValue)*f;
                    }
                }
                samples[end]=value;lastIndex=end;lastValue=value;invalidate();return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                touched=false;lastIndex=-1;invalidate();return true;
            default:return true;
        }
    }
    private int lastIndex=-1; private float lastValue=0;
}
