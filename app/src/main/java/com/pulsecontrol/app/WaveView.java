package com.pulsecontrol.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class WaveView extends View {
    private final Paint p = new Paint(3);
    private int mode = 0, intensity = 3;
    private double phase = 0;
    private boolean running = false;
    public WaveView(Context context) { super(context); }
    public void setWave(int m, int i, boolean r) { mode=m; intensity=i; running=r; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight(), cy=h/2;
        p.setColor(0xff263449); p.setStrokeWidth(2f);
        for(int j=1;j<4;j++) c.drawLine(0,h*j/4f,w,h*j/4f,p);
        p.setColor(0xff64dfbd); p.setStrokeWidth(4f); p.setStyle(Paint.Style.STROKE);
        android.graphics.Path path = new android.graphics.Path();
        double amp = h*.36*intensity/9.0;
        for(int x=0;x<(int)w;x+=3){double t=x/(double)Math.max(w,1)*5*Math.PI+phase;
            double v;
            switch(mode){
                case 0: v=1;break; // continuous envelope
                case 1: v=Math.sin(t)>0.6?1:0;break;
                case 2: v=(Math.sin(t)+1)/2;break;
                case 3: v=(t%(2*Math.PI))/(2*Math.PI);break;
                case 4: v=(Math.sin(2*t)+1)/2;break;
                case 5: v=Math.abs(Math.sin(t));break;
                case 6: v=Math.sin(t)>0?1:.2;break;
                case 7: v=(1+Math.sin(t)*Math.sin(t*.17))/2;break;
                case 8: v=(Math.cos(t)+1)/2;break;
                default: v=(Math.sin(t)+1)/2;break;
            }
            float y=(float)(cy-amp*v);
            if(x==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xffa6b7c9); p.setTextSize(25);
        c.drawText("PATRÓN ORIENTATIVO · 3 NIVELES BLE",12,h-12,p);
        if(running){phase+=.13;postInvalidateDelayed(35);}
    }
}
