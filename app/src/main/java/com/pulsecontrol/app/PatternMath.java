package com.pulsecontrol.app;

/** Pure-Java signal envelope; output is discretized to MuSE levels 0..3. */
public final class PatternMath {
    private PatternMath() {}
    public static int levelFromTouch(int percent) {
        if (percent <= 0) return 0;
        if (percent <= 33) return 1;
        if (percent <= 66) return 2;
        return 3;
    }
    public static int patternLevel(int pattern, int ceiling, double phase, float[] custom) {
        if (ceiling <= 0) return 0;
        ceiling = Math.min(3, ceiling);
        phase = phase - Math.floor(phase);
        double v;
        switch (pattern) {
            case 0: v = 1.0; break;                              // Constant
            case 1: v = phase < 0.50 ? 1.0 : 0.0; break;         // Pulses
            case 2: v = (Math.sin(phase*2*Math.PI-Math.PI/2)+1)/2; break;
            case 3: v = phase; break;                             // Ramp
            case 4: v = (phase < .15 || (phase>.32 && phase<.46)) ? 1.0 : 0.0; break;
            case 5: v = phase<.5 ? phase*2 : (1-phase)*2; break;
            case 6: v = phase<.33?.33:phase<.66?.66:1.0; break;
            case 7: v = .25 + .75*Math.abs(Math.sin(phase*6*Math.PI)); break;
            case 8: v = (Math.cos(phase*2*Math.PI)+1)/2; break;
            case 9: v = custom == null || custom.length==0 ? 0 :
                        Math.max(0,Math.min(1,custom[Math.min(custom.length-1,(int)(phase*custom.length))])); break;
            default: v = 0;
        }
        // Deterministic quantization with an actual OFF window for low envelopes.
        if (v < .15) return 0;
        return Math.max(1, Math.min(ceiling, (int)Math.ceil(v*ceiling)));
    }
}
