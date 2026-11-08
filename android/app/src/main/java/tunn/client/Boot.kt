package tunn.client

import android.app.Application
import android.os.Handler
import android.os.Looper
import libv2ray.Libv2ray
import java.io.File
import java.security.SecureRandom
import java.util.Base64

object Boot {
    fun start(app: Application) {
        TunnApp.appContext = app
        J.init(app)
        val main = currentProcess(app) == app.packageName
        if (main) {
            TunnApp.state = AppState(app)
            Thread { Plugins.seedBuiltins(app) }.start()
        }
        Handler(Looper.getMainLooper()).postDelayed({ Layer.healthy(app) }, 20_000L)
        try {
            val prefs = app.getSharedPreferences("tunn", android.content.Context.MODE_PRIVATE)
            var key = prefs.getString("core_key", null)
            if (key == null) {
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                if (main) prefs.edit().putString("core_key", key).apply()
            }
            Libv2ray.initCoreEnv(app.filesDir.absolutePath, key)
        } catch (e: Throwable) {
        }
    }

    private fun currentProcess(app: Application): String {
        return try {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        } catch (e: Throwable) {
            app.packageName
        }
    }
}
