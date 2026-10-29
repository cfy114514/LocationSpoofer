package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vincenthzr.locationspoofer.ui.R

/** Owns the location foreground service while a real GPS route is being recorded. */
internal class RealRouteRecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recorder = RealRouteRecorder.getInstance(applicationContext)
        if (intent?.action == pauseAction(this)) {
            recorder.pause()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!recorder.starting) { stopSelf(startId); return START_NOT_STICKY }
        return try {
            createChannel(this)
            clearStoppedNotification(this)
            val notification = notification(this, recorder.points.size)
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, notification)
            if (!recorder.startGps()) stopSelf(startId)
            START_NOT_STICKY
        } catch (_: RuntimeException) {
            recorder.serviceFailed()
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        RealRouteRecorder.getInstance(applicationContext).serviceStopped()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "real_route_recording"
        private const val ID = 4210
        private const val STOPPED_ID = 4213
        private fun pauseAction(context: Context) = context.packageName + ".REAL_ROUTE_PAUSE"
        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.real_route_notification_channel), NotificationManager.IMPORTANCE_LOW))
            }
        }
        private fun notification(context: Context, count: Int): Notification {
            val pause = Intent(context, RealRouteRecordingService::class.java).setAction(pauseAction(context))
            val pauseIntent = PendingIntent.getService(context, 4211, pause, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(context.getString(R.string.real_route_notification_title))
                .setContentText(context.getString(R.string.real_route_points, count))
                .setOngoing(true).setOnlyAlertOnce(true)
                .addAction(0, context.getString(R.string.real_route_pause), pauseIntent)
            if (launch != null) builder.setContentIntent(PendingIntent.getActivity(context, 4212, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return builder.build()
        }
        private fun clearStoppedNotification(context: Context) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(STOPPED_ID)
        }
        fun notifyStopped(context: Context, reason: String) {
            try {
                createChannel(context)
                val message = when (reason) {
                    "gps" -> R.string.real_route_stopped_gps
                    "limit" -> R.string.real_route_stopped_limit
                    else -> return
                }
                val builder = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle(context.getString(R.string.real_route_stopped_title))
                    .setContentText(context.getString(message))
                    .setAutoCancel(true)
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                    builder.setContentIntent(PendingIntent.getActivity(context, 4212, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(STOPPED_ID, builder.build())
            } catch (_: SecurityException) { }
        }
        fun update(context: Context, count: Int) {
            try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notification(context, count)) }
            catch (_: SecurityException) { }
        }
    }
}
