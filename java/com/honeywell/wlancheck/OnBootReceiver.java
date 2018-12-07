package com.honeywell.wlancheck;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;


/**
 * Created by E438447 on 3/16/2018.
 */

public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceLauncher = new Intent(context, wlanCheckService.class);
            notifService(context);
            context.startService(serviceLauncher);
        }
    }

    //region NOTIFICATION
    //region Create Notification
    private void notifService(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

//        Bitmap bitmapIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_battery);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_wlancheck_notif)
//                .setLargeIcon(bitmapIcon)
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentTitle("Wlan Check")
                .setTicker("Tap to stop it!")
                .setContentText("Tap to stop it!")
                .setContentIntent(pendingIntent);

        Notification not = mBuilder.build();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, not);
    }
    //endregion

}