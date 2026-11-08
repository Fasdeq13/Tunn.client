package tunn.client

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.security.SecureRandom

object Identity {
    val names = listOf("Tunn", "Happ", "v2RayTun", "Incy")
    private val agents = listOf("Tunn/1.9", "Happ/2.0.0/android", "v2raytun/android", "INCY/1.0 (Android)")

    private fun prefs() = TunnApp.appContext.getSharedPreferences("tunn", Context.MODE_PRIVATE)

    fun mask(): Int = prefs().getInt("mask", 1).coerceIn(0, names.size - 1)

    fun setMask(value: Int) {
        prefs().edit().putInt("mask", value.coerceIn(0, names.size - 1)).apply()
    }

    fun hwid(): String {
        val p = prefs()
        val cur = p.getString("hwid", null)
        if (!cur.isNullOrEmpty()) return cur
        return resetHwid()
    }

    fun resetHwid(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        val value = bytes.joinToString("") { "%02x".format(it) }
        prefs().edit().putString("hwid", value).apply()
        return value
    }

    val osChoices = listOf("Android", "iOS", "Windows", "macOS", "Linux")

    fun os(): String = prefs().getString("hw_os", null)?.ifBlank { null } ?: "Android"

    fun osVer(): String = prefs().getString("hw_ver", null)?.ifBlank { null } ?: (Build.VERSION.RELEASE ?: "")

    fun model(): String = prefs().getString("hw_model", null)?.ifBlank { null } ?: (Build.MODEL ?: "")

    fun setDevice(os: String, ver: String, model: String) {
        prefs().edit().putString("hw_os", os.trim()).putString("hw_ver", ver.trim()).putString("hw_model", model.trim()).apply()
    }

    fun resetDevice() {
        prefs().edit().remove("hw_os").remove("hw_ver").remove("hw_model").apply()
    }

    fun order(url: String): List<Int> {
        val host = try { java.net.URL(url).host ?: "" } catch (e: Exception) { "" }
        val known = prefs().getInt("maskfor:$host", -1)
        val list = ArrayList<Int>()
        list.add(1)
        if (known in 2 until names.size && known !in list) list.add(known)
        if (mask() !in list) list.add(mask())
        for (i in names.indices) if (i !in list) list.add(i)
        return list
    }

    fun remember(url: String, m: Int) {
        val host = try { java.net.URL(url).host ?: "" } catch (e: Exception) { return }
        prefs().edit().putInt("maskfor:$host", m).apply()
    }

    fun apply(c: HttpURLConnection, m: Int = mask()) {
        c.setRequestProperty("User-Agent", agents[m.coerceIn(0, agents.size - 1)])
        c.setRequestProperty("Accept", "*/*")
        c.setRequestProperty("x-hwid", hwid())
        c.setRequestProperty("x-device-os", os())
        c.setRequestProperty("x-ver-os", osVer())
        c.setRequestProperty("x-device-model", model())
    }
}
