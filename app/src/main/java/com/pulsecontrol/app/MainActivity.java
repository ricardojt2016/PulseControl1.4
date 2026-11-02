package com.pulsecontrol.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG=0xff101723, CARD=0xff202c3e, GREEN=0xff66dfbd, WHITE=Color.WHITE, MUTED=0xffb6c2d1;
    private LinearLayout root, body, statusRow;
    private BluetoothAdapter bt;
    private BluetoothLeScanner scanner;
    private MuseTransmitter muse;
    private String museStatus="MuSE preparado · receptor sin identificación";
    private boolean museArmed=false;
    private int actualLevel=0;
    private boolean oscillatorOn=false;
    private boolean fingerMode=false;
    private int frequencyIndex=1; // period: 4 seconds / cycle
    private long waveStartMs=0;
    private final double[] cycleHz={0.125,0.25,0.5};
    private final Runnable oscillator=new Runnable(){public void run(){
        if(!oscillatorOn || !museArmed)return;
        long elapsed=android.os.SystemClock.elapsedRealtime()-waveStartMs;
        double phase=(elapsed*cycleHz[frequencyIndex]/1000.0)%1;
        int level=PatternMath.patternLevel(pattern,Math.max(1,desiredLevel),phase,customWave);
        if(level!=actualLevel){actualLevel=level;muse.sendLevel(level,false);}
        handler.postDelayed(this,700);
    }};
    private float[] customWave=null;
    private int desiredLevel=1;
    private DrawWaveView waveEditor;
    private BluetoothGatt gatt;
    private boolean scanning=false, connected=false, running=false;
    private String gattReport="";
    private String connectedBattery="No detectada";
    private int intensity=3, pattern=0, remaining=0, activePage=0;
    private final String[] patterns={"Constante","Pulsos","Olas","Progresiva","Latido","Triangular","Escalonada","Aleatoria","Respiración","Dibujo propio"};
    private final Map<String,BluetoothDevice> found = new LinkedHashMap<>();
    private final Map<String,Integer> rssiByAddress = new HashMap<>();
    private android.content.SharedPreferences aliases;
    private android.content.SharedPreferences savedWaves;
    private TextView liveLevelView;
    private long lastScanRenderMs=0;
    private String connectedAddress=null;
    // Previous MAC was only a candidate, never verified as the MuSE receiver.
    private static final String KNOWN_ADDRESS="E4:26:D5:F8:1D:51";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WaveView wave;
    private TextView status, countdownView;
    private final Runnable timer = new Runnable(){public void run(){
        if(running && remaining>0){remaining--;handler.postDelayed(this,1000);}
        else if(running && remaining==0){stopEverything();}
        if(countdownView!=null && activePage==0)
            countdownView.setText(remaining>0?String.format(Locale.ROOT,"%02d:%02d restantes",remaining/60,remaining%60):"Inactivo");
    }};
    private final ScanCallback scanCallback = new ScanCallback(){@Override public void onScanResult(int callbackType,ScanResult result){BluetoothDevice d=result.getDevice();if(d!=null){String addr=d.getAddress().toUpperCase(Locale.ROOT);
                found.put(addr,d);rssiByAddress.put(addr,result.getRssi());
                long now=android.os.SystemClock.elapsedRealtime();
                if(activePage==2 && now-lastScanRenderMs>550){lastScanRenderMs=now;showDevices();}}}@Override public void onScanFailed(int errorCode){scanning=false;toast("Error de búsqueda BLE: "+errorCode);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);bt=manager==null?null:manager.getAdapter();
        aliases=getSharedPreferences("ble_device_aliases",MODE_PRIVATE);
        savedWaves=getSharedPreferences("pulse_saved_waves",MODE_PRIVATE);
        // Do not attribute an unverified MAC address to the MuSE target.
        if("diver1".equals(aliases.getString(KNOWN_ADDRESS,"")))
           aliases.edit().remove(KNOWN_ADDRESS).apply();
        muse=new MuseTransmitter(this,bt,value->{museStatus=value;
            runOnUiThread(()->{if(status!=null)status.setText(value);});});
        render(0); }
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);return l;}
    private void add(LinearLayout parent,View child,int height,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.setMargins(0,dp(margin),0,0);parent.addView(child,p);}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(WHITE);b.setTextSize(17);b.setBackground(bg(CARD,13));b.setOnClickListener(v->action.run());return b;}
    private void render(int page){
        if(page!=0 && (oscillatorOn || fingerMode || actualLevel>0)) stopEverything();
        activePage=page;root=vertical();root.setBackgroundColor(BG);setContentView(root);
        LinearLayout header=vertical();header.setPadding(dp(18),dp(12),dp(18),dp(8));add(root,header,-2,0);
        LinearLayout headerTop=horizontal();add(header,headerTop,74,0);
        LinearLayout heading=vertical();headerTop.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
        add(heading,text("PULSE CONTROL",23,GREEN,true),-2,5);
        add(heading,text("Control local • Bluetooth BLE",12,MUTED,false),-2,3);
        Button emergency=button("■ STOP",()->{stopEverything();if(activePage==0)showControl();else if(activePage==2)showDevices();toast("Orden STOP MuSE enviada (sin acuse de recibo)");});
        emergency.setTextSize(17);emergency.setBackground(bg(0xffae3045,13));
        headerTop.addView(emergency,new LinearLayout.LayoutParams(dp(112),-1));
        LinearLayout nav=horizontal();nav.setPadding(dp(12),0,dp(12),dp(6));add(root,nav,56,0);
        String[] names={"Control","Ondas","Dispositivos"};for(int i=0;i<3;i++){final int target=i;Button b=button(names[i],()->render(target));b.setTextSize(13);b.setBackground(bg(page==i?0xff286356:CARD,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(3),0,dp(3),0);nav.addView(b,lp);}
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=vertical();body.setPadding(dp(16),dp(8),dp(16),dp(24));scroll.addView(body);
        status=text(museStatus,12,MUTED,false);
        add(header,status,-2,3);
        if(page==0)showControl();if(page==1)showWaves();if(page==2)showDevices();
    }
    private void title(String s){add(body,text(s,17,GREEN,true),-2,16);}
    private void explain(String s){TextView t=text(s,14,MUTED,false);t.setPadding(dp(10),dp(12),dp(10),dp(12));t.setBackground(bg(CARD,12));add(body,t,-2,12);}
    private void addButton(String s,Runnable action){add(body,button(s,action),62,9);}
    private void stopEverything(){
        oscillatorOn=false;fingerMode=false;running=false;remaining=0;
        handler.removeCallbacks(oscillator);handler.removeCallbacks(timer);
        actualLevel=0;desiredLevel=1;
        if(muse!=null)muse.stop();
    }
    private boolean ensureMuSE(){
        if(bt==null){toast("Este teléfono no tiene adaptador Bluetooth");return false;}
        if(Build.VERSION.SDK_INT>=31){
            ArrayList<String> missing=new ArrayList<>();
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),15);return false;}
        }
        if(!muse.isSupported()){toast("Activa Bluetooth; comprueba soporte BLE Advertising");return false;}
        if(!museArmed){
            new android.app.AlertDialog.Builder(this).setTitle("Activar controlador MuSE")
                .setMessage("Emisión BLE no dirigida. Todos los receptores MuSE compatibles cercanos podrían responder. No identifica el dispositivo ni confirma la recepción. Prueba el hardware fuera del cuerpo y conserva el mando para detenerlo.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Activar",(d,w)->{museArmed=true;toast("Control MuSE activado. Usa PARAR para detenerlo.");})
                .show();return false;
        }
        return true;
    }
    private void startSafetyTimerIfNeeded(){
        if(!running){
            if(remaining<=0)remaining=5*60; // timeout by default, not indefinite
            running=true;
            handler.removeCallbacks(timer);
            handler.postDelayed(timer,1000);
        }
    }
    private void sendManualLevel(int level){
        if(!ensureMuSE())return;
        stopWaveOnly();
        startSafetyTimerIfNeeded();
        actualLevel=level;
        muse.sendLevel(level,true);
        if(activePage==0)showControl();
    }
    private void stopWaveOnly(){oscillatorOn=false;fingerMode=false;handler.removeCallbacks(oscillator);}
    private void startWave(){
        if(pattern==9 && customWave==null){toast("Dibuja o carga un favorito primero");return;}
        if(!ensureMuSE())return;
        stopWaveOnly();
        startSafetyTimerIfNeeded();
        oscillatorOn=true;
        actualLevel=-1;
        waveStartMs=android.os.SystemClock.elapsedRealtime();
        handler.post(oscillator);
        if(activePage==0)showControl();
    }
    private void showControl(){
        if(activePage!=0)return;
        body.removeAllViews();
        explain("PERFIL MuSE: diver1 (perfil local, receptor sin MAC verificada). Los anuncios BLE controlan receptores compatibles cercanos; no existe telemetría de recepción ni de batería.");
        title("BATERÍA");
        add(body,text("No disponible · MuSE no comunica el nivel de carga",15,MUTED,false),-2,5);
        title("CONTROL REAL · 3 INTENSIDADES");
        explain("Pulsa un nivel para enviar una orden real. 0 envía STOP. Los niveles 2 y 3 están documentados pero aún no están verificados en tu unidad. Si dejas de utilizar el teléfono, detén el dispositivo con su mando.");
        LinearLayout levels=horizontal();add(body,levels,78,6);
        for(int i=0;i<=3;i++){
            final int level=i;
            Button b=button(i==0?"0 · STOP":String.valueOf(i),()->{
               if(level==0){stopEverything();showControl();}
               else sendManualLevel(level);
            });
            b.setTextSize(23);
            b.setBackground(bg(i==actualLevel?0xff286356:(i==0?0xffa2354a:CARD),13));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
            lp.setMargins(dp(3),0,dp(3),0);levels.addView(b,lp);
        }
        title("MODO LIBRE · DESLIZA Y MANTÉN");
        explain("Intensidad según la altura del dedo; al levantarlo se emite STOP. Los 3 niveles son discretos; la frecuencia mecánica del motor no es configurable.");
        liveLevelView=text("Nivel solicitado: "+Math.max(0,actualLevel)+"/3 (sin confirmación del receptor)",13,MUTED,false);
        add(body,liveLevelView,-2,4);
        TouchControl touch=new TouchControl(this,value->{
            if(!museArmed){ if(value>0)ensureMuSE();return; }
            if(!muse.isSupported())return;
            fingerMode=value>0;
            int level=PatternMath.levelFromTouch(value);
            if(value>0)startSafetyTimerIfNeeded();
            if(level!=actualLevel){
                stopWaveOnly();fingerMode=value>0;
                actualLevel=level;
                muse.sendLevel(level,level==0);
            } else if(value==0){muse.sendLevel(0,true);}
            if(value==0 && !oscillatorOn){running=false;remaining=0;handler.removeCallbacks(timer);}
            if(liveLevelView!=null)liveLevelView.setText("Nivel solicitado: "+level+"/3 (sin confirmación del receptor)");
        });
        add(body,touch,250,8);
        title("PATRONES · "+patterns[pattern]);
        wave=new WaveView(this);
        wave.setWave(pattern,Math.max(1,desiredLevel)*3,oscillatorOn);
        wave.setBackground(bg(CARD,13));add(body,wave,155,7);
        addButton("Elegir o dibujar patrón",()->render(1));
        title("NIVEL MÁXIMO DEL PATRÓN: "+desiredLevel);
        SeekBar amplitude=new SeekBar(this);amplitude.setMax(2);amplitude.setProgress(desiredLevel-1);
        amplitude.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar v,int progress,boolean user){desiredLevel=1+progress;}
            public void onStartTrackingTouch(SeekBar v){}
            public void onStopTrackingTouch(SeekBar v){}
        });add(body,amplitude,49,2);
        title("FRECUENCIA DE PULSOS");
        add(body,text(String.format(Locale.ROOT,"%.3f ciclos/s · %d s por ciclo",cycleHz[frequencyIndex],
                 (int)Math.round(1/cycleHz[frequencyIndex])),15,WHITE,false),-2,5);
        SeekBar f=new SeekBar(this);f.setMax(2);f.setProgress(frequencyIndex);
        f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int p,boolean user){frequencyIndex=p;}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){showControl();}
        });add(body,f,45,0);
        explain("El ritmo se genera por secuencias de comandos BLE (0,125–0,5 ciclos/s aproximados). No equivale a controlar la frecuencia de giro del motor. Depende de las limitaciones de Android y del receptor.");
        addButton(oscillatorOn?"Reiniciar patrón":"▶ Iniciar patrón real",this::startWave);
        title("TEMPORIZADOR DE SEGURIDAD");
        explain("Al activar cualquier nivel o patrón comienza automáticamente un límite de 5 minutos, si no has seleccionado otra duración. Al finalizar se envía STOP (sin garantía de recepción).");
        countdownView=text(remaining>0?String.format(Locale.ROOT,"%02d:%02d restantes",remaining/60,remaining%60):"5 min por defecto al activar",19,WHITE,true);
        add(body,countdownView,-2,4);
        LinearLayout times=horizontal();add(body,times,65,6);
        for(int m:new int[]{2,5,10}){
           Button b=button(m+" min",()->{remaining=m*60;running=true;
               handler.removeCallbacks(timer);handler.postDelayed(timer,1000);showControl();});
           LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
           lp.setMargins(dp(3),0,dp(3),0);times.addView(b,lp);
        }
        Button stop=button("■  STOP MuSE — DETENER TODO",()->{stopEverything();showControl();});
        stop.setBackground(bg(0xffae3045,13));add(body,stop,82,15);
        add(body,text("Sin Internet · Sin cuentas · Sin analítica · Parada no garantizada si se interrumpe Bluetooth: usa el mando",12,MUTED,false),-2,12);
    }
    private void showWaves(){
        body.removeAllViews();
        explain("Patrones generados localmente y cuantizados a los 3 niveles MuSE. No confundir la forma visual con la vibración exacta del motor.");
        title("BIBLIOTECA DE ONDAS");
        for(int i=0;i<patterns.length;i+=2){
            LinearLayout line=horizontal();add(body,line,92,8);
            for(int j=i;j<Math.min(i+2,patterns.length);j++){
                final int p=j;
                Button b=button(patterns[j]+(j==pattern?" ✓":""),()->{pattern=p;render(0);});
                b.setBackground(bg(j==pattern?0xff286356:CARD,13));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
                lp.setMargins(dp(3),0,dp(3),0);line.addView(b,lp);
            }
        }
        title("DIBUJAR MI ONDA");
        explain("Dibuja de izquierda a derecha: altura = intensidad. La app repetirá la curva usando niveles discretos 0–3.");
        waveEditor=new DrawWaveView(this);add(body,waveEditor,240,9);
        addButton("Usar dibujo sin guardar",()->{
             customWave=waveEditor.getSamples();pattern=9;render(0);
        });
        addButton("Limpiar dibujo",()->waveEditor.reset());
        title("MIS ONDAS · GUARDADO LOCAL");
        explain("Puedes guardar hasta tres dibujos; se conservan en este teléfono, sin servidores ni cuentas.");
        for(int slot=1;slot<=3;slot++){
            final int which=slot;
            LinearLayout line=horizontal();add(body,line,68,7);
            Button save=button("Guardar "+slot,()->{
                savedWaves.edit().putString("wave"+which,WaveStorage.encode(waveEditor.getSamples())).apply();
                customWave=waveEditor.getSamples();pattern=9;
                toast("Onda guardada en favorito "+which);
            });
            Button load=button("Cargar "+slot,()->{
                float[] points=WaveStorage.decode(savedWaves.getString("wave"+which,null));
                if(points==null){toast("Favorito "+which+" vacío");return;}
                customWave=points;pattern=9;render(0);
            });
            for(Button b:new Button[]{save,load}){
                b.setTextSize(15);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
                lp.setMargins(dp(3),0,dp(3),0);line.addView(b,lp);
            }
        }
    }
    private boolean perms(){if(Build.VERSION.SDK_INT>=31){if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED ||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},11);return false;}}else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},11);return false;}return true;}
    private void startScan(){if(bt==null){toast("Bluetooth no disponible");return;}if(!perms())return;if(!bt.isEnabled()){startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));return;}try{if(scanning)stopScan();found.clear();rssiByAddress.clear();scanner=bt.getBluetoothLeScanner();if(scanner==null){toast("Escáner BLE no disponible");return;}scanning=true;scanner.startScan(scanCallback);render(2);handler.postDelayed(()->{if(scanning){stopScan();if(activePage==2)showDevices();}},12000);}catch(SecurityException e){toast("Faltan permisos Bluetooth");}}
    private void stopScan(){if(scanning && scanner!=null){try{scanner.stopScan(scanCallback);}catch(SecurityException ignored){}scanning=false;}}
    private void connect(BluetoothDevice d){
        if(!perms())return;
        stopScan();
        connectedAddress=d.getAddress().toUpperCase(Locale.ROOT);
        connectedBattery="No detectada";
        if(gatt!=null){gatt.close();gatt=null;}
        gattReport="";
        try{
            gatt=d.connectGatt(this,false,new BluetoothGattCallback(){
                @Override public void onConnectionStateChange(BluetoothGatt g,int code,int state){
                    connected=state==BluetoothProfile.STATE_CONNECTED;
                    if(connected){try{g.discoverServices();}catch(SecurityException ignored){}}
                    else {gattReport="";connectedBattery="No detectada";}
                    runOnUiThread(()->{if(activePage==2)showDevices();});
                }
                @Override public void onServicesDiscovered(BluetoothGatt g,int code){
                    runOnUiThread(()->{
                        showGatt(g);
                        BluetoothGattService battery=g.getService(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"));
                        if(battery!=null){
                            BluetoothGattCharacteristic c=battery.getCharacteristic(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"));
                            if(c!=null && (c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0){
                                try{g.readCharacteristic(c);}catch(SecurityException ignored){}
                            }
                        }
                    });
                }
                @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int statusCode){
                    batteryRead(c,value,statusCode);
                }
                @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode){
                    batteryRead(c,c.getValue(),statusCode);
                }
            });
            toast("Conectando GATT...");
        }catch(SecurityException e){toast("Faltan permisos de conexión");}
    }
    private void batteryRead(BluetoothGattCharacteristic c,byte[] data,int statusCode){
        if(statusCode!=BluetoothGatt.GATT_SUCCESS || data==null || data.length==0)return;
        if(!c.getUuid().equals(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")))return;
        int value=data[0]&0xff;
        if(value>100)return;
        connectedBattery=value+" %";
        runOnUiThread(()->{if(activePage==2)showDevices();});
    }
    private void showGatt(BluetoothGatt g){
        StringBuilder report=new StringBuilder();
        for(BluetoothGattService service:g.getServices()){
            report.append("Servicio: ").append(service.getUuid()).append('\n');
            for(BluetoothGattCharacteristic ch:service.getCharacteristics()){
                int prop=ch.getProperties();String flags="";
                if((prop&BluetoothGattCharacteristic.PROPERTY_READ)!=0)flags+="READ ";
                if((prop&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)flags+="WRITE ";
                if((prop&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)flags+="WRITE_NO_RESPONSE ";
                if((prop&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)flags+="NOTIFY ";
                if((prop&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0)flags+="INDICATE ";
                report.append("  ").append(ch.getUuid()).append(" [").append(flags.trim()).append("]\n");
            }
        }
        gattReport=report.toString();
        if(activePage==2)showDevices();
    }
    private void disconnect(){stopEverything();stopScan();if(gatt!=null){try{gatt.disconnect();}catch(SecurityException ignored){}gatt.close();gatt=null;}connected=false;connectedAddress=null;connectedBattery="No detectada";gattReport="";render(2);}
    private String aliasFor(String address,String fallback){
        String alias=aliases.getString(address,"");return alias.isEmpty()?fallback:alias;
    }
    private void editAlias(String address,String suggested){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setText(aliases.getString(address,""));
        input.setHint(suggested);input.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(this).setTitle("Nombre del dispositivo")
            .setMessage(address+"\nEl nombre se guarda solo en este móvil.")
            .setView(input).setPositiveButton("Guardar",(dialog,which)->{
                String value=input.getText().toString().trim();
                if(value.length()>40)value=value.substring(0,40);
                if(value.isEmpty())aliases.edit().remove(address).apply();
                else aliases.edit().putString(address,value).apply();
                showDevices();
            }).setNeutralButton("Quitar nombre",(dialog,which)->{
                aliases.edit().remove(address).apply();showDevices();
            }).setNegativeButton("Cancelar",null).show();
    }
    private void showDevices(){if(activePage!=2)return;body.removeAllViews();
        explain("Dispositivos BLE GATT ordenados por RSSI. No son necesariamente el receptor MuSE. La señal más fuerte no confirma que sea el dispositivo buscado.");
        addButton(scanning?"Detener búsqueda":"Buscar dispositivos Bluetooth",()->{if(scanning){stopScan();showDevices();}else startScan();});
        if(connected)addButton("Desconectar",this::disconnect);
        title("PERFIL MuSE · diver1");
        explain("Comandos MuSE comprobados para encender y detener un receptor, pero sin MAC confirmada. El control funciona mediante anuncios BLE y no aparece como conexión GATT. Batería: NO DISPONIBLE.");
        addButton("■ STOP MuSE",()->{stopEverything();showDevices();});
        addButton("Ir al control MuSE",()->render(0));
        add(body,text("Emisor BLE: "+(muse!=null && muse.isSupported()?"compatible":"no disponible o Bluetooth apagado"),13,MUTED,false),-2,5);
        title("DISPOSITIVOS GATT · SEÑAL FUERTE PRIMERO ("+found.size()+")");
        if(found.isEmpty())add(body,text("Enciende el dispositivo y pulsa Buscar. La búsqueda dura 12 segundos.",14,MUTED,false),-2,7);
        ArrayList<String> ordered=new ArrayList<>(found.keySet());
        Collections.sort(ordered,(a,b)->{
            int signalCompare=Integer.compare(rssiByAddress.getOrDefault(b,-127),rssiByAddress.getOrDefault(a,-127));
            return signalCompare!=0?signalCompare:a.compareTo(b);
        });
        for(String address:ordered){
            BluetoothDevice d=found.get(address);String systemName="Dispositivo BLE";
            try{String name=d.getName();if(name!=null&&!name.trim().isEmpty())systemName=name;}catch(SecurityException ignored){}
            final String originalName=systemName;
            int rssi=rssiByAddress.getOrDefault(address,-127);
            String label=aliasFor(address,systemName);
            TextView heading=text(label+"    "+rssi+" dBm",17,WHITE,true);
            add(body,heading,-2,15);
            add(body,text(address+(address.equals(KNOWN_ADDRESS)?" · dirección candidata SIN verificar":""),12,MUTED,false),-2,3);
            LinearLayout controls=horizontal();add(body,controls,60,6);
            Button connectButton=button(connected&&address.equals(connectedAddress)?"Conectado":"Conectar",()->connect(d));
            Button renameButton=button("Renombrar",()->editAlias(address,originalName));
            for(Button btn:new Button[]{connectButton,renameButton}){
                btn.setTextSize(15);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
                p.setMargins(dp(3),0,dp(3),0);controls.addView(btn,p);
            }
        }
        if(connected && gatt!=null){title("ESTADO: CONECTADO"+(connectedAddress==null?"":" · "+aliasFor(connectedAddress,connectedAddress)));
            add(body,text("Batería GATT de este dispositivo: "+connectedBattery+" (NO atribuida a diver1)",14,MUTED,false),-2,6);
            if(!gattReport.isEmpty()){
                addButton("Compartir diagnóstico GATT",()->{
                    Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT,gattReport);
                    startActivity(Intent.createChooser(share,"Compartir diagnóstico Bluetooth"));
                });
                TextView info=text(gattReport,12,MUTED,false);info.setTextIsSelectable(true);add(body,info,-2,12);
            }else explain("Esperando descubrimiento de servicios GATT…");
        }
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==11 && g.length>0){boolean ok=true;for(int x:g)ok&=x==PackageManager.PERMISSION_GRANTED;if(ok)startScan();else toast("Sin permisos no es posible buscar dispositivos");}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onStop(){
        super.onStop();
        // An active MuSE receiver keeps vibrating after advertisements cease.
        // Explicitly send STOP; the physical remote remains the fail-safe.
        if(museArmed)stopEverything();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        stopScan();
        if(gatt!=null){try{gatt.disconnect();}catch(SecurityException ignored){}gatt.close();}
        // Let a pending STOP packet finish before closing the advertiser.
        if(muse!=null)new Handler(Looper.getMainLooper()).postDelayed(()->muse.close(),3100);
        super.onDestroy();
    }
}
