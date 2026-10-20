package com.vknxworks.filesctrl.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.MainActivity
import com.vknxworks.filesctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while file operations run, with a progress notification. Holds a wake lock and a
 * Wi-Fi lock so transfers to NFS shares keep going with the screen off. Stops itself when nothing is running.
 */
class OpService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        App.init(this)
        createChannels(this)
        try {
            startForeground(NOTIF_ID, build("Starting…", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (_: Exception) {
            // Android 15+ daily data-sync budget used up (or started from the background): tasks still run
            // while the app is open, just without the keep-alive.
            stopSelf()
            return
        }
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FilesCtrl:ops").apply { acquire(6 * 60 * 60 * 1000L) }
        wifi = try {
            @Suppress("DEPRECATION")
            getSystemService(WifiManager::class.java).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FilesCtrl:ops")
                .apply { acquire() }
        } catch (_: Exception) { null }

        scope.launch {
            // Poll rather than observe: snapshot changes made off the main thread aren't guaranteed to be
            // delivered while no UI is on screen.
            while (true) {
                val running = Ops.active
                if (running.isEmpty()) {
                    delay(800) // Give a just-queued task a moment to register.
                    if (Ops.active.isEmpty()) { finish(); break }
                    continue
                }
                val first = running.first()
                val title = if (running.size == 1) first.title else "${running.size} operations running"
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(title + "\n" + first.detail, first.progress))
                delay(700)
            }
        }
    }

    /** Android 15+: the daily data-sync time budget ran out. Stop cleanly instead of being killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Ops.active.forEach { it.cancel() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Ops.active.forEach { it.cancel() }
        } else {
            // Every startForegroundService() must be answered with startForeground(), even when already running.
            val first = Ops.active.firstOrNull()
            try {
                startForeground(NOTIF_ID, build((first?.title ?: "Working…") + "\n" + (first?.detail ?: ""), first?.progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (_: Exception) { stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, progress: Float?): Notification {
        val lines = text.split('\n')
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, OpService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_files)
            .setContentTitle(lines.first())
            .setContentText(lines.getOrNull(1).orEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .lockScreenSafe(CHANNEL, "File operation in progress")
            .build()
    }

    private fun finish() {
        // Tell the user how it went if the app isn't in front.
        val done = Ops.tasks.firstOrNull()
        if (done != null && done.state != TaskState.Running) {
            val nm = getSystemService(NotificationManager::class.java)
            val text = done.error ?: done.detail
            val n = NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_files)
                .setContentTitle(done.title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .lockScreenSafe(CHANNEL_DONE, "File operation finished")
                .build()
            if (!MainActivity.inForeground) nm.notify(DONE_ID, n)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * File and folder names stay off the lock screen: a secure lock screen shows only [generic]. With the app
     * lock on, the notification isn't shown on the lock screen at all.
     */
    private fun NotificationCompat.Builder.lockScreenSafe(channel: String, generic: String): NotificationCompat.Builder =
        setVisibility(if (com.vknxworks.filesctrl.ui.lock.AppLockState.enabled) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this@OpService, channel)
                    .setSmallIcon(R.drawable.ic_stat_files)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(generic)
                    .build(),
            )

    override fun onDestroy() {
        scope.cancel()
        try { wake?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        try { wifi?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ops"
        private const val CHANNEL_DONE = "ops_done"
        private const val NOTIF_ID = 1
        private const val DONE_ID = 2
        private const val ACTION_CANCEL = "com.vknxworks.filesctrl.CANCEL_OPS"

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "File operations", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished operations", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
