package app.fasdeq.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CorePlugins {
    const val XRAY = "xray"
    const val SINGBOX = "singbox"
    const val TUIC = "tuic"
    const val DPI = "dpi"
    const val DPI_ID = "dpi-free"
    const val DPI_CONF = "dpi://free"
    const val DPI_NAME = "Бесплатный сервер DPI-обход"

    private fun prefs(c: Context) = c.getSharedPreferences("core_plugins", Context.MODE_PRIVATE)

    fun enabled(c: Context, id: String): Boolean = prefs(c).getBoolean("on_$id", id != SINGBOX && id != TUIC)

    fun deleted(c: Context, id: String): Boolean = prefs(c).getBoolean("del_$id", false)

    fun primary(c: Context): String = prefs(c).getString("primary", XRAY) ?: XRAY

    fun setEnabled(c: Context, id: String, value: Boolean) {
        prefs(c).edit().putBoolean("on_$id", value).apply()
    }

    fun setPrimary(c: Context, id: String) {
        prefs(c).edit().putString("primary", id).apply()
    }

    fun delete(c: Context, id: String) {
        prefs(c).edit().putBoolean("del_$id", true).putBoolean("on_$id", false).apply()
        if (id == XRAY) {
            for (core in Cores.list(c)) Cores.delete(c, core.id)
        }
    }

    fun restore(c: Context, id: String) {
        prefs(c).edit().putBoolean("del_$id", false).putBoolean("on_$id", true).apply()
    }

    fun singboxFile(c: Context): File = File(c.applicationInfo.nativeLibraryDir, "libsingbox.so")

    fun present(c: Context, id: String): Boolean = when (id) {
        XRAY -> !deleted(c, XRAY)
        SINGBOX -> !deleted(c, SINGBOX) && singboxFile(c).exists()
        TUIC -> !deleted(c, TUIC) && singboxFile(c).exists()
        else -> !deleted(c, id)
    }

    fun usable(c: Context, id: String): Boolean = present(c, id) && enabled(c, id)

    fun pick(c: Context, outbound: JSONObject): String? {
        if (outbound.optString("protocol") == "tuic") return if (usable(c, TUIC)) SINGBOX else null
        val order = listOf(primary(c), XRAY, SINGBOX).distinct()
        for (id in order) {
            if (!usable(c, id)) continue
            if (id == SINGBOX && !SingBox.supports(outbound)) continue
            return id
        }
        return null
    }

    fun snapshot(c: Context): JSONObject {
        val out = JSONObject()
        val items = JSONArray()
        fun add(id: String, name: String, description: String, core: Boolean) {
            items.put(
                JSONObject().put("id", id).put("name", name).put("description", description).put("core", core)
                    .put("present", present(c, id)).put("enabled", enabled(c, id) && present(c, id))
                    .put("primary", core && primary(c) == id)
            )
        }
        add(XRAY, "Xray", "Основное ядро: VLESS, VMess, Trojan, Shadowsocks, WireGuard, XHTTP", true)
        add(SINGBOX, "sing-box", "Второе ядро: VLESS, VMess, Trojan, Shadowsocks. Нет XHTTP и WireGuard", true)
        add(TUIC, "TUIC", "Протокол TUIC (QUIC/UDP). Работает через ядро sing-box, выключен по умолчанию", false)
        add(DPI, DPI_NAME, "Один общий сервер без выхода в сеть: обход DPI по принципу zapret и tg-ws-proxy", false)
        out.put("list", items)
        out.put("dpi", Dpi.status(c))
        return out
    }
}
