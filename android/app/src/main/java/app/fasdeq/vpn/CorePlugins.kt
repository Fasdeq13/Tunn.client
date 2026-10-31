package app.fasdeq.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CorePlugins {
    const val XRAY = "xray"
    const val SINGBOX = "singbox"
    const val TUIC = "tuic"
    const val WG = "wireguard"
    const val AWG = "awg"
    const val DPI = "dpi"
    const val DPI_ID = "dpi-free"
    const val DPI_CONF = "dpi://free"
    const val DPI_NAME = "Бесплатный сервер DPI-обход"

    private fun prefs(c: Context) = c.getSharedPreferences("core_plugins", Context.MODE_PRIVATE)

    fun enabled(c: Context, id: String): Boolean = prefs(c).getBoolean("on_$id", id != TUIC)

    fun deleted(c: Context, id: String): Boolean = prefs(c).getBoolean("del_$id", false)

    fun primary(c: Context): String = prefs(c).getString("primary", SINGBOX) ?: SINGBOX

    fun fallback(c: Context): Boolean = prefs(c).getBoolean("xray_fallback", true)

    fun setFallback(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("xray_fallback", v).apply()
    }

    fun serverCore(c: Context, id: String): String = prefs(c).getString("srv_$id", "") ?: ""

    fun serverVer(c: Context, id: String): String = prefs(c).getString("srvver_$id", "") ?: ""

    fun setServer(c: Context, id: String, core: String, ver: String) {
        prefs(c).edit().putString("srv_$id", core).putString("srvver_$id", ver).apply()
    }

    fun corePath(c: Context, engine: String, serverId: String): String {
        val ver = serverVer(c, serverId)
        if (engine == SINGBOX) {
            return SbCores.pathOf(c, ver.ifEmpty { SbCores.active(c) }) ?: ""
        }
        return Cores.pathOf(c, ver.ifEmpty { Cores.active(c) }) ?: ""
    }

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

    fun pick(c: Context, outbound: JSONObject, override: String = ""): String? {
        if (outbound.optString("protocol") == "tuic") return if (usable(c, TUIC) && usable(c, SINGBOX)) SINGBOX else null
        val sbOk = usable(c, SINGBOX) && SingBox.supports(outbound)
        val xOk = usable(c, XRAY)
        if (override == SINGBOX) return if (sbOk) SINGBOX else null
        if (override == XRAY) return if (xOk) XRAY else null
        if (primary(c) == XRAY) return if (xOk) XRAY else if (sbOk) SINGBOX else null
        if (sbOk) return SINGBOX
        if (xOk && (fallback(c) || !usable(c, SINGBOX))) return XRAY
        return null
    }

    fun switchTo(c: Context, id: String) {
        prefs(c).edit().putBoolean("on_$id", true).putString("primary", id).apply()
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
        add(SINGBOX, "sing-box", "Основное ядро по умолчанию: VLESS, VMess, Trojan, Shadowsocks. Нет XHTTP", true)
        add(XRAY, "Xray", "Подхватывает то, что не поддерживает sing-box (например XHTTP)", true)
        add(WG, "WireGuard", "Отдельный плагин для WireGuard-конфигов. Не зависит от Xray и sing-box, включён по умолчанию", false)
        add(AWG, "AmneziaWG", "Отдельный плагин для AmneziaWG-конфигов. Не зависит от Xray и sing-box, включён по умолчанию", false)
        add(TUIC, "TUIC", "Протокол TUIC (QUIC/UDP). Работает через ядро sing-box, выключен по умолчанию", false)
        add(DPI, DPI_NAME, "Один общий сервер без выхода в сеть: обход DPI по принципу zapret и tg-ws-proxy", false)
        out.put("list", items)
        out.put("dpi", Dpi.status(c))
        return out
    }
}
