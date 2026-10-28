package app.fasdeq.vpn

import android.app.Application
import libv2ray.Libv2ray
import java.io.File
import java.security.SecureRandom
import java.util.Base64

class FasdeqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        val main = currentProcess() == packageName
        if (main) {
            state = AppState(this)
            Thread { Plugins.seedBuiltins(this) }.start()
        }
        try {
            val prefs = getSharedPreferences("fasdeq", MODE_PRIVATE)
            var key = prefs.getString("core_key", null)
            if (key == null) {
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                if (main) prefs.edit().putString("core_key", key).apply()
            }
            Libv2ray.initCoreEnv(filesDir.absolutePath, key)
        } catch (e: Throwable) {
        }
    }

    private fun currentProcess(): String {
        return try {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        } catch (e: Throwable) {
            packageName
        }
    }

    companion object {
        lateinit var state: AppState
        lateinit var appContext: android.content.Context
    }
}
