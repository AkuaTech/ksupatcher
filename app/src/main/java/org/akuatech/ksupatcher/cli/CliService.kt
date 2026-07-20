package org.akuatech.ksupatcher.cli

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.akuatech.ksupatcher.data.SettingsRepository
import org.akuatech.ksupatcher.network.DownloadRepository
import org.akuatech.ksupatcher.network.GitHubReleaseRepository
import org.akuatech.ksupatcher.viewmodel.KsuEngine
import org.akuatech.ksupatcher.viewmodel.KsuVariant

class CliService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        val action = intent?.getStringExtra("action")?.lowercase()
        val lkmMode = when (action) {
            "lkm", "install" -> true
            "ota" -> false
            else -> {
                Log.e(TAG, "unknown action: $action (expected ota, lkm or install)")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val variant = when (intent.getStringExtra("variant")?.lowercase()) {
            "ksun", "next", "kernelsu-next" -> KsuVariant.KSUN
            else -> KsuVariant.KSU
        }
        val kmiArg = intent.getStringExtra("kmi")
        val allowShell = intent.getBooleanExtra("allow_shell", false)
        val enableAdbd = intent.getBooleanExtra("enable_adbd", false)

        val client = OkHttpClient()
        val engine = KsuEngine(application, DownloadRepository(client), GitHubReleaseRepository(client))
        val settings = SettingsRepository(applicationContext)

        scope.launch {
            val kmi = kmiArg ?: settings.kmiFlow.first()
            Log.i(TAG, "action=$action variant=$variant kmi=$kmi allowShell=$allowShell enableAdbd=$enableAdbd")
            val result = engine.runSlotPatch(
                lkmMode = lkmMode,
                variant = variant,
                kmi = kmi,
                moduleOverride = null,
                allowShell = allowShell,
                enableAdbd = enableAdbd,
                onLine = { Log.i(TAG, it) },
                onPhase = { Log.i(TAG, "phase: $it") },
                onSlots = { current, next -> Log.i(TAG, "slot $current -> $next") },
            )
            if (result.isSuccess) {
                Log.i(TAG, "done, reboot to apply")
            } else {
                Log.e(TAG, "failed: ${result.exceptionOrNull()?.message}")
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "KSUPatcher CLI", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle("KSUPatcher")
            .setContentText("Running patch from CLI")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private companion object {
        const val TAG = "KsuPatcherCli"
        const val CHANNEL = "ksupatcher_cli"
        const val NOTIF_ID = 42
    }
}
