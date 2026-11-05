package app.fasdeq.vpn

import org.json.JSONArray
import org.json.JSONObject

data class SrvUi(
    val id: String,
    val name: String,
    val proto: String,
    val endpoint: String,
    val group: String,
    val blocked: String?,
    val core: String,
    val ver: String
)

data class GroupUi(val name: String, val url: String, val used: Long, val total: Long, val expire: Long)

data class PingUi(val ms: Int?, val busy: Boolean)

data class CheckUi(val ms: List<Int?>, val busy: Boolean)

data class PluginUi(
    val id: String,
    val name: String,
    val description: String,
    val core: Boolean,
    val present: Boolean,
    val enabled: Boolean,
    val primary: Boolean
)

data class CoreVer(val id: String, val label: String, val version: String)

data class RelUi(val tag: String, val size: Long)

data class UpdateUi(val available: Boolean, val checking: Boolean, val tag: String, val url: String, val notes: String)

data class FieldUi(
    val key: String,
    val type: String,
    val title: String,
    val sub: String,
    val options: List<String>,
    val min: Float,
    val max: Float,
    val step: Float,
    val placeholder: String
)

data class PlugUi(
    val key: String,
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val kinds: List<String>,
    val enabled: Boolean,
    val hasUi: Boolean,
    val schema: List<FieldUi>,
    val values: Map<String, Any?>
)

data class CatVer(val version: String, val url: String, val size: Long, val notes: String)

data class CatFam(val family: String, val name: String, val versions: List<CatVer>)

data class IconUi(val id: String, val name: String, val note: String)

data class Snap(
    val status: String = "Off",
    val selected: String? = null,
    val error: String? = null,
    val servers: List<SrvUi> = emptyList(),
    val groups: List<GroupUi> = emptyList(),
    val collapsed: Set<String> = emptySet(),
    val pings: Map<String, PingUi> = emptyMap(),
    val checks: Map<String, CheckUi> = emptyMap(),
    val serviceNames: List<String> = emptyList(),
    val serviceBest: List<String?> = emptyList(),
    val checking: Boolean = false,
    val theme: Int = 0,
    val dynamic: Boolean = true,
    val amoled: Boolean = false,
    val glass: Boolean = false,
    val glassLevel: Int = 55,
    val glassBg: Long = 0L,
    val autoConnect: Boolean = false,
    val dns: Int = 0,
    val dnsCustom: String = "",
    val mtu: Int = 1400,
    val pingMode: Int = 0,
    val pingTimeout: Int = 1,
    val autoPing: Boolean = false,
    val sortPing: Boolean = false,
    val autoCheck: Boolean = false,
    val checkInterval: Float = 60f,
    val mask: Int = 0,
    val xrayFallback: Boolean = true,
    val ipv6: Boolean = true,
    val sniff: Boolean = false,
    val quic: Boolean = false,
    val lan: Boolean = false,
    val unm: Boolean = false,
    val mux: Boolean = false,
    val merged: Boolean = true,
    val appMode: Int = 0,
    val apps: String = "",
    val plugins: List<PluginUi> = emptyList(),
    val xrayBusy: String? = null,
    val xrayActive: String = "",
    val xrayBuiltin: String = "",
    val xrayList: List<CoreVer> = emptyList(),
    val xrayReleases: List<RelUi>? = null,
    val sbActive: String = "",
    val sbBuiltin: Boolean = false,
    val dist: String = "full",
    val setupDone: Boolean = true,
    val hasSb: Boolean = true,
    val hasXray: Boolean = true,
    val hasAwg: Boolean = true,
    val sbList: List<CoreVer> = emptyList(),
    val sbReleases: List<RelUi>? = null,
    val notice: Boolean = false,
    val accentHex: String = "",
    val bgHex: String = "",
    val bgOn: Boolean = false,
    val localName: String = "",
    val btnShape: Int = 0,
    val btnLobes: Int = 9,
    val btnAmp: Float = 0.062f,
    val btnSize: Int = 196,
    val btnSpin: Boolean = true,
    val installed: List<PlugUi> = emptyList(),
    val plugBusy: String? = null,
    val catalog: List<CatFam>? = null,
    val wgMode: Int = 0,
    val dpiLine: String = "",
    val iconCurrent: String = "",
    val icons: List<IconUi> = emptyList(),
    val update: UpdateUi = UpdateUi(false, false, "", "", "")
) {
    fun selectedServer(): SrvUi? = servers.firstOrNull { it.id == selected }
}

data class Live(val rx: Long = 0, val tx: Long = 0, val down: Long = 0, val up: Long = 0, val uptime: Long = 0)

private fun JSONObject.strOrNull(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

private fun releases(a: JSONArray?): List<RelUi>? {
    if (a == null) return null
    val out = ArrayList<RelUi>()
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        out.add(RelUi(o.optString("tag"), o.optLong("size")))
    }
    return out
}

private fun coreVers(a: JSONArray?): List<CoreVer> {
    val out = ArrayList<CoreVer>()
    if (a == null) return out
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        out.add(CoreVer(o.optString("id"), o.optString("label"), o.optString("version")))
    }
    return out
}

fun parseSnap(json: String): Snap {
    val o = JSONObject(json)
    val servers = ArrayList<SrvUi>()
    val sa = o.optJSONArray("servers") ?: JSONArray()
    for (i in 0 until sa.length()) {
        val s = sa.getJSONObject(i)
        servers.add(
            SrvUi(
                s.optString("id"), s.optString("name"), s.optString("proto"), s.optString("endpoint"),
                s.optString("group"), s.strOrNull("blocked"), s.optString("core"), s.optString("ver")
            )
        )
    }
    val groups = ArrayList<GroupUi>()
    val ga = o.optJSONArray("groups") ?: JSONArray()
    for (i in 0 until ga.length()) {
        val g = ga.getJSONObject(i)
        groups.add(GroupUi(g.optString("name"), g.optString("url"), g.optLong("used"), g.optLong("total", -1), g.optLong("expire")))
    }
    val collapsed = HashSet<String>()
    val ca = o.optJSONArray("collapsed") ?: JSONArray()
    for (i in 0 until ca.length()) collapsed.add(ca.optString(i))
    val pings = HashMap<String, PingUi>()
    val po = o.optJSONObject("pings") ?: JSONObject()
    for (k in po.keys()) {
        val p = po.getJSONObject(k)
        pings[k] = PingUi(if (p.isNull("ms")) null else p.optInt("ms"), p.optBoolean("busy"))
    }
    val sv = o.optJSONObject("services") ?: JSONObject()
    val names = ArrayList<String>()
    val na = sv.optJSONArray("names") ?: JSONArray()
    for (i in 0 until na.length()) names.add(na.optString(i))
    val best = ArrayList<String?>()
    val ba = sv.optJSONArray("best") ?: JSONArray()
    for (i in 0 until ba.length()) best.add(if (ba.isNull(i)) null else ba.optString(i))
    val checks = HashMap<String, CheckUi>()
    val co = sv.optJSONObject("checks") ?: JSONObject()
    for (k in co.keys()) {
        val c = co.getJSONObject(k)
        val arr = c.optJSONArray("ms") ?: JSONArray()
        val ms = ArrayList<Int?>()
        for (i in 0 until arr.length()) ms.add(if (arr.isNull(i)) null else arr.optInt(i))
        checks[k] = CheckUi(ms, c.optBoolean("busy"))
    }
    val st = o.optJSONObject("settings") ?: JSONObject()
    val tu = st.optJSONObject("tuning") ?: JSONObject()
    val plugins = ArrayList<PluginUi>()
    val pa = o.optJSONObject("corePlugins")?.optJSONArray("list") ?: JSONArray()
    for (i in 0 until pa.length()) {
        val p = pa.getJSONObject(i)
        plugins.add(
            PluginUi(
                p.optString("id"), p.optString("name"), p.optString("description"), p.optBoolean("core"),
                p.optBoolean("present"), p.optBoolean("enabled"), p.optBoolean("primary")
            )
        )
    }
    val cores = o.optJSONObject("cores") ?: JSONObject()
    val sb = o.optJSONObject("sbCores") ?: JSONObject()
    val up = o.optJSONObject("update") ?: JSONObject()
    return Snap(
        status = o.optString("status", "Off"),
        selected = o.strOrNull("selected"),
        error = o.strOrNull("error"),
        servers = servers,
        groups = groups,
        collapsed = collapsed,
        pings = pings,
        checks = checks,
        serviceNames = names,
        serviceBest = best,
        checking = sv.optBoolean("checking"),
        theme = st.optInt("theme"),
        dynamic = st.optBoolean("dynamic", true),
        amoled = st.optBoolean("amoled"),
        glass = st.optBoolean("glass"),
        glassLevel = st.optInt("glassLevel", 55),
        glassBg = st.optLong("glassBg"),
        autoConnect = st.optBoolean("autoConnect"),
        dns = st.optInt("dns"),
        dnsCustom = st.optString("dnsCustom"),
        mtu = st.optInt("mtu", 1400),
        pingMode = st.optInt("pingMode"),
        pingTimeout = st.optInt("pingTimeout", 1),
        autoPing = st.optBoolean("autoPing"),
        sortPing = st.optBoolean("sortPing"),
        autoCheck = st.optBoolean("autoCheck"),
        checkInterval = st.optDouble("checkInterval", 60.0).toFloat(),
        mask = st.optInt("mask"),
        xrayFallback = st.optBoolean("xrayFallback", true),
        ipv6 = tu.optBoolean("ipv6", true),
        sniff = tu.optBoolean("sniff"),
        quic = tu.optBoolean("quic"),
        lan = tu.optBoolean("lan"),
        unm = tu.optBoolean("unm"),
        mux = tu.optBoolean("mux"),
        merged = st.optBoolean("merged", true),
        appMode = tu.optInt("am"),
        apps = tu.optString("apps"),
        plugins = plugins,
        xrayBusy = cores.strOrNull("busy"),
        xrayActive = cores.optString("active"),
        xrayBuiltin = cores.optString("builtin"),
        xrayList = coreVers(cores.optJSONArray("list")),
        xrayReleases = if (cores.isNull("releases")) null else releases(cores.optJSONArray("releases")),
        sbActive = sb.optString("active"),
        sbBuiltin = sb.optBoolean("builtin"),
        dist = o.optJSONObject("components")?.optString("dist", "full") ?: "full",
        setupDone = o.optJSONObject("components")?.optBoolean("setupDone", true) ?: true,
        hasSb = o.optJSONObject("components")?.optBoolean("singbox", true) ?: true,
        hasXray = o.optJSONObject("components")?.optBoolean("xray", true) ?: true,
        hasAwg = o.optJSONObject("components")?.optBoolean("awg", true) ?: true,
        sbList = coreVers(sb.optJSONArray("list")),
        sbReleases = if (sb.isNull("releases")) null else releases(sb.optJSONArray("releases")),
        notice = o.optBoolean("notice"),
        accentHex = st.optString("accentHex"),
        bgHex = st.optString("bgHex"),
        bgOn = st.optBoolean("bgCustom"),
        localName = st.optString("localName"),
        btnShape = st.optInt("btnShape", 0),
        btnLobes = st.optInt("btnLobes", 9),
        btnAmp = st.optDouble("btnAmp", 0.062).toFloat(),
        btnSize = st.optInt("btnSize", 196),
        btnSpin = st.optBoolean("btnSpin", true),
        installed = parsePlugs(o.optJSONObject("plugins")?.optJSONArray("list")),
        plugBusy = o.optJSONObject("plugins")?.strOrNull("busy"),
        catalog = parseCatalog(o.optJSONObject("plugins")),
        wgMode = o.optJSONObject("plugins")?.optInt("wgMode") ?: 0,
        dpiLine = dpiText(o.optJSONObject("corePlugins")?.optJSONObject("dpi")),
        iconCurrent = o.optJSONObject("appIcon")?.optString("current") ?: "",
        icons = parseIcons(o.optJSONObject("appIcon")?.optJSONArray("variants")),
        update = UpdateUi(up.optBoolean("available"), up.optBoolean("checking"), up.optString("tag"), up.optString("url"), up.optString("notes"))
    )
}

private fun strList(a: JSONArray?): List<String> {
    val out = ArrayList<String>()
    if (a == null) return out
    for (i in 0 until a.length()) out.add(a.optString(i))
    return out
}

private fun parsePlugs(a: JSONArray?): List<PlugUi> {
    val out = ArrayList<PlugUi>()
    if (a == null) return out
    for (i in 0 until a.length()) {
        val p = a.optJSONObject(i) ?: continue
        val fields = ArrayList<FieldUi>()
        val sc = p.optJSONArray("schema") ?: JSONArray()
        for (j in 0 until sc.length()) {
            val f = sc.optJSONObject(j) ?: continue
            fields.add(
                FieldUi(
                    f.optString("key"), f.optString("type"), f.optString("title", f.optString("key")), f.optString("sub"),
                    strList(f.optJSONArray("options")), f.optDouble("min", 0.0).toFloat(), f.optDouble("max", 100.0).toFloat(),
                    f.optDouble("step", 1.0).toFloat(), f.optString("placeholder")
                )
            )
        }
        val vals = HashMap<String, Any?>()
        val vo = p.optJSONObject("values") ?: JSONObject()
        for (k in vo.keys()) vals[k] = if (vo.isNull(k)) null else vo.opt(k)
        out.add(
            PlugUi(
                p.optString("key"), p.optString("id"), p.optString("name"), p.optString("version"), p.optString("author"),
                p.optString("description"), strList(p.optJSONArray("kinds")), p.optBoolean("enabled"), p.optBoolean("hasUi"), fields, vals
            )
        )
    }
    return out
}

private fun parseCatalog(o: JSONObject?): List<CatFam>? {
    if (o == null || o.isNull("catalog")) return null
    val a = o.optJSONArray("catalog") ?: return null
    val out = ArrayList<CatFam>()
    for (i in 0 until a.length()) {
        val f = a.optJSONObject(i) ?: continue
        val vs = ArrayList<CatVer>()
        val va = f.optJSONArray("versions") ?: JSONArray()
        for (j in 0 until va.length()) {
            val v = va.optJSONObject(j) ?: continue
            vs.add(CatVer(v.optString("version"), v.optString("url"), v.optLong("size"), v.optString("notes")))
        }
        out.add(CatFam(f.optString("family"), f.optString("name"), vs))
    }
    return out
}

private fun parseIcons(a: JSONArray?): List<IconUi> {
    val out = ArrayList<IconUi>()
    if (a == null) return out
    for (i in 0 until a.length()) {
        val v = a.optJSONObject(i) ?: continue
        out.add(IconUi(v.optString("id"), v.optString("name"), v.optString("note")))
    }
    return out
}

private fun dpiText(d: JSONObject?): String {
    if (d == null) return ""
    return if (d.optString("state") == "searching") "подбор " + d.optInt("done") + "/" + d.optInt("total")
    else (if (d.optString("strategy").isNotEmpty()) "стратегия: " + d.optString("strategy") else "ещё не подбиралась") +
        " · соединений: " + d.optInt("ok") + ", ошибок: " + d.optInt("fail")
}

fun fmtBytes(n: Long): String {
    val v = n.toDouble()
    return when {
        v < 1024 -> "$n Б"
        v < 1024 * 1024 -> String.format("%.0f КБ", v / 1024)
        v < 1024.0 * 1024 * 1024 -> String.format("%.1f МБ", v / 1048576)
        else -> String.format("%.2f ГБ", v / 1073741824)
    }
}

fun fmtTime(s: Long): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, x) else String.format("%02d:%02d", m, x)
}
