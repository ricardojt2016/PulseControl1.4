import com.pulsecontrol.app.WaveStorage;
public class WaveStorageTest {
    public static void main(String[] args) {
        float[] wave=new float[64];
        for(int i=0;i<64;i++)wave[i]=i/63f;
        String saved=WaveStorage.encode(wave);
        float[] loaded=WaveStorage.decode(saved);
        if(loaded==null || loaded.length!=64 || loaded[0]!=0 || loaded[63]!=1)
            throw new AssertionError("Wave not preserved");
        if(WaveStorage.decode(null)!=null || WaveStorage.decode("bogus")!=null)
            throw new AssertionError("Invalid storage not rejected");
        System.out.println("WaveStorage: todos los tests OK");
    }
}
