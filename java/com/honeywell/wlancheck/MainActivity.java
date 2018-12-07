package com.honeywell.wlancheck;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends AppCompatActivity {

    //region Variables
    private static final int MULTIPLE_REQUESTS= 123;
    private View view;
    private Handler handler_requestPermission;
    private static String TAG = "wlanCheck";
    //endregion

    //region Overridable Methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {       // Only for Android Marshmallow or higher
            // Request External Storage Permission
            handler_requestPermission= new Handler();
            if (!check_Permissions()) {                               // Checks if the App needs a permission
                handler_requestPermission.postDelayed(ask_Permissions, 50);
            }
            // Permissions already granted
            else
            {   runService(this);
                try{
                    Thread.sleep(2000);
                }catch (InterruptedException  ex){
                    finish();
                }
                finish();
            }
        // Android 5 or lower
        } else {
            runService(this);
            try{
                Thread.sleep(100);
            }catch (InterruptedException  ex){
                finish();
            }
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.e(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy");
        // removeCallBacks
        try {
            if(handler_requestPermission!=null) handler_requestPermission.removeCallbacks(ask_Permissions);
        } catch (Exception ex) {
            Log.e(TAG, "Exception Destroying Service (Removing Handlers): " + ex.getMessage());
        }
    }

    //endregion
    
    //region Service Methods
    private void runService(Context context) {
        Intent serviceLauncher = new Intent(context, wlanCheckService.class);
        if (!wlanCheckService.isInstanceCreated()) {
            Log.e(TAG,"Service Running!!");
            context.startService(serviceLauncher);
            notifService();
        } else {
            Log.e(TAG, "Service Stopped!!");
            context.stopService(serviceLauncher);
        }
    }
    //endregion
    
    //region ANDROID 6, REQUEST PERMISSIONS
    // Checks that the Permission is not granted yet.
    private boolean check_Permissions() {
        return (ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(getApplicationContext(),ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED) ;
    }

    // askPermissions is launched just one time after onCreate, because it cannot be called on that method.
    private Runnable ask_Permissions = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG,"request_Permissions");
            request_Permissions();
        }
    };

    // Ask for Permission REQUEST_WRITE_STORAGE
    private void request_Permissions() {

        Log.d(TAG,"request_ExternalStoragePermission");
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d(TAG,"request_ExternalStoragePermission->RequestRationale");
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Provide Permissions to work!")
                    .setMessage("Please press OK in the next Dialogs in order to let the app work")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //Prompt the user once explanation has been shown
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION},
                                    MULTIPLE_REQUESTS);
                        }
                    })
                    .create()
                    .show();
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, MULTIPLE_REQUESTS);
        }
    }

    // Callback that is called when the user clicks on Deny or Allow
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MULTIPLE_REQUESTS:
                if (grantResults.length > 0) {

                    boolean writeAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean locationAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if (!writeAccepted || !locationAccepted) {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            // Is it just Denied but not checked "Never Ask Again"?  --> Ask again
                            showMessageOKCancel("If you don't accept all permissions, the App won't work!. Press ALLOW",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            restartApp();
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Toast.makeText(MainActivity.this, "If you don't GRANT permission, it won't work.", Toast.LENGTH_SHORT).show();
                                            finish();
                                        }
                                    });
                            return;
                        } else {
                            // It was checked "Never Ask Again!"
                            Toast.makeText(MainActivity.this, "Sorry! Go to Apps->Permissions", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        runService(MainActivity.this);
                        finish();
                        Log.e(TAG, "Permissions Granted");
                    }
                }
                break;
        }
    }
    //endregion

    //region NOTIFICATION

    //Create Notification
    //When it's triggered the pendingIntent will launch MainActivity but as there's already an instance created it will close the App
    private void notifService() {
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_wlancheck_notif)
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentTitle("Wlan Check")
                .setTicker("The Service is Running!!")
                .setContentText("The Service is Running!!")
                .setContentIntent(pendingIntent);

        Notification not = mBuilder.build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, not);
    }
    //endregion

    //region UTILS
    //Show Dialog
    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener koListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", koListener)
                .create()
                .show();
    }

    //Restart the App
    private void restartApp() {
        Intent i = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }
    //endregion
}
