package com.pulsecontrol.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;

/**
 * MuSE / Love Spouse legacy advertiser for a single documented frame family.
 * Important: protocol is broadcast-only. We cannot discover, pair, select,
 * confirm receipt from, or read the battery of the physical receiver.
 */
public final class MuseTransmitter {
    public interface Listener { void onStatus(String message); }

    private static final byte[] PREFIX = {
            (byte)0x6d,(byte)0xb6,(byte)0x43,(byte)0xce,
            (byte)0x97,(byte)0xfe,(byte)0x42,(byte)0x7c
    };
    private static final int COMPANY_ID = 0xfff0;
    private static final byte[][] CODES = {
            {(byte)0xe5,(byte)0x15,(byte)0x7d}, // STOP (verified in prior user test)
            {(byte)0xe4,(byte)0x9c,(byte)0x6c}, // low (verified in prior user test)
            {(byte)0xe7,(byte)0x07,(byte)0x5e}, // medium, documented, not device-tested
            {(byte)0xe6,(byte)0x8e,(byte)0x4f}  // high, documented, not device-tested
    };
    private static final long LEVEL_TRANSMIT_MS = 1200;
    private static final long STOP_TRANSMIT_MS = 2500; // duration proven effective in previous test
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private final Context context;
    private final Listener listener;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback currentCallback;
    private Runnable pendingStart;
    private Runnable pendingFinish;
    private long lastCommandMs = 0;
    private int lastLevel = -1;
    private boolean enabled = true;

    public MuseTransmitter(Context context, BluetoothAdapter adapter, Listener listener) {
        this.context=context.getApplicationContext();
        this.adapter=adapter;
        this.listener=listener;
    }
    public boolean isSupported() {
        try {return adapter!=null && adapter.isEnabled() && adapter.isMultipleAdvertisementSupported();}
        catch(SecurityException ex){return false;}
    }
    public boolean hasPermission() {
        return Build.VERSION.SDK_INT<31 || (context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);
    }
    private void say(String status) { listener.onStatus(status); }
    private byte[] buildFrame(int level) {
        byte[] data=Arrays.copyOf(PREFIX, 11);
        System.arraycopy(CODES[level],0,data,8,3);
        return data;
    }
    private void stopCurrentAdvertisement() {
        if(pendingStart!=null) handler.removeCallbacks(pendingStart);
        if(pendingFinish!=null) handler.removeCallbacks(pendingFinish);
        pendingStart=null; pendingFinish=null;
        if(advertiser!=null && currentCallback!=null) {
            try { advertiser.stopAdvertising(currentCallback); } catch (RuntimeException ignored) { }
        }
        currentCallback=null;
    }
    public void sendLevel(int requestedLevel, boolean force) {
        if(!enabled) return;
        final int level=Math.max(0,Math.min(3,requestedLevel));
        long now=android.os.SystemClock.elapsedRealtime();
        if(!force && level==lastLevel && (now-lastCommandMs)<5000) return;
        if(!hasPermission()){say("Permiso BLUETOOTH_ADVERTISE pendiente");return;}
        if(!isSupported()){say("BLE Advertising no disponible; utiliza STOP del mando");return;}
        lastLevel=level;
        lastCommandMs=now;
        // Legacy advertisements must not overlap, or they may fail with code 3.
        stopCurrentAdvertisement();
        advertiser=adapter.getBluetoothLeAdvertiser();
        if(advertiser==null) { say("No hay transmisor BLE"); return; }
        final byte[] frame=buildFrame(level);
        final AdvertiseSettings settings=new AdvertiseSettings.Builder()
              .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
              .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
              .setConnectable(true).setTimeout(0).build();
        final AdvertiseData payload=new AdvertiseData.Builder()
              .setIncludeTxPowerLevel(false).setIncludeDeviceName(false)
              .addManufacturerData(COMPANY_ID,frame).build();
        final AdvertiseCallback callback=new AdvertiseCallback(){
            @Override public void onStartSuccess(AdvertiseSettings effective) {
                if(currentCallback==this) say("Orden "+level+" emitida; recepción sin confirmar");
            }
            @Override public void onStartFailure(int errorCode) {
                if(currentCallback==this){ currentCallback=null;
                    say("Error de publicidad BLE ("+errorCode+"); usa STOP del mando"); }
            }
        };
        pendingStart=()->{
            pendingStart=null;
            try {
                currentCallback=callback;
                advertiser.startAdvertising(settings,payload,callback);
                pendingFinish=()->{ if(currentCallback==callback) stopCurrentAdvertisement(); };
                handler.postDelayed(pendingFinish,level==0?STOP_TRANSMIT_MS:LEVEL_TRANSMIT_MS);
            } catch (RuntimeException e) {
                currentCallback=null;
                say("Error al enviar BLE; usa STOP del mando");
            }
        };
        handler.postDelayed(pendingStart,90);
    }
    public void stop() { sendLevel(0,true); }
    public void close() {
        enabled=false;
        stopCurrentAdvertisement();
    }
}
