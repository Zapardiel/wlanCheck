package com.honeywell.wlancheck;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by E438447 on 3/16/2018.
 */

public class wlanCheckService extends Service {
    //region "VARIABLES"

    // Constants
    private static String TAG="wlancheck";

    // int Values
    private int PING_INTERVAL = 10000;
    private int CHECK_RSSI_INTERVAL = 5000;
    private int RECONNECT_INTERVAL=12000;
    private int RSSI_LOW_LEVEL=-75;

    // String Values
    private String inet_add = "www.yahoo.es";

    // Flags
    private boolean logCONNECTIVITY=true;
    private boolean logNETWORK_STATE_CHANGE=true;
    private boolean logSUPPLICANT_CONNECTION_CHANGE=true;
    private boolean logSUPPLICANT_STATE_CHANGE=true;
    private boolean logWIFI_STATE_CHANGE=true;
    private boolean chkRssi = true;
    private boolean chkPing = false;
    private boolean chkReconnect=true;
    private boolean sndConnect =true;
    private boolean sndDisconnect =true;
    private boolean sndWaiting =true;
    private boolean sndScanning = true;
    private boolean logRSSI_Active=false;
    private boolean logRSSI_Passive=true;
    private boolean isReconnecting = false;
    private long timeSignalLost=0;
    private long rssi_timestamp=0;
    private int rssi_Level=0;

    // Handlers
    private Handler handler_ping;
    private Handler handler_rssi;
    private Handler handler_reconnect;

    // Connectivity Variables
    ConnectivityManager conManager;
    NetworkInfo netInfo;
    WifiManager wifiManager;
    WifiInfo wifiInfo;
    SupplicantState supplicantState;
    WifiManager.WifiLock wifiLock;

    // Sounds and Media
    MediaPlayer mp_waiting;
    MediaPlayer mp_disconnect;
    MediaPlayer mp_connect;

    //endregion

    //region SERVICE STATUS

    // Knows Service Lifecycle
    private static wlanCheckService instance = null;

    public static boolean isInstanceCreated() {
        return instance != null;
    }
    //endregion

    //region Overidable Methods
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        // Flag to know that the service is running
        instance = this;

        // Loads settings from Ini File
        try {
            iniFile ini = new iniFile("/sdcard/wlancheck.txt");
            sndConnect =ini.getBoolean("RECONNECT", "SOUND_CONNECT", true);
            sndDisconnect =ini.getBoolean("RECONNECT", "SOUND_DISCONNECT", true);
            sndWaiting =ini.getBoolean("RECONNECT", "SOUND_WAITING", true);

            chkPing= ini.getBoolean("PING","ENABLE",false);
            inet_add = ini.getString("PING", "NET_ADDR", "www.yahoo.es");
            PING_INTERVAL = ini.getInt("PING", "PING_INTERVAL", 1000);

            chkReconnect = ini.getBoolean("RECONNECT", "ENABLE", true);
            RECONNECT_INTERVAL = ini.getInt("RECONNECT", "RECONNECT_INTERVAL", 12000);

            chkRssi = ini.getBoolean("RSSI", "ENABLE", true);
            CHECK_RSSI_INTERVAL = ini.getInt("RSSI", "CHECK_RSSI_INTERVAL", 5000);
            RSSI_LOW_LEVEL= ini.getInt("RSSI", "RSSI_LOW_LEVEL", -75);
            logRSSI_Active = ini.getBoolean("RSSI", "LOG_ACTIVE_RSSI", false);
            logRSSI_Passive =ini.getBoolean("RSSI", "LOG_PASSIVE_RSSI", true);
            sndScanning = ini.getBoolean("RSSI","SOUND_SCANNING", true);
            logCONNECTIVITY = ini.getBoolean("LOG DETAIL", "CONNECTIVITY", true);
            logNETWORK_STATE_CHANGE = ini.getBoolean("LOG DETAIL", "NETWORK_STATE_CHANGE", true);
            logSUPPLICANT_CONNECTION_CHANGE = ini.getBoolean("LOG DETAIL", "SUPPLICANT_CONNECTION_CHANGE", true);
            logSUPPLICANT_STATE_CHANGE = ini.getBoolean("LOG DETAIL", "SUPPLICANT_STATE_CHANGE", true);
            logWIFI_STATE_CHANGE = ini.getBoolean("LOG DETAIL", "WIFI_STATE_CHANGE", true);

        } catch (IOException ex) {
            Log.e(TAG, "Ini File does not exists");
            appendLog("Ini File does not exists");
        }

        // Launch Handler on service Startup
        handler_ping = new Handler();
        handler_rssi = new Handler();
        handler_reconnect=new Handler();

        if (chkPing) {
            appendLog("Ping: " + parsePingResults(ping(inet_add)));
            handler_ping.postDelayed(runPing_Runnable, PING_INTERVAL);
        }

        if (chkRssi) {
            handler_rssi.postDelayed(runRssiCheck_Runnable, CHECK_RSSI_INTERVAL);
        }

        if (chkReconnect){
            handler_reconnect.postDelayed(runReconnect_Runnable, 1000);
        }

        // Registering Receivers
        this.registerReceiver(this.newWiFiState_Receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        this.registerReceiver(this.newWiFiState_Receiver, new IntentFilter(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION));
        this.registerReceiver(this.newWiFiState_Receiver, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
        this.registerReceiver(this.newWiFiState_Receiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        this.registerReceiver(this.newWiFiState_Receiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));

        this.registerReceiver(this.newRssi_Receiver, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        this.registerReceiver(this.newRssi_Receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // USB detection
        registerReceiver(usbReceiver, new IntentFilter("android.hardware.usb.action.USB_STATE"));

        // Sounds
        mp_waiting =  MediaPlayer.create(this, R.raw.click);
        mp_disconnect =  MediaPlayer.create(this,R.raw.buzz );
        mp_connect = MediaPlayer.create(this,R.raw.ding);

        // WiFiLock for HighPerformance
        wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HIGH_WIFI");
        wifiLock.acquire();
    }

    @Override
    public void onDestroy() {
        // flag to quote service status
        instance = null;

        // unregister Broadcast Receivers
        try {
            if(newRssi_Receiver!=null) unregisterReceiver(newRssi_Receiver);
            if(newWiFiState_Receiver!=null) unregisterReceiver(newWiFiState_Receiver);
            if(usbReceiver!=null) unregisterReceiver(usbReceiver);
        } catch (Exception ex) {
            Log.e(TAG, "Exception Destroying Service (Unregistering Broadcasts): " + ex.getMessage());
        }
        // removeCallBacks
        try {
            if(runPing_Runnable!=null) handler_ping.removeCallbacks(runPing_Runnable);
            if(runRssiCheck_Runnable!=null) handler_rssi.removeCallbacks(runRssiCheck_Runnable);
            if(runReconnect_Runnable!=null) handler_reconnect.removeCallbacks(runReconnect_Runnable);
        } catch (Exception ex) {
            Log.e(TAG, "Exception Destroying Service (Removing Handlers): " + ex.getMessage());
        }

        // remove WifiLock
        wifiLock.release();
    }
//endregion



    //region BROADCAST RECEIVER

    // Record all WiFi Status Changes in a Log
    private BroadcastReceiver newWiFiState_Receiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            conManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiInfo = wifiManager.getConnectionInfo();
            supplicantState = (SupplicantState)intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);

            //Log Intents ACTIONs
            //Log.i(TAG,intent.getAction().toString());

            // Authentication error
            int supl_error=intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
            if(supl_error==WifiManager.ERROR_AUTHENTICATING){
                Log.i(TAG, "ERROR_AUTHENTICATING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                appendLog("ERROR_AUTHENTICATING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                try {
                    if(sndDisconnect){
                        mp_disconnect = MediaPlayer.create(context, R.raw.buzz);
                        mp_disconnect.start();
                        mp_disconnect.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                mp_disconnect.stop();
                                mp_disconnect.release();                   // release object from memory
                            }
                        });}
                } catch (Exception e) {
                    appendLog("Exception MediaPlayer: " + e.getMessage());
                }
                wifiManager.setWifiEnabled(false);
                isReconnecting = true;                                // Starts an new Association
                timeSignalLost = 0;                                   // Avoids that Runnable Reconnecting will shutoff the radio again
            }

            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)&& logCONNECTIVITY){
                if(conManager.getActiveNetworkInfo()!= null){
                    try{
                        if (conManager.getActiveNetworkInfo().isAvailable()){
                            Log.d(TAG,"CONNECTIVITY_ACTION--> Available");
                            appendLog("CONNECTIVITY_ACTION--> Available");
                        }
                        else if (conManager.getActiveNetworkInfo().isConnected()){
                            Log.d(TAG,"CONNECTIVITY_ACTION--> Connected");
                            appendLog("CONNECTIVITY_ACTION--> Connected");
                        }
                        else if (conManager.getActiveNetworkInfo().isConnectedOrConnecting()){
                            Log.d(TAG,"CONNECTIVITY_ACTION--> Connected or Connecting");
                            appendLog("CONNECTIVITY_ACTION--> Connected or Connecting");
                        }
                        else if (conManager.getActiveNetworkInfo().isFailover()){
                            Log.d(TAG,"CONNECTIVITY_ACTION--> Failed Over");
                            appendLog("CONNECTIVITY_ACTION--> Failed Over");
                        }
                        else if (conManager.getActiveNetworkInfo().isRoaming()){
                            Log.d(TAG,"CONNECTIVITY_ACTION--> Roaming");
                            appendLog("CONNECTIVITY_ACTION--> Roaming");
                        }
                    } catch(Exception ex){
                        Log.d(TAG,"CONNECTIVITY_ACTION--> Exception: " + ex.getMessage());
                        appendLog("CONNECTIVITY_ACTION--> Exception: " + ex.getMessage());
                    }
                }else {
                    Log.d(TAG,"CONNECTIVITY_ACTION--> ActiveNetwork is not WiFi");
                    appendLog("CONNECTIVITY_ACTION--> ActiveNetwork is not WiFi");
                }
            }
            else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)&&logNETWORK_STATE_CHANGE){
                if(netInfo.isConnected()) {
                    Log.i(TAG, "NETWORK_STATE_CHANGED --> Connected");
                    appendLog("NETWORK_STATE_CHANGED --> Connected");
                } else {
                    Log.i(TAG, "NETWORK_STATE_CHANGED --> Disconnected");
                    appendLog("NETWORK_STATE_CHANGED --> Disconnected");
                }
            }
            // SUPPLICANT CONNECTION STATE
            else if (intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION) && logSUPPLICANT_CONNECTION_CHANGE) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)){
                    Log.i(TAG, "SUPPLICANT_CONNECTION_CHANGE ---> Connected");
                    appendLog("SUPPLICANT_CONNECTION_CHANGE ---> Connected");
                } else{
                    Log.i(TAG, "SUPPLICANT_CONNECTION_CHANGE ---> Disconnected");
                    appendLog("SUPPLICANT_CONNECTION_CHANGE ---> Disconnected");
                }
                
            }
            // SUPPLICANT STATE CHANGE
            else if (intent.getAction().equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION) && logSUPPLICANT_STATE_CHANGE){

                SupplicantState supplicantState = (SupplicantState)intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);

                // Supplicant State
                switch (supplicantState){
                    case INACTIVE:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Inactive");
                        appendLog("SUPPLICANTSTATE ---> Innactive");
                        break;
                    case INTERFACE_DISABLED:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Disabled");
                        appendLog("SUPPLICANTSTATE ---> Disabled");
                        break;
                    case DORMANT:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Dormant");
                        appendLog("SUPPLICANTSTATE ---> Dormant");
                        break;
                    case INVALID:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Invalid");
                        appendLog("SUPPLICANTSTATE ---> Invalid");
                        break;
                    case SCANNING:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Scanning");
                        appendLog("SUPPLICANTSTATE ---> Scanning");
                        break;
                    case ASSOCIATED:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Associated");
                        appendLog("SUPPLICANTSTATE ---> Associated");
                        break;
                    case ASSOCIATING:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Associating");
                        appendLog("SUPPLICANTSTATE ---> Associating");
                        break;
                    case AUTHENTICATING:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Authenticating");
                        appendLog("SUPPLICANTSTATE ---> Authenticating");
                        break;
                    case FOUR_WAY_HANDSHAKE:
                        Log.i(TAG, "SUPPLICANTSTATE ---> 4Way Handshake");
                        appendLog("SUPPLICANTSTATE ---> 4Way Handshake");
                        break;
                    case COMPLETED:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Completed");
                        appendLog("SUPPLICANTSTATE ---> Completed");
                        isReconnecting=false;                                     // Ended the first Association
                        timeSignalLost=0;                                         // Time when the signal was lost: Low Rssi or isReconnecting=true

                        // Sound (Ding)
                        try {
                            if(sndConnect){
                                mp_connect = MediaPlayer.create(context, R.raw.ding);
                                mp_connect.start();
                                mp_connect.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                    public void onCompletion(MediaPlayer mp) {
                                        mp_connect.stop();
                                        mp_connect.release();                             // release object from memory
                                    }
                                });}
                        } catch (Exception e) {
                            appendLog("Exception MediaPlayer: " + e.getMessage());
                        }
                        break;
                    case DISCONNECTED:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Disconnected. Switching off the radio.");
                        appendLog("SUPPLICANT STATE ---> Disconnected. Switching off the radio.");
                        if(!isReconnecting) {
                            try {
                                if(sndDisconnect){
                                    mp_disconnect = MediaPlayer.create(context, R.raw.buzz);
                                    mp_disconnect.start();
                                    mp_disconnect.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                        public void onCompletion(MediaPlayer mp) {
                                            mp_disconnect.stop();
                                            mp_disconnect.release();                    // release object from memory
                                        }
                                    });}
                            } catch (Exception e) {
                                appendLog("Exception MediaPlayer: " + e.getMessage());
                            }

                            wifiManager.setWifiEnabled(false);
                            isReconnecting = true;                                      // Starts an new Association
                            timeSignalLost = 0;                                         // Avoids that Runnable Reconnecting will shutoff the radio again
                        }
                        break;
                    case GROUP_HANDSHAKE:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Group Handshake");
                        appendLog("SUPPLICANTSTATE ---> Group Handshake");
                        break;
                    case UNINITIALIZED:
                        Log.i(TAG, "SUPPLICANTSTATE ---> Uninitialized");
                        appendLog("SUPPLICANTSTATE ---> Uninitialized");
                        break;
                }

            }
            // WIFI State Change
            else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION) && logWIFI_STATE_CHANGE){
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (wifiState == WifiManager.WIFI_STATE_DISABLED){
                    Log.d(TAG,"WIFI STATE -> DISABLED");
                    appendLog("WIFI STATE -> DISABLED");
                } else if (wifiState == WifiManager.WIFI_STATE_ENABLED){
                    Log.d(TAG,"WIFI STATE -> ENABLED");
                    appendLog("WIFI STATE -> ENABLED");
                } else if (wifiState == WifiManager.WIFI_STATE_ENABLING){
                    Log.d(TAG,"WIFI STATE -> ENABLING");
                    appendLog("WIFI STATE -> ENABLING");
                }
            }
        }
    };

    // RSSI Changes
    private BroadcastReceiver newRssi_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiInfo = wifiManager.getConnectionInfo();


            // RSSI Passively.
            // ConnectionInfo is a local copy of last time we made a Scan request (startScan)
            if (intent.getAction().equals(WifiManager.RSSI_CHANGED_ACTION)) {
                try{
                    int newRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
                    if(logRSSI_Passive){
                        Log.i(TAG, "RSSI Passive -> " + String.valueOf(wifiInfo.getRssi()));
                        appendLog("RSSI Passive -> " + String.valueOf(wifiInfo.getRssi()));
                    }
                }catch (Exception ex){
                    appendLog("[RSSI Passively]Exception: " + ex.getMessage());
                }

                // RSSI returned by ScanResults.
                // This is the only refreshed value of ConnectionInfo
            } else if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {

                try {
                    if (sndScanning) {
                        mp_disconnect = MediaPlayer.create(wlanCheckService.this, R.raw.beep02);
                        mp_disconnect.start();
                        mp_disconnect.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                mp_disconnect.stop();
                                mp_disconnect.release();                   // release object from memory
                            }
                        });
                    }
                } catch (Exception e) {
                    appendLog("Exception MediaPlayer: " + e.getMessage());
                }


                try{
                    List<ScanResult> scanResult = wifiManager.getScanResults();

                    for (ScanResult scan : scanResult) {
                        if((scan.BSSID==null) || (wifiInfo.getBSSID()==null)){
                            Log.d(TAG,"BSSID es null");
                            continue;
                        }
                        // Check that result is the current BSSID
                        if(wifiInfo.getBSSID().equals(scan.BSSID)) {

                            rssi_Level=scan.level;
                            rssi_timestamp=scan.timestamp;

                            if(logRSSI_Active) {
                                Log.d(TAG, "RSSI Active -> " + String.valueOf(scan.level) + " dBm " + scan.SSID + " of " + scanResult.size() + " BSSIDs");
                                appendLog("RSSI Active -> " + String.valueOf(scan.level) + " dBm " + scan.SSID + " of " + scanResult.size() + " BSSIDs");
                            }
                            // If signal is close to the RSSI_LOW_LEVEL, try to reassociate before reconnect
                            if (scan.level<RSSI_LOW_LEVEL+10) {
                                wifiManager.reconnect();
//                                Log.d(TAG,"Low Signal (" + String.valueOf(scan.level)+ ") - Reassociating");
//                                appendLog("Low Signal (" + String.valueOf(scan.level)+ ") - Reassociating");
                            }
                        }
                    }
                }
                catch (Exception ex){
                    appendLog("[RRSI returned by ScanResuslts]Exception: " + ex.getMessage());
                }
            } else {
                appendLog("<<<< Unknown Intent: " + intent.getAction().toString() + " >>>>>");
            }

            // Is "Scanning" enabled?
            // WiFi Scanning is enabled if it's sets on Location-->Scanning
//            if(wifiManager.isScanAlwaysAvailable()){
//                appendLog("isScanAlwaysAvailable : true");
//            }
        }
    };

    // Thrown by Intent android.hardware.usb.action.USB_STATE in order
    // In order to refresh the MediaScanner, and see the logs from a PC
    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent usbIntent) {
            try {
                Date curDate = new Date();
                SimpleDateFormat format_date = new SimpleDateFormat("yyyyMMdd");

                boolean bConnected = usbIntent.getExtras().getBoolean("connected");
                if (bConnected) {
                    appendLog("USB Cable Plugged");
                    File logFile = new File("sdcard","wlancheck_" + format_date.format(curDate) + ".txt");
                    Uri uri = Uri.fromFile(logFile);
                    Intent scanFileIntent = new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                    sendBroadcast(scanFileIntent);
                }else{
                    appendLog("USB Cable Unplugged");
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    };
    //endregion

    //region RUNNABLES
    private Runnable runPing_Runnable = new Runnable() {
        @Override
        public void run() {
            // Do network checka
            appendLog("Ping: " + parsePingResults(ping(inet_add)));
            handler_ping.postDelayed(runPing_Runnable, PING_INTERVAL);
        }
    };

    private Runnable runRssiCheck_Runnable = new Runnable() {
        @Override
        public void run() {
            // Scan BSSIDs when radio is ON and connected,
            try{
            if(wifiManager.isWifiEnabled()) {
                // Do Network scan

                // Time to time startScan is not reporting fresh info
                if (SystemClock.elapsedRealtime()>(rssi_timestamp +CHECK_RSSI_INTERVAL+1)){
                    Log.d(TAG,"Scanning was stopped. Last SystemScan was at " + String.valueOf((SystemClock.elapsedRealtime()- rssi_timestamp)/1000));
                    appendLog("Scanning was stopped. Last SystemScan was at " + String.valueOf((SystemClock.elapsedRealtime()- rssi_timestamp)/1000));
                    // There is a hidden method up to API 2.3 to force scan. But it's not longer available
                    // Method startScanActiveMethod = WifiManager.class.getMethod("startScanActive");
                    // startScanActiveMethod.invoke(wifiManager);
                }
                if(logRSSI_Active) {
                    Log.d(TAG, "<<<< ----  Scanning ---- >>>>");
                    appendLog("<<<< ----  Scanning ---- >>>>");
                }
                wifiManager.startScan();

            }
            }catch (Exception ex){
                Log.d(TAG,"[runRssiCheck_Runnable]Exception: " + ex.getMessage());
                appendLog("[runRssiCheck_Runnable]Exception: " + ex.getMessage());
            }

            handler_rssi.postDelayed(runRssiCheck_Runnable, CHECK_RSSI_INTERVAL);
        }
    };



    // Reconnection is based on two Flags:
    //      "isReconnecting" which is triggered with low signal,
    //      "timeSignalLost" is a flag activated (>0) when it's connected with low Rssi signal or when is trying to connect
    //          The initial state is zero. And should be left in Zero when it reaches the max (RECONNECT_INTERVAL)
    //          It's only valid if: Low Rssi or isReconnecting=true

    private Runnable runReconnect_Runnable = new Runnable() {
        @Override
        public void run() {

            wifiManager = (WifiManager) getApplicationContext().getSystemService(wlanCheckService.this.WIFI_SERVICE);
            wifiInfo = wifiManager.getConnectionInfo();

            // Always wlan Radio ON.
            if(!wifiManager.isWifiEnabled()) {
                Log.d(TAG,"Switching ON Wlan Radio. Reconneting...");
                appendLog("Switching ON Wlan Radio. Reconnecting...");
                wifiManager.setWifiEnabled(true);
            }


            // rssi_timestamp it's initialized in newRssi_Receiver, with the last scan time-stamp.
            // Android does only PASSIVE scanning (Beacon Loss), and only does a ACTIVE Scanning on a Reconnection (after a Signal Loss or first time connect)
            // If rssi_timestamp is not inilitialized has value 0, and then we cannot evaluate a valid Rssi value
            Log.d(TAG, "rssi_Level: " + rssi_Level);
            Log.d(TAG,String.format("Last Scan was: %.2f secs",(float)(SystemClock.elapsedRealtime()-(rssi_timestamp / 1000))/1000));

            // It's already connected, but the Signal is low OR the latest scan result timestamp was very old.
            try {
                if (!isReconnecting) {
                    //Connected with Low Rssi signal level for too long OR Rssi value is no longer valid because it's too old (>RECONNECT_INTERVAL)
                    //Both issues were caused by a lack of ACTIVE scanning since a while (RECONNECT_INTERVAL)
                    if ((rssi_Level < RSSI_LOW_LEVEL) || (SystemClock.elapsedRealtime() - (rssi_timestamp / 1000)) > RECONNECT_INTERVAL) {
                        try {

                            if ((timeSignalLost > 0) && (System.currentTimeMillis() > timeSignalLost + RECONNECT_INTERVAL)) {
                                appendLog("It was " + RECONNECT_INTERVAL / 1000 + " seconds with Low Signal(" + rssi_Level + "dbm). Switching off the radio.");
                                Log.d(TAG, "It was " + RECONNECT_INTERVAL / 1000 + " seconds with Low Signal(" + rssi_Level + "dbm). Switching off the radio.");
                                wifiManager.setWifiEnabled(false);
                                isReconnecting = true;                // With this Flag we avoid to trigger another Radio switch off in DISCONNECT
                                timeSignalLost = 0;                   // Inactivate this Flag saying that the signal is not lost (recovered)
                                try {
                                    if (sndDisconnect) {
                                        mp_disconnect = MediaPlayer.create(wlanCheckService.this, R.raw.buzz);
                                        mp_disconnect.start();
                                        mp_disconnect.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                            public void onCompletion(MediaPlayer mp) {
                                                mp_disconnect.stop();
                                                mp_disconnect.release();                   // release object from memory
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    appendLog("Exception MediaPlayer: " + e.getMessage());
                                }
                            }
                            // Very first time that the signal is low
                            else if (timeSignalLost == 0) {
                                if((SystemClock.elapsedRealtime() - (rssi_timestamp / 1000)) > RECONNECT_INTERVAL){
                                    // ForceScanning???
                                    appendLog(String.format("[+][+][+][+][+][+] Signal too old. Last scan was at:  %.2f secs - Waiting %.2f secs to recover the signal.", (float)((SystemClock.elapsedRealtime() - (rssi_timestamp / 1000))/1000), (float)RECONNECT_INTERVAL / 1000));
                                    Log.d(TAG, String.format("[+][+][+][+][+][+] Signal too old. Last scan was at:  %.2f secs - Waiting %.2f secs to recover the signal.", (float)((SystemClock.elapsedRealtime() - (rssi_timestamp / 1000))/1000), (float)RECONNECT_INTERVAL / 1000));

                                }else{
                                    appendLog("Low signal(" + rssi_Level + "dbm), waiting " + RECONNECT_INTERVAL / 1000 + "secs to recover the signal.");
                                    Log.d(TAG, "Low signal(" + rssi_Level + "dbm), waiting " + RECONNECT_INTERVAL / 1000 + "secs to recover the signal.");
                                }
                                timeSignalLost = System.currentTimeMillis();
                            } else {
                                // Waiting to recover a good signal level
                            }
                        }catch (Exception ex){
                            appendLog("[runReconnect_Runnable-(rssi_Level < RSSI_LOW_LEVEL)]Exception" + ex.getMessage());
                        }
                    }

                    // Signal was recovered without disconnect, so we didn't switched off the radio
                    else {

                        if (timeSignalLost > 0) {
                            Log.d(TAG, "Good signal recovered in less than " + RECONNECT_INTERVAL / 1000 + "secs");
                            appendLog("Good signal recovered in less than " + RECONNECT_INTERVAL / 1000 + "secs");
                            timeSignalLost = 0;
                        }
                        // Desired state: not Reconnecting and good Rssi.
                        else {
                            //PRAY!!!
                        }
                    }
                } else {
                    // It's Reconnecting.
                    // Triggered by the above IF branch (because it was connected with low Rssi a few seconds ago)
                    // It's independant of the RSSI level, the goal is to get connected at any Rssi level.
                    // If at anytime the radio gets connnected, isReconnecting will be false and it won't enter in this branch
                    // But it takes more than 12secs to get connected, -> Switching off the radio again
                    if ((timeSignalLost > 0) && (System.currentTimeMillis() > timeSignalLost + RECONNECT_INTERVAL)) {
                        Log.d(TAG, "Reconnecting took too Long (>" + RECONNECT_INTERVAL / 1000 + "sec). Switching Off Wlan Radio.");
                        appendLog("Reconnecting took too Long (>" + RECONNECT_INTERVAL / 1000 + "sec). Switching Off Wlan Radio.");

                        wifiManager.setWifiEnabled(false);
                        timeSignalLost = 0;
                    }
                    // The radio was recently set Off and this Runnable set it ON, so let's start counting!
                    // This is the second state after the "Bad Rssi signal level for too long." state.
                    else if (timeSignalLost == 0) {
                        // Reset the timer
                        appendLog("Trying to Reconnect...");
                        Log.d(TAG, "Trying to Reconnect...");
                        timeSignalLost = System.currentTimeMillis();
                    } else {
                        //Waiting to get connected
                        try {
                            if(sndWaiting){
                                mp_waiting = MediaPlayer.create(wlanCheckService.this, R.raw.click);
                                mp_waiting.start();
                                mp_waiting.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                    public void onCompletion(MediaPlayer mp) {
                                        mp_waiting.stop();
                                        mp_waiting.release();                   // release object from memory
                                    }
                                });}
                        } catch (Exception e) {
                            appendLog("Exception MediaPlayer: " + e.getMessage());
                        }
                    }
                }
            }catch (Exception ex){
                appendLog("[runReconnect_Runnable]Exception: " + ex.getMessage());
            }

            // It's set too often because in this routine we always check system time.
            // So we are not overloading the service.
            handler_reconnect.postDelayed(runReconnect_Runnable, 1000);
        }
    };
    //endregion

    //region LOG FILES
    public void appendLog(String info) {
        Date curDate = new Date();
        SimpleDateFormat format_date = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat format_hour = new SimpleDateFormat("hh:mm:ss");
        String DateToStr = format_hour.format(curDate);
        File logFile = new File("sdcard","wlancheck_" + format_date.format(curDate) + ".txt");

        if (!logFile.exists()) {
            // delete old files wlancheck_xxx.txt
            deleteOldFiles(5);

            // create a new file wlancheck_yyyyMMdd.txt
            try {
                logFile.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                Toast.makeText(wlanCheckService.this, "Impossible to write a LOG File!!", Toast.LENGTH_LONG).show();
            }
        }

        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(DateToStr + ": " + info);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    class Pair implements Comparable {
        public long t;
        public File f;

        public Pair(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(Object o) {
            long u = ((Pair) o).t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    };

    private void deleteOldFiles(int maxFiles)
    {
        File dir = new File("sdcard");
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches("wlancheck_(.*).txt");
            }
        });

        Pair[] pairs = new Pair[files.length];
        for (int i = 0; i < files.length; i++)
            pairs[i] = new Pair(files[i]);

        // Sort them by timestamp.
        Arrays.sort(pairs);

        // Take the sorted pairs and extract only the file part, discarding the timestamp.
        if (files.length>maxFiles) {
            for ( int i = 0; i < files.length - maxFiles; i++) {
                pairs[i].f.delete();
            }
        }
    }

    //endregion

    //region PING
    public String ping(String url) {
        String str = "";
        try {
            Process process = Runtime.getRuntime().exec(
                    "/system/bin/ping -c 1 " + url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));
            int i;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((i = reader.read(buffer)) > 0)
                output.append(buffer, 0, i);
            reader.close();

            // body.append(output.toString()+"\n");
            str = output.toString();
            // Log.d(TAG, str);
        } catch (Exception e) {
            appendLog("Exception: " + e.getMessage());
        }
        return str;
    }

    private String parsePingResults(String pingResults) {
        try {
            int ptime = pingResults.indexOf("time=");
            int pmill = pingResults.indexOf("ms");
            if ((pingResults.indexOf("unknown") >= 0) || ((pmill > ptime) && (ptime > 0) && (pmill > 0))) {
                return pingResults.substring(ptime, pmill + 2);
            } else {
                return "No Answer from Host";
            }
        } catch (Exception ex) {
            appendLog("Exception parsing Ping Statistics");
        }
        return "No Answer from Host";
    }
    //endregion
}

