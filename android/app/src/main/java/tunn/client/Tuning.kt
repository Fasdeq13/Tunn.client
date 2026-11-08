package tunn.client

import org.json.JSONObject

data class Tuning(
    val ipv6: Boolean = true,
    val logLevel: Int = 2,
    val sniffing: Boolean = false,
    val bypassLan: Boolean = false,
    val blockQuic: Boolean = false,
    val domainStrategy: Int = 0,
    val appMode: Int = 0,
    val apps: String = "",
    val unmetered: Boolean = false,
    val connIdle: Int = 300,
    val bufferKb: Int = 0,
    val mux: Boolean = false,
    val muxConcurrency: Int = 8
) {
    fun appList(): List<String> = apps.split(',', ' ', ';', '\n')
        .map { it.trim() }
        .filter { it.matches(Regex("[A-Za-z0-9_.]+")) && it.contains('.') }
        .distinct()

    fun toJson(): String = JSONObject()
        .put("ipv6", ipv6)
        .put("log", logLevel)
        .put("sniff", sniffing)
        .put("lan", bypassLan)
        .put("quic", blockQuic)
        .put("ds", domainStrategy)
        .put("am", appMode)
        .put("apps", apps)
        .put("unm", unmetered)
        .put("idle", connIdle)
        .put("buf", bufferKb)
        .put("mux", mux)
        .put("muxc", muxConcurrency)
        .toString()

    companion object {
        val logNames = listOf("Нет", "Ошибки", "Предупреждения", "Инфо", "Отладка")
        val logKeys = listOf("none", "error", "warning", "info", "debug")
        val domainNames = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
        val appModes = listOf("Все", "Исключить", "Только")

        fun fromJson(text: String?): Tuning {
            if (text.isNullOrBlank()) return Tuning()
            return try {
                val d = Tuning()
                val o = JSONObject(text)
                Tuning(
                    ipv6 = o.optBoolean("ipv6", d.ipv6),
                    logLevel = o.optInt("log", d.logLevel).coerceIn(0, logKeys.size - 1),
                    sniffing = o.optBoolean("sniff", d.sniffing),
                    bypassLan = o.optBoolean("lan", d.bypassLan),
                    blockQuic = o.optBoolean("quic", d.blockQuic),
                    domainStrategy = o.optInt("ds", d.domainStrategy).coerceIn(0, domainNames.size - 1),
                    appMode = o.optInt("am", d.appMode).coerceIn(0, appModes.size - 1),
                    apps = o.optString("apps", d.apps),
                    unmetered = o.optBoolean("unm", d.unmetered),
                    connIdle = o.optInt("idle", d.connIdle).coerceIn(10, 7200),
                    bufferKb = o.optInt("buf", d.bufferKb).coerceIn(0, 4096),
                    mux = o.optBoolean("mux", d.mux),
                    muxConcurrency = o.optInt("muxc", d.muxConcurrency).coerceIn(1, 128)
                )
            } catch (e: Throwable) {
                Tuning()
            }
        }
    }
}
