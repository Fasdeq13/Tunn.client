package app.fasdeq.vpn

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.webkit.JavascriptInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.FutureTask

class WebBridge(private val host: WebUiActivity) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = host.getSharedPreferences("fasdeq", Context.MODE_PRIVATE)
    private val app get() = FasdeqApp.state

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask { block() }
        main.post(task)
        return task.get()
    }

    @JavascriptInterface
    fun state(): String = onMain { snapshot() }

    @JavascriptInterface
    fun ready() {
    }

    @JavascriptInterface
    fun bars(dark: Boolean) {
        main.post { host.applyBars(dark) }
    }

    @JavascriptInterface
    fun act(name: String, payload: String, id: Int) {
        main.post {
            plugAt = 0L
            try {
                handle(name, payload, id)
            } catch (e: Throwable) {
                reply(id, JSONObject().put("error", e.message ?: "Ошибка"))
            }
        }
    }

    fun reply(id: Int, value: JSONObject = JSONObject()) {
        host.runJs("window.__fasdeqResult(" + id + "," + JSONObject.quote(value.toString()) + ")")
    }

    fun pushLater() {
        main.postDelayed({ push() }, 300)
        main.postDelayed({ push() }, 1200)
    }

    fun pushStats() {
        val st = app
        val o = JSONObject()
            .put("rx", st.rx)
            .put("tx", st.tx)
            .put("down", st.downSpeed)
            .put("up", st.upSpeed)
            .put("uptime", st.uptime)
        host.runJs("window.__fasdeqStats(" + JSONObject.quote(o.toString()) + ")")
    }

    private var plugCache: JSONObject? = null
    private var plugAt = 0L

    private fun plugins(): JSONObject {
        val now = android.os.SystemClock.elapsedRealtime()
        val c = plugCache
        if (c != null && now - plugAt < 800) return c
        val fresh = Plugins.snapshot(host)
        plugCache = fresh
        plugAt = now
        return fresh
    }

    private val sentPing = HashMap<String, PingResult?>()
    private val sentCheck = HashMap<String, ServiceCheck?>()

    fun push() {
        sentPing.clear()
        sentCheck.clear()
        val st = app
        for (s in st.servers) {
            sentPing[s.id] = st.pings[s.id]
            sentCheck[s.id] = st.checks[s.id]
        }
        host.runJs("window.__fasdeqPush(" + JSONObject.quote(snapshot()) + ")")
    }

    fun pingHash(): Int {
        val st = app
        return listOf(st.pings.toMap(), st.checks.toMap(), st.checkingServices).hashCode()
    }

    fun pushPings() {
        val st = app
        val pings = JSONObject()
        val checks = JSONObject()
        for (s in st.servers.toList()) {
            val p = st.pings[s.id]
            if (sentPing[s.id] != p) {
                sentPing[s.id] = p
                val best = p?.best() ?: Int.MAX_VALUE
                pings.put(
                    s.id,
                    JSONObject()
                        .put("ms", if (best == Int.MAX_VALUE || best <= 0) JSONObject.NULL else best)
                        .put("busy", p?.busy ?: false)
                )
            }
            val c = st.checks[s.id]
            if (sentCheck[s.id] != c) {
                sentCheck[s.id] = c
                if (c != null) {
                    val arr = JSONArray()
                    for (v in c.ms) arr.put(v ?: JSONObject.NULL)
                    checks.put(s.id, JSONObject().put("ms", arr).put("busy", c.busy))
                }
            }
        }
        val best = JSONArray()
        for (i in Services.names.indices) best.put(st.bestFor(i) ?: JSONObject.NULL)
        val o = JSONObject().put("pings", pings).put("checks", checks).put("best", best).put("checking", st.checkingServices)
        host.runJs("window.__fasdeqPings(" + JSONObject.quote(o.toString()) + ")")
    }

    private fun parse(raw: String): Any? {
        if (raw.isEmpty()) return null
        return try {
            JSONTokener(raw).nextValue()
        } catch (e: Throwable) {
            raw
        }
    }

    private fun handle(name: String, raw: String, id: Int) {
        val value = parse(raw)
        val text = (value as? String) ?: ""
        when (name) {
            "connect" -> {
                host.requestConnect()
                reply(id)
                pushLater()
            }
            "disconnect" -> {
                app.disconnect()
                reply(id)
                pushLater()
            }
            "select" -> {
                val target = app.servers.firstOrNull { it.id == text }
                val why = if (target == null) null else blockedFor(target.conf, target.id)
                if (why != null) reply(id, JSONObject().put("error", why)) else {
                    app.select(text)
                    reply(id)
                }
            }
            "remove" -> {
                app.servers.firstOrNull { it.id == text }?.let { app.remove(it) }
                reply(id)
            }
            "removeGroup" -> {
                app.removeGroup(text)
                reply(id)
            }
            "ping" -> host.lifecycleScope.launch {
                app.pingAll().join()
                reply(id)
                push()
            }
            "pingOne" -> host.lifecycleScope.launch {
                app.ping(listOfNotNull(app.servers.firstOrNull { it.id == text })).join()
                reply(id)
                push()
            }
            "pingGroup" -> host.lifecycleScope.launch {
                app.pingGroup(text).join()
                reply(id)
                push()
            }
            "checkServices" -> {
                app.checkServices()
                reply(id)
                push()
            }
            "loadSbReleases" -> host.lifecycleScope.launch {
                val error = app.loadSbReleases()
                reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                push()
            }
            "downloadSb" -> host.lifecycleScope.launch {
                val release = app.sbReleases?.firstOrNull { it.tag == text }
                val error = if (release == null) "Версия не найдена" else app.downloadSb(release)
                reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                push()
            }
            "selectSb" -> {
                SbCores.setActive(host, text)
                reply(id)
                push()
            }
            "deleteSb" -> {
                app.deleteSb(text)
                reply(id)
                push()
            }
            "selectCore" -> {
                app.selectCore(text)
                reply(id)
                push()
            }
            "deleteCore" -> {
                app.deleteCore(text)
                reply(id)
                push()
            }
            "pickCore" -> host.pickCore { uri ->
                if (uri == null) {
                    reply(id, JSONObject().put("cancel", true))
                } else {
                    host.lifecycleScope.launch {
                        val label = displayName(host, uri).substringBeforeLast('.')
                        val error = app.importCore(uri, label)
                        reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                        push()
                    }
                }
            }
            "loadReleases" -> host.lifecycleScope.launch {
                val error = app.loadReleases()
                reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                push()
            }
            "downloadCore" -> host.lifecycleScope.launch {
                val release = app.releases?.firstOrNull { it.tag == text }
                val error = if (release == null) "Версия не найдена" else app.downloadCore(release)
                reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                push()
            }
            "pickPlugin" -> host.pickPlugin { uri ->
                if (uri == null) {
                    reply(id, JSONObject().put("cancel", true))
                } else {
                    host.lifecycleScope.launch {
                        val error = withContext(Dispatchers.IO) {
                            try {
                                val stream = host.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Не удалось открыть файл")
                                stream.use { Plugins.install(host, it, displayName(host, uri)) }
                                null
                            } catch (e: Throwable) {
                                e.message ?: "Не удалось установить"
                            }
                        }
                        reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                        push()
                    }
                }
            }
            "installPluginUrl" -> host.lifecycleScope.launch {
                val o = value as? JSONObject
                val url = o?.optString("url") ?: text
                val select = o?.optBoolean("select", false) ?: false
                val error = withContext(Dispatchers.IO) { Plugins.installFromUrl(host, url.trim(), select) }
                reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                push()
            }
            "loadCatalog" -> host.lifecycleScope.launch {
                val error = withContext(Dispatchers.IO) { Plugins.loadCatalog() }
                reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                push()
            }
            "corePlugin" -> {
                val o = value as JSONObject
                val pid = o.getString("id")
                when (o.getString("op")) {
                    "enable" -> CorePlugins.setEnabled(host, pid, o.getBoolean("value"))
                    "primary" -> CorePlugins.setPrimary(host, pid)
                    "switch" -> CorePlugins.switchTo(host, pid)
                    "server" -> CorePlugins.setServer(host, pid, o.optString("core"), o.optString("ver"))
                    "delete" -> CorePlugins.delete(host, pid)
                    "restore" -> {
                        host.lifecycleScope.launch {
                            val err = if (pid == CorePlugins.XRAY) app.restoreXray() else null
                            app.syncDpiServer()
                            reply(id, if (err != null) JSONObject().put("error", err) else JSONObject())
                            push()
                        }
                        return
                    }
                }
                app.syncDpiServer()
                reply(id)
                push()
            }
            "dpiSearch" -> {
                Dpi.forget(host)
                Dpi.setStatus(host, "searching", 0, Dpi.strategies.size, "")
                DpiService.start(host)
                reply(id, Dpi.status(host))
                push()
            }
            "dpiStatus" -> reply(id, Dpi.status(host))
            "setIcon" -> {
                AppIcon.set(host, text)
                reply(id)
                push()
            }
            "pickIcon" -> host.pickImage { uri ->
                if (uri == null) {
                    reply(id, JSONObject().put("cancel", true))
                } else {
                    host.lifecycleScope.launch {
                        val error = withContext(Dispatchers.IO) { AppIcon.pinCustom(host, uri) }
                        reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                    }
                }
            }
            "pluginEnable" -> {
                val o = value as JSONObject
                Plugins.setEnabled(host, o.getString("key"), o.getBoolean("value"))
                reply(id)
                push()
            }
            "pluginDelete" -> {
                Plugins.delete(host, text)
                reply(id)
                push()
            }
            "pluginSet" -> {
                val o = value as JSONObject
                Plugins.setSetting(host, o.getString("key"), o.getString("name"), o.opt("value"))
                reply(id)
                push()
            }
            "wgEngine" -> {
                Plugins.setWgEngine(host, text)
                reply(id)
                push()
            }
            "awgEngine" -> {
                Plugins.setAwgEngine(host, text)
                reply(id)
                push()
            }
            "awgMode" -> {
                Plugins.setWgMode(host, (value?.toString() ?: "0").trim().toIntOrNull() ?: 0)
                reply(id)
                push()
            }
            "awgParams" -> {
                Plugins.setAwgParams(host, (value as? JSONObject) ?: JSONObject())
                reply(id)
                push()
            }
            "checkUpdate" -> host.lifecycleScope.launch {
                Updates.check(host, true)
                val st = Updates.state
                reply(id, JSONObject().put("available", st.available).put("tag", st.tag).put("error", st.error ?: JSONObject.NULL))
                push()
            }
            "dismissUpdate" -> {
                Updates.skip(host, text)
                reply(id)
                push()
            }
            "renameServer" -> {
                val o = value as JSONObject
                app.renameServer(o.getString("id"), o.getString("name"))
                reply(id)
                push()
            }
            "renameGroup" -> {
                val o = value as JSONObject
                val error = app.renameGroup(o.getString("old"), o.getString("name"))
                reply(id, if (error != null) JSONObject().put("error", error) else JSONObject())
                push()
            }
            "moveServer" -> {
                val o = value as JSONObject
                app.moveServer(o.getString("id"), o.optString("group"), if (o.isNull("before")) null else o.optString("before"))
                reply(id)
                push()
            }
            "refreshInfo" -> host.lifecycleScope.launch {
                app.refreshInfo()
                reply(id)
                push()
            }
            "setCollapsed" -> {
                val arr = if (value is JSONArray) value else JSONArray()
                FasdeqApp.appContext.getSharedPreferences("fasdeq", Context.MODE_PRIVATE).edit().putString("collapsed", arr.toString()).commit()
                reply(id)
            }
            "clearGroup" -> {
                app.clearGroup(text)
                reply(id)
                push()
            }
            "add" -> host.lifecycleScope.launch {
                val before = app.servers.size
                val error = app.importText(text)
                val out = JSONObject().put("added", maxOf(0, app.servers.size - before))
                if (error != null) out.put("error", error)
                reply(id, out)
                push()
            }
            "refreshGroup" -> host.lifecycleScope.launch {
                val error = app.refreshGroup(text)
                val out = JSONObject()
                if (error != null) out.put("error", error)
                reply(id, out)
                push()
            }
            "clipboard" -> reply(id, JSONObject().put("text", clipboard()))
            "hwid" -> reply(id, JSONObject().put("hwid", Identity.hwid()))
            "hwidReset" -> reply(id, JSONObject().put("hwid", Identity.resetHwid()))
            "copy" -> {
                host.getSystemService(ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Tunn", text))
                reply(id)
            }
            "noticeSeen" -> {
                prefs.edit().putBoolean("notice33", true).apply()
                reply(id)
            }
            "haptic" -> {
                host.window.decorView.performHapticFeedback(
                    if (text == "strong") HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
                )
                reply(id)
            }
            "open" -> {
                try {
                    host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
                } catch (e: Throwable) {
                }
                reply(id)
            }
            "set" -> {
                val o = value as JSONObject
                setting(o.getString("path"), o.opt("value"))
                reply(id)
                push()
            }
            else -> reply(id)
        }
    }

    private fun clipboard(): String {
        val cm = host.getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(host)?.toString() ?: ""
    }

    private fun int(v: Any?): Int = (v as? Number)?.toInt() ?: 0
    private fun bool(v: Any?): Boolean = v as? Boolean ?: false

    private fun setting(path: String, v: Any?) {
        val st = app
        when (path) {
            "theme" -> st.setTheme(int(v))
            "dynamic" -> st.setDynamic(bool(v))
            "amoled" -> st.changeAmoled(bool(v))
            "autoConnect" -> st.changeAutoConnect(bool(v))
            "dns" -> st.changeDnsPreset(int(v))
            "dnsCustom" -> st.changeDnsCustom(v as? String ?: "")
            "mtu" -> st.changeMtu(int(v))
            "pingMode" -> st.changePingMode(int(v))
            "pingTimeout" -> st.changePingTimeout(int(v))
            "autoPing" -> st.changeAutoPing(bool(v))
            "sortPing" -> st.changeSortPing(bool(v))
            "autoCheck" -> st.changeAutoCheck(bool(v))
            "mask" -> Identity.setMask(int(v))
            "xrayFallback" -> CorePlugins.setFallback(host, bool(v))
            "checkInterval" -> st.changeCheckInterval(((v as? Number)?.toFloat()) ?: 60f)
            else -> {
                if (path.startsWith("tuning.")) {
                    val json = JSONObject(st.tuning.toJson())
                    json.put(path.removePrefix("tuning."), v ?: JSONObject.NULL)
                    st.changeTuning(Tuning.fromJson(json.toString()))
                } else {
                    val root = blob()
                    val keys = path.split('.')
                    var node = root
                    for (i in 0 until keys.size - 1) {
                        val next = node.optJSONObject(keys[i]) ?: JSONObject()
                        node.put(keys[i], next)
                        node = next
                    }
                    node.put(keys.last(), v ?: JSONObject.NULL)
                    prefs.edit().putString("web_ui", root.toString()).apply()
                }
            }
        }
    }

    private fun blob(): JSONObject = try {
        JSONObject(prefs.getString("web_ui", "{}") ?: "{}")
    } catch (e: Throwable) {
        JSONObject()
    }

    private val protoCache = HashMap<String, String>()
    private var protoStamp = -1

    private fun protoOf(conf: String): String {
        if (protoStamp != Plugins.stamp) {
            protoCache.clear()
            protoStamp = Plugins.stamp
        }
        return protoCache.getOrPut(conf) { protoOfRaw(conf) }
    }

    private fun protoOfRaw(conf: String): String {
        if (Wg.isConf(conf)) return if (Wg.isAwg(conf)) "AmneziaWG" else "WireGuard"
        Plugins.label(host, conf)?.let { return it }
        return when (val scheme = conf.substringBefore("://", "").lowercase()) {
            "vless" -> "VLESS"
            "vmess" -> "VMess"
            "trojan" -> "Trojan"
            "ss" -> "Shadowsocks"
            "hysteria2", "hy2" -> "Hysteria2"
            "hysteria" -> "Hysteria"
            "tuic" -> "TUIC"
            "socks", "socks5" -> "SOCKS"
            "" -> "Профиль"
            else -> scheme.uppercase()
        }
    }

    private val blockCache = HashMap<String, String>()
    private var blockSig = ""

    private fun blockedFor(conf: String, sid: String = ""): String? {
        val sig = listOf(
            CorePlugins.usable(host, CorePlugins.XRAY), CorePlugins.usable(host, CorePlugins.SINGBOX),
            CorePlugins.usable(host, CorePlugins.TUIC), CorePlugins.usable(host, CorePlugins.WG),
            CorePlugins.usable(host, CorePlugins.AWG), CorePlugins.primary(host), CorePlugins.fallback(host), Plugins.stamp
        ).toString()
        if (sig != blockSig) {
            blockCache.clear()
            blockSig = sig
        }
        val ov = if (sid.isEmpty()) "" else CorePlugins.serverCore(host, sid)
        return blockCache.getOrPut("$ov|$conf") { computeBlocked(conf, ov) ?: "" }.ifEmpty { null }
    }

    private fun computeBlocked(conf: String, ov: String): String? {
        if (conf.startsWith("dpi://")) return null
        if (Wg.isConf(conf)) {
            return when (Wg.kind(conf)) {
                "awg" -> if (CorePlugins.usable(host, CorePlugins.AWG)) null else "Включите плагин AmneziaWG в настройках"
                else -> if (CorePlugins.usable(host, CorePlugins.WG)) null else "Включите плагин WireGuard в настройках"
            }
        }
        val parsed = try {
            if (Plugins.forConf(host, conf) != null || Plugins.usesAwg(host, conf)) return null
            Links.parse(Plugins.effectiveConf(host, conf))
        } catch (e: Throwable) {
            return null
        }
        if (CorePlugins.pick(host, parsed.outbound, ov) != null) return null
        if (parsed.outbound.optString("protocol") == "tuic") {
            return if (!CorePlugins.present(host, CorePlugins.TUIC)) "В этой сборке нет ядра sing-box для TUIC"
            else "Включите плагин TUIC в настройках"
        }
        val x = CorePlugins.usable(host, CorePlugins.XRAY)
        val sb = CorePlugins.usable(host, CorePlugins.SINGBOX)
        if (ov == CorePlugins.SINGBOX) return if (sb) "Ядро sing-box такое не поддерживает. Смените ядро сервера (шестерёнка)" else "Включите ядро sing-box в плагинах"
        if (ov == CorePlugins.XRAY) return "Включите ядро Xray в плагинах"
        return when {
            sb && !x -> "Ядро sing-box такое не поддерживает. Включите Xray в плагинах"
            !sb && !x -> "Включите ядро в настройках"
            sb && x && !CorePlugins.fallback(host) -> "Ядро sing-box такое не поддерживает. Включите «Xray для неподдерживаемых» в настройках"
            else -> "Не поддерживается включёнными ядрами"
        }
    }

    private fun systemColors(): JSONArray {
        val out = JSONArray()
        if (Build.VERSION.SDK_INT < 31) return out
        val dark = (host.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val names = if (dark) {
            listOf(
                "system_accent1_200", "system_accent1_800", "system_accent1_700", "system_accent1_100",
                "system_accent2_700", "system_accent2_100", "system_accent3_200", "system_neutral1_900"
            )
        } else {
            listOf(
                "system_accent1_600", "system_accent1_0", "system_accent1_100", "system_accent1_900",
                "system_accent2_100", "system_accent2_900", "system_accent3_600", "system_neutral1_10"
            )
        }
        val r = android.R.color::class.java
        for (n in names) {
            val c = try {
                host.getColor(r.getField(n).getInt(null))
            } catch (e: Throwable) {
                0
            }
            out.put(c and 0xFFFFFF)
        }
        return out
    }

    private fun snapshot(): String {
        val st = app
        val o = JSONObject()
        o.put("status", st.status.name)
        o.put("selected", st.selectedId ?: JSONObject.NULL)
        o.put("rx", st.rx)
        o.put("tx", st.tx)
        o.put("down", st.downSpeed)
        o.put("up", st.upSpeed)
        o.put("uptime", st.uptime)
        o.put("error", st.error ?: JSONObject.NULL)
        o.put("dark", (host.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        o.put("colors", systemColors())
        val servers = JSONArray()
        for (s in st.servers) {
            servers.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("proto", protoOf(s.conf))
                    .put("endpoint", s.endpoint)
                    .put("group", s.group)
                    .put("blocked", blockedFor(s.conf, s.id) ?: JSONObject.NULL)
                    .put("core", CorePlugins.serverCore(host, s.id))
                    .put("ver", CorePlugins.serverVer(host, s.id))
            )
        }
        o.put("servers", servers)
        val groups = JSONArray()
        for (g in st.groups) groups.put(JSONObject().put("name", g.name).put("url", g.url).put("used", g.used).put("total", g.total).put("expire", g.expire))
        o.put("groups", groups)
        o.put("collapsed", try { JSONArray(FasdeqApp.appContext.getSharedPreferences("fasdeq", Context.MODE_PRIVATE).getString("collapsed", "[]")) } catch (e: Exception) { JSONArray() })
        val pings = JSONObject()
        for (s in st.servers) {
            val p = st.pings[s.id]
            val best = p?.best() ?: Int.MAX_VALUE
            pings.put(
                s.id,
                JSONObject()
                    .put("ms", if (best == Int.MAX_VALUE || best <= 0) JSONObject.NULL else best)
                    .put("busy", p?.busy ?: false)
            )
        }
        o.put("pings", pings)
        val settings = blob()
        settings.put("theme", st.themeMode)
        settings.put("dynamic", st.dynamicColors)
        settings.put("amoled", st.amoled)
        settings.put("autoConnect", st.autoConnect)
        settings.put("dns", st.dnsPreset)
        settings.put("dnsCustom", st.dnsCustom)
        settings.put("mtu", st.mtu)
        settings.put("pingMode", st.pingMode)
        settings.put("pingTimeout", st.pingTimeout)
        settings.put("autoPing", st.autoPing)
        settings.put("sortPing", st.sortPing)
        settings.put("tuning", JSONObject(st.tuning.toJson()))
        settings.put("autoCheck", st.autoCheck)
        settings.put("mask", Identity.mask())
        settings.put("xrayFallback", CorePlugins.fallback(host))
        settings.put("checkInterval", st.checkInterval.toDouble())
        o.put("settings", settings)
        o.put("plugins", plugins())
        o.put("notice", !prefs.getBoolean("notice33", false))

        val checks = JSONObject()
        for (s in st.servers) {
            val c = st.checks[s.id] ?: continue
            val arr = JSONArray()
            for (v in c.ms) arr.put(v ?: JSONObject.NULL)
            checks.put(s.id, JSONObject().put("ms", arr).put("busy", c.busy))
        }
        val best = JSONArray()
        for (i in Services.names.indices) best.put(st.bestFor(i) ?: JSONObject.NULL)
        o.put(
            "services",
            JSONObject()
                .put("names", JSONArray(Services.names))
                .put("checking", st.checkingServices)
                .put("checks", checks)
                .put("best", best)
        )

        val coreList = JSONArray()
        for (c in st.cores) coreList.put(JSONObject().put("id", c.id).put("label", c.label).put("version", c.version))
        val rel = st.releases
        val relArr = JSONArray()
        rel?.take(12)?.forEach { relArr.put(JSONObject().put("tag", it.tag).put("size", it.size)) }
        o.put(
            "cores",
            JSONObject()
                .put("active", st.activeCore)
                .put("builtin", st.builtinVersion)
                .put("busy", st.coreBusy ?: JSONObject.NULL)
                .put("list", coreList)
                .put("releases", if (rel == null) JSONObject.NULL else relArr)
        )

        val sbArr = JSONArray()
        for (c in st.sbCores) sbArr.put(JSONObject().put("id", c.id).put("label", c.label).put("version", c.version))
        val sbRel = JSONArray()
        st.sbReleases?.take(12)?.forEach { sbRel.put(JSONObject().put("tag", it.tag).put("size", it.size)) }
        o.put(
            "sbCores",
            JSONObject().put("active", SbCores.active(host)).put("list", sbArr)
                .put("releases", if (st.sbReleases == null) JSONObject.NULL else sbRel)
                .put("builtin", CorePlugins.singboxFile(host).exists())
        )
        o.put("corePlugins", CorePlugins.snapshot(host))
        o.put("appIcon", AppIcon.snapshot(host))

        val up = Updates.state
        o.put(
            "update",
            JSONObject()
                .put("available", up.available)
                .put("checking", up.checking)
                .put("tag", up.tag)
                .put("url", up.url)
                .put("notes", up.notes)
                .put("current", up.current)
        )
        return o.toString()
    }
}
