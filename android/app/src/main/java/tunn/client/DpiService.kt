package tunn.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class DpiService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("dpi_search", "Подбор обхода DPI", NotificationManager.IMPORTANCE_LOW))
        }
        val n = Notification.Builder(this, "dpi_search")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Tunn")
            .setContentText("Подбор обхода DPI для вашего оператора")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7701, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(7701, n)
        if (DpiSearch.isRunning()) return START_NOT_STICKY
        Thread {
            DpiSearch.run(this)
            stopForeground(true)
            stopSelf()
        }.start()
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DpiService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
