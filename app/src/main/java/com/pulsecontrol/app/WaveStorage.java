package com.pulsecontrol.app;

/** Compact, offline storage for 64 uniformly sampled user-drawn envelopes. */
public final class WaveStorage {
    private WaveStorage() {}
    public static String encode(float[] waveform) {
        if(waveform==null || waveform.length!=64) throw new IllegalArgumentException("Expected 64 samples");
        StringBuilder b=new StringBuilder();
        for(int i=0;i<64;i++) {
            if(i>0)b.append(',');
            b.append(Math.round(Math.max(0,Math.min(1,waveform[i]))*100));
        }
        return b.toString();
    }
    public static float[] decode(String encoded) {
        if(encoded==null)return null;
        String[] parts=encoded.split(",",-1);
        if(parts.length!=64)return null;
        float[] values=new float[64];
        try {
            for(int i=0;i<64;i++) {
                int value=Integer.parseInt(parts[i]);
                if(value<0 || value>100)return null;
                values[i]=value/100f;
            }
        } catch(NumberFormatException ex){return null;}
        return values;
    }
}
