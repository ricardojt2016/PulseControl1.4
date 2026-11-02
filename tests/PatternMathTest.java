import com.pulsecontrol.app.PatternMath;
public class PatternMathTest {
  private static void eq(int a, int b) { if (a != b) throw new AssertionError(a+" != "+b); }
  public static void main(String[] args) {
    eq(PatternMath.levelFromTouch(0), 0);
    eq(PatternMath.levelFromTouch(1), 1);
    eq(PatternMath.levelFromTouch(33), 1);
    eq(PatternMath.levelFromTouch(34), 2);
    eq(PatternMath.levelFromTouch(66), 2);
    eq(PatternMath.levelFromTouch(67), 3);
    eq(PatternMath.levelFromTouch(100), 3);
    eq(PatternMath.patternLevel(0,3,0,null),3);
    eq(PatternMath.patternLevel(1,3,0.1,null),3);
    eq(PatternMath.patternLevel(1,3,0.9,null),0);
    eq(PatternMath.patternLevel(9,3,0.5,new float[]{0,0,0,0}),0);
    for (int p=0;p<=9;p++)for (int t=0;t<100;t++) {
      int v=PatternMath.patternLevel(p,3,t/100.,new float[]{0f,.3f,.8f,1f});
      if (v<0 || v>3) throw new AssertionError("Out of bounds: "+v);
    }
    System.out.println("PatternMath: todos los tests OK");
  }
}
