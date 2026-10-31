package app.fasdeq.vpn

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

data class Parsed(val name: String, val endpoint: String, val outbound: JSONObject)

class SubInfo(val used: Long, val total: Long, val expire: Long)

class Fetched(val body: String, val group: String, val info: SubInfo? = null)

object Links {

    private val stableParams = setOf("type", "security", "sni", "host", "path", "servicename", "pbk", "encryption", "mode", "headertype")

    fun identity(link: String): String {
        val t = link.trim()
        if (t.contains("[Interface]", ignoreCase = true)) {
            val d = Wg.parseDoc(t)
            val peer = d.peers.firstOrNull()
            val pub = peer?.get("publickey") ?: ""
            val ep = peer?.get("endpoint") ?: ""
            if (pub.isNotEmpty() && ep.isNotEmpty()) return "wg://" + (d.iface["privatekey"] ?: "") + "@" + pub + "@" + ep
            return t.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }.joinToString("\n")
        }
        val scheme = t.substringBefore("://", "").lowercase()
        if (scheme.isEmpty()) return t
        val rest = t.substring(scheme.length + 3).substringBefore('#')
        if (scheme == "vmess") {
            val json = try {
                JSONObject(String(b64(rest), Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }
            if (json != null) {
                return "vmess://" + listOf("add", "port", "id", "net", "host", "path", "sni", "tls").joinToString("|") { json.optString(it, "") }
            }
        }
        val q = rest.indexOf('?')
        val main = (if (q < 0) rest else rest.substring(0, q)).trimEnd('/')
        val query = if (q < 0) "" else rest.substring(q + 1)
        val params = query.split('&')
            .mapNotNull { p ->
                val i = p.indexOf('=')
                if (i <= 0) null else p.substring(0, i).lowercase() to p.substring(i + 1).lowercase()
            }
            .filter { it.first in stableParams }
            .sortedBy { it.first }
            .joinToString("&") { it.first + "=" + it.second }
        return "$scheme://$main?$params"
    }

    fun extract(text: String): List<String> {
        val t = text.trim().removePrefix("﻿")
        if (t.contains("[Interface]", ignoreCase = true)) return listOf(t)
        return t.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun parse(raw: String): Parsed {
        val head = raw.trim()
        if (Wg.isScheme(head) || Wg.isAmnezia(head)) return parse(Wg.convert(head).first())
        val native = NativeCore.call { Native.parseLink(raw) }
        if (native != null) {
            return Parsed(
                native.getString("name"),
                native.getString("endpoint"),
                native.getJSONObject("outbound")
            )
        }
        return parseKt(raw)
    }

    private fun parseKt(raw: String): Parsed {
        val text = raw.trim()
        if (text.contains("[Interface]", ignoreCase = true)) return parseInterface(text)
        val end = text.indexOf("://")
        require(end > 0) { "Не удалось распознать ссылку" }
        return when (text.substring(0, end).lowercase()) {
            "vless" -> parseVless(text)
            "vmess" -> parseVmess(text)
            "trojan" -> parseTrojan(text)
            "ss" -> parseSs(text)
            "tuic" -> parseTuic(text)
            else -> throw IllegalArgumentException("Неподдерживаемый формат ссылки")
        }
    }

    fun buildConfig(outbound: JSONObject, mtu: Int = 1500, t: Tuning = Tuning(), extra: JSONObject? = null): String {
        val tun = JSONObject()
            .put("tag", "tun")
            .put("protocol", "tun")
            .put("settings", JSONObject().put("name", "xray0").put("mtu", mtu))
        if (t.sniffing) {
            tun.put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            )
        }
        outbound.put("tag", "proxy")
        val protocol = outbound.optString("protocol")
        val muxable = protocol in listOf("vless", "vmess", "trojan", "shadowsocks") &&
            !outbound.toString().contains("xtls-rprx-vision")
        if (t.mux && muxable) {
            outbound.put("mux", JSONObject().put("enabled", true).put("concurrency", t.muxConcurrency))
        }
        val rules = JSONArray()
        if (extra != null) addPluginRules(rules, extra)
        if (t.blockQuic) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "udp")
                    .put("port", "443")
                    .put("outboundTag", "block")
            )
        }
        if (t.bypassLan) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put(
                        "ip",
                        JSONArray()
                            .put("10.0.0.0/8")
                            .put("172.16.0.0/12")
                            .put("192.168.0.0/16")
                            .put("169.254.0.0/16")
                            .put("100.64.0.0/10")
                            .put("fc00::/7")
                            .put("fe80::/10")
                    )
                    .put("outboundTag", "direct")
            )
        }
        val level = JSONObject().put("connIdle", t.connIdle)
        if (t.bufferKb > 0) level.put("bufferSize", t.bufferKb)
        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", Tuning.logKeys[t.logLevel.coerceIn(0, Tuning.logKeys.size - 1)]))
            .put("policy", JSONObject().put("levels", JSONObject().put("0", level)))
            .put("inbounds", JSONArray().put(tun))
            .put(
                "outbounds",
                JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                    .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            )
        if (rules.length() > 0 || t.domainStrategy != 0) {
            root.put(
                "routing",
                JSONObject()
                    .put("domainStrategy", Tuning.domainNames[t.domainStrategy.coerceIn(0, Tuning.domainNames.size - 1)])
                    .put("rules", rules)
            )
        }
        return root.toString()
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }

    private fun addPluginRules(rules: JSONArray, extra: JSONObject) {
        fun dom(list: List<String>) = JSONArray(list.map { if (it.contains(':')) it else "domain:$it" })
        val block = strings(extra.optJSONArray("blockDomains"))
        val direct = strings(extra.optJSONArray("directDomains"))
        val proxy = strings(extra.optJSONArray("proxyDomains"))
        val ips = strings(extra.optJSONArray("directIps"))
        if (block.isNotEmpty()) rules.put(JSONObject().put("type", "field").put("domain", dom(block)).put("outboundTag", "block"))
        if (proxy.isNotEmpty()) rules.put(JSONObject().put("type", "field").put("domain", dom(proxy)).put("outboundTag", "proxy"))
        if (direct.isNotEmpty()) rules.put(JSONObject().put("type", "field").put("domain", dom(direct)).put("outboundTag", "direct"))
        if (ips.isNotEmpty()) rules.put(JSONObject().put("type", "field").put("ip", JSONArray(ips)).put("outboundTag", "direct"))
    }

    fun socksOutbound(port: Int): JSONObject = JSONObject()
        .put("protocol", "socks")
        .put(
            "settings",
            JSONObject().put(
                "servers",
                JSONArray().put(JSONObject().put("address", "127.0.0.1").put("port", port))
            )
        )

    fun buildInner(outbound: JSONObject, port: Int): String {
        outbound.put("tag", "proxy")
        val inbound = JSONObject()
            .put("tag", "in")
            .put("listen", "127.0.0.1")
            .put("port", port)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true).put("ip", "127.0.0.1"))
        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put(
                "outbounds",
                JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            )
        return root.toString()
    }

    private fun ip(host: String): String = try {
        InetAddress.getByName(host).hostAddress ?: host
    } catch (e: Exception) {
        host
    }

    fun resolve(outbound: JSONObject) {
        val settings = outbound.optJSONObject("settings") ?: return
        for (key in listOf("vnext", "servers")) {
            val arr = settings.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val host = o.optString("address")
                if (host.isNotEmpty()) o.put("address", ip(host))
            }
        }
        val peers = settings.optJSONArray("peers") ?: return
        for (i in 0 until peers.length()) {
            val p = peers.getJSONObject(i)
            val ep = p.optString("endpoint")
            if (ep.isEmpty()) continue
            try {
                val hp = hostPort(ep)
                val addr = ip(hp.first)
                p.put("endpoint", if (addr.contains(':')) "[" + addr + "]:" + hp.second else addr + ":" + hp.second)
            } catch (e: Exception) {
            }
        }
    }

    fun fetchMeta(url: String): Fetched {
        var last: Exception? = null
        var tlsFailure = false
        for (m in Identity.order(url)) {
            try {
                val r = fetchOnce(url, m, false)
                Identity.remember(url, m)
                return r
            } catch (e: javax.net.ssl.SSLException) {
                last = e
                tlsFailure = true
            } catch (e: java.io.IOException) {
                last = e
            } catch (e: IllegalStateException) {
                last = e
            }
            if (tlsFailure) break
        }
        if (tlsFailure && url.startsWith("https", true)) {
            for (m in Identity.order(url)) {
                try {
                    val r = fetchOnce(url, m, true)
                    Identity.remember(url, m)
                    return r
                } catch (e: java.io.IOException) {
                    last = e
                } catch (e: IllegalStateException) {
                    last = e
                }
            }
        }
        throw last ?: IllegalStateException("Не удалось загрузить")
    }

    private fun legacyTls(): javax.net.ssl.SSLSocketFactory {
        val ctx = javax.net.ssl.SSLContext.getInstance("TLSv1.2")
        ctx.init(null, null, null)
        return ctx.socketFactory
    }

    private fun snippet(text: String): String {
        val t = text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        return if (t.length > 140) t.substring(0, 140) + "…" else t
    }

    private fun parseInfo(raw: String?): SubInfo? {
        if (raw.isNullOrBlank()) return null
        val m = HashMap<String, Long>()
        for (part in raw.split(';')) {
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2) kv[1].trim().toDoubleOrNull()?.let { m[kv[0].trim().lowercase()] = it.toLong() }
        }
        if (m.isEmpty()) return null
        return SubInfo((m["upload"] ?: 0L) + (m["download"] ?: 0L), m["total"] ?: 0L, m["expire"] ?: 0L)
    }

    private fun fetchOnce(url: String, mask: Int, legacy: Boolean): Fetched {
        val c = URL(url).openConnection() as HttpURLConnection
        if (legacy && c is javax.net.ssl.HttpsURLConnection) c.sslSocketFactory = legacyTls()
        c.connectTimeout = 10000
        c.readTimeout = 15000
        Identity.apply(c, mask)
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                val err = try { c.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "" } catch (e: Exception) { "" }
                val tail = snippet(err)
                throw IllegalStateException("HTTP $code" + if (tail.isNotEmpty()) ": $tail" else "")
            }
            val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val decoded = decodeSubscription(body)
            val looksOk = decoded.contains("://") || decoded.trimStart().startsWith("{") || decoded.trimStart().startsWith("[") || decoded.contains("[Interface]", true)
            if (!looksOk) {
                val tail = snippet(body)
                throw IllegalStateException(if (tail.isNotEmpty()) "Сервер ответил: $tail" else "Пустой ответ сервера")
            }
            var title = c.getHeaderField("profile-title") ?: ""
            if (title.startsWith("base64:", true)) {
                title = try {
                    String(b64(title.substring(7)), Charsets.UTF_8)
                } catch (e: Exception) {
                    title.substring(7)
                }
            }
            if (title.isBlank()) {
                val cd = c.getHeaderField("content-disposition") ?: ""
                val m = Regex("filename\\*?=(?:UTF-8\'\')?\"?([^\";]+)", RegexOption.IGNORE_CASE).find(cd)
                if (m != null) title = decode(m.groupValues[1]).substringBeforeLast('.')
            }
            if (title.isBlank()) {
                val m = Regex("(?im)^#\\s*profile-title\\s*:\\s*(.+)$").find(decoded)
                if (m != null) title = m.groupValues[1].trim()
            }
            if (title.isBlank()) title = c.url.host ?: ""
            return Fetched(body, decode(title).trim(), parseInfo(c.getHeaderField("subscription-userinfo")))
        } finally {
            c.disconnect()
        }
    }

    fun decodeSubscription(body: String): String {
        val t = body.trim()
        if (t.contains("://")) return t
        return try {
            String(b64(t), Charsets.UTF_8)
        } catch (e: Exception) {
            t
        }
    }

    private class Link(
        val userInfo: String,
        val host: String,
        val port: Int,
        val params: Map<String, String>,
        val fragment: String
    )

    private fun decode(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun b64(s: String): ByteArray {
        val n = s.replace("\\s".toRegex(), "").replace('-', '+').replace('_', '/')
        return try {
            Base64.getDecoder().decode(n)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Неверный формат ссылки")
        }
    }

    private fun hostPort(value: String): Pair<String, Int> {
        val hp = value.trimEnd('/')
        val host: String
        val port: Int
        if (hp.startsWith("[")) {
            val e = hp.indexOf(']')
            require(e > 0) { "Неверный адрес сервера" }
            host = hp.substring(1, e)
            port = hp.substring(e + 1).removePrefix(":").toIntOrNull() ?: 443
        } else {
            val c = hp.lastIndexOf(':')
            require(c > 0) { "В ссылке нет порта" }
            host = hp.substring(0, c)
            port = hp.substring(c + 1).toIntOrNull() ?: throw IllegalArgumentException("Неверный порт")
        }
        require(host.isNotEmpty() && port in 1..65535) { "Неверный адрес сервера" }
        return Pair(host, port)
    }

    private fun split(text: String): Link {
        var rest = text.substring(text.indexOf("://") + 3)
        var fragment = ""
        val hash = rest.indexOf('#')
        if (hash >= 0) {
            fragment = decode(rest.substring(hash + 1))
            rest = rest.substring(0, hash)
        }
        var query = ""
        val q = rest.indexOf('?')
        if (q >= 0) {
            query = rest.substring(q + 1)
            rest = rest.substring(0, q)
        }
        val at = rest.lastIndexOf('@')
        val userInfo = if (at >= 0) rest.substring(0, at) else ""
        val hp = hostPort(if (at >= 0) rest.substring(at + 1) else rest)
        val params = HashMap<String, String>()
        query.split('&').forEach { pair ->
            val i = pair.indexOf('=')
            if (i > 0) params[pair.substring(0, i)] = decode(pair.substring(i + 1))
        }
        return Link(userInfo, hp.first, hp.second, params, fragment)
    }

    private fun list(value: String): List<String> =
        value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun stream(p: Map<String, String>, host: String, forceTls: Boolean = false): JSONObject {
        var net = (p["type"] ?: "tcp").lowercase().ifEmpty { "tcp" }
        if (net == "h2") net = "http"
        if (net == "splithttp") net = "xhttp"
        val sec = (p["security"] ?: if (forceTls) "tls" else "none").lowercase().ifEmpty { "none" }
        val s = JSONObject().put("network", net).put("security", sec)
        val path = (p["path"] ?: "").ifEmpty { "/" }
        val hostHeader = p["host"] ?: ""

        when (net) {
            "ws" -> {
                val headers = JSONObject()
                if (hostHeader.isNotEmpty()) headers.put("Host", hostHeader)
                s.put("wsSettings", JSONObject().put("path", path).put("headers", headers))
            }
            "grpc" -> {
                val name = p["serviceName"] ?: (p["path"] ?: "")
                s.put(
                    "grpcSettings",
                    JSONObject().put("serviceName", name).put("multiMode", p["mode"] == "multi")
                )
            }
            "http" -> s.put(
                "httpSettings",
                JSONObject().put("path", path).put("host", JSONArray(list(hostHeader)))
            )
            "httpupgrade" -> s.put(
                "httpupgradeSettings",
                JSONObject().put("path", path).put("host", hostHeader)
            )
            "xhttp" -> {
                val x = JSONObject().put("path", path).put("host", hostHeader)
                val mode = p["mode"]
                if (!mode.isNullOrEmpty()) x.put("mode", mode)
                val extra = p["extra"]
                if (!extra.isNullOrEmpty()) {
                    try {
                        x.put("extra", JSONObject(extra))
                    } catch (e: Exception) {
                    }
                }
                s.put("xhttpSettings", x)
            }
            "kcp" -> {
                val k = JSONObject().put("header", JSONObject().put("type", p["headerType"] ?: "none"))
                val seed = p["seed"]
                if (!seed.isNullOrEmpty()) k.put("seed", seed)
                s.put("kcpSettings", k)
            }
            "tcp" -> {
                if (p["headerType"] == "http") {
                    val request = JSONObject()
                        .put("path", JSONArray().put(path))
                        .put("headers", JSONObject().put("Host", JSONArray(list(hostHeader))))
                    s.put(
                        "tcpSettings",
                        JSONObject().put("header", JSONObject().put("type", "http").put("request", request))
                    )
                }
            }
        }

        val sni = (p["sni"] ?: "").ifEmpty { p["peer"] ?: "" }.ifEmpty { hostHeader }.ifEmpty { host }
        val fp = p["fp"] ?: ""
        if (sec == "tls") {
            val t = JSONObject().put("serverName", sni)
            if (fp.isNotEmpty()) t.put("fingerprint", fp)
            val alpn = list(p["alpn"] ?: "")
            if (alpn.isNotEmpty()) t.put("alpn", JSONArray(alpn))
            val insecure = p["allowInsecure"] ?: p["insecure"] ?: "0"
            if (insecure == "1" || insecure == "true") t.put("allowInsecure", true)
            s.put("tlsSettings", t)
        } else if (sec == "reality") {
            val key = p["pbk"] ?: ""
            require(key.isNotEmpty()) { "В ссылке нет публичного ключа" }
            s.put(
                "realitySettings",
                JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", fp.ifEmpty { "chrome" })
                    .put("publicKey", key)
                    .put("shortId", p["sid"] ?: "")
                    .put("spiderX", p["spx"] ?: "")
            )
        }
        return s
    }

    private fun parseVless(text: String): Parsed {
        val l = split(text)
        val id = decode(l.userInfo)
        require(id.isNotEmpty()) { "В ссылке нет идентификатора" }
        val user = JSONObject()
            .put("id", id)
            .put("encryption", (l.params["encryption"] ?: "none").ifEmpty { "none" })
            .put("level", 0)
        val flow = l.params["flow"] ?: ""
        if (flow.isNotEmpty()) user.put("flow", flow)
        val server = JSONObject()
            .put("address", l.host)
            .put("port", l.port)
            .put("users", JSONArray().put(user))
        val out = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            .put("streamSettings", stream(l.params, l.host))
        return Parsed(l.fragment.ifEmpty { l.host }, "${l.host}:${l.port}", out)
    }

    private fun parseTuic(text: String): Parsed {
        val l = split(text)
        val info = decode(l.userInfo)
        val uuid = info.substringBefore(':')
        val password = if (info.contains(':')) info.substringAfter(':') else ""
        require(uuid.isNotEmpty()) { "В ссылке нет идентификатора" }
        fun flag(k: String) = l.params[k] == "1" || l.params[k] == "true"
        val alpn = JSONArray()
        (l.params["alpn"] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("h3") }.forEach { alpn.put(it) }
        val server = JSONObject().put("address", l.host).put("port", l.port).put("uuid", uuid).put("password", password)
        val tls = JSONObject()
            .put("serverName", (l.params["sni"] ?: "").ifEmpty { l.host })
            .put("alpn", alpn)
            .put("allowInsecure", flag("allow_insecure") || flag("insecure") || flag("allowInsecure"))
        val out = JSONObject()
            .put("protocol", "tuic")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", JSONObject().put("network", "udp").put("security", "tls").put("tlsSettings", tls))
            .put(
                "tuic",
                JSONObject()
                    .put("congestion_control", (l.params["congestion_control"] ?: "").ifEmpty { "bbr" })
                    .put("udp_relay_mode", (l.params["udp_relay_mode"] ?: "").ifEmpty { "native" })
                    .put("zero_rtt", flag("zero_rtt") || flag("reduce_rtt"))
            )
        return Parsed(l.fragment.ifEmpty { l.host }, "${l.host}:${l.port}", out)
    }

    private fun parseTrojan(text: String): Parsed {
        val l = split(text)
        val password = decode(l.userInfo)
        require(password.isNotEmpty()) { "В ссылке нет пароля" }
        val server = JSONObject()
            .put("address", l.host)
            .put("port", l.port)
            .put("password", password)
            .put("level", 0)
        val out = JSONObject()
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", stream(l.params, l.host, forceTls = true))
        return Parsed(l.fragment.ifEmpty { l.host }, "${l.host}:${l.port}", out)
    }

    private fun parseVmess(text: String): Parsed {
        val body = text.substring(8).substringBefore('#').trim()
        val json = try {
            JSONObject(String(b64(body), Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Неверная ссылка")
        }
        val host = json.optString("add")
        val port = json.optString("port").toIntOrNull() ?: 0
        val id = json.optString("id")
        require(host.isNotEmpty() && port in 1..65535 && id.isNotEmpty()) { "В ссылке не хватает данных" }
        val net = json.optString("net").ifEmpty { "tcp" }
        val p = HashMap<String, String>()
        p["type"] = net
        p["headerType"] = json.optString("type")
        p["host"] = json.optString("host")
        p["path"] = json.optString("path")
        if (net == "grpc") {
            p["serviceName"] = json.optString("path")
            p["mode"] = json.optString("type")
        }
        p["security"] = if (json.optString("tls").lowercase() == "tls") "tls" else "none"
        p["sni"] = json.optString("sni")
        p["fp"] = json.optString("fp")
        p["alpn"] = json.optString("alpn")
        val user = JSONObject()
            .put("id", id)
            .put("alterId", json.optString("aid").toIntOrNull() ?: 0)
            .put("security", json.optString("scy").ifEmpty { "auto" })
            .put("level", 0)
        val server = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("users", JSONArray().put(user))
        val out = JSONObject()
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            .put("streamSettings", stream(p, host))
        return Parsed(json.optString("ps").ifEmpty { host }, "$host:$port", out)
    }

    private fun parseSs(text: String): Parsed {
        var rest = text.substring(5)
        var name = ""
        val hash = rest.indexOf('#')
        if (hash >= 0) {
            name = decode(rest.substring(hash + 1))
            rest = rest.substring(0, hash)
        }
        val q = rest.indexOf('?')
        if (q >= 0) {
            require(!rest.substring(q + 1).contains("plugin=")) { "Плагины в ссылках не поддерживаются" }
            rest = rest.substring(0, q)
        }
        rest = rest.trimEnd('/')
        val cred: String
        val target: String
        val at = rest.lastIndexOf('@')
        if (at >= 0) {
            val ui = rest.substring(0, at)
            cred = if (ui.contains(':')) decode(ui) else String(b64(decode(ui)), Charsets.UTF_8)
            target = rest.substring(at + 1)
        } else {
            val plain = String(b64(rest), Charsets.UTF_8)
            val i = plain.lastIndexOf('@')
            require(i > 0) { "Неверная ссылка" }
            cred = plain.substring(0, i)
            target = plain.substring(i + 1)
        }
        val c = cred.indexOf(':')
        require(c > 0) { "Неверная ссылка" }
        val hp = hostPort(target)
        val server = JSONObject()
            .put("address", hp.first)
            .put("port", hp.second)
            .put("method", cred.substring(0, c))
            .put("password", cred.substring(c + 1))
            .put("level", 0)
        val out = JSONObject()
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
        return Parsed(name.ifEmpty { hp.first }, "${hp.first}:${hp.second}", out)
    }

    private fun parseInterface(text: String): Parsed {
        var section = ""
        val iface = HashMap<String, String>()
        val peers = ArrayList<HashMap<String, String>>()
        for (line in text.lines()) {
            val t = line.substringBefore('#').trim()
            if (t.isEmpty()) continue
            if (t.startsWith("[")) {
                section = t.lowercase()
                if (section == "[peer]") peers.add(HashMap())
                continue
            }
            val i = t.indexOf('=')
            if (i <= 0) continue
            val k = t.substring(0, i).trim().lowercase()
            val v = t.substring(i + 1).trim()
            if (section == "[interface]") iface[k] = v
            else if (section == "[peer]") peers.last()[k] = v
        }
        val key = iface["privatekey"] ?: ""
        require(key.isNotEmpty()) { "В конфигурации нет приватного ключа" }
        val addresses = list(iface["address"] ?: "")
        require(addresses.isNotEmpty()) { "В конфигурации нет адреса" }
        require(peers.isNotEmpty()) { "В конфигурации нет сервера" }
        val peerArray = JSONArray()
        var endpoint = ""
        for (p in peers) {
            val pub = p["publickey"] ?: ""
            val ep = p["endpoint"] ?: ""
            require(pub.isNotEmpty() && ep.isNotEmpty()) { "В конфигурации не хватает данных сервера" }
            if (endpoint.isEmpty()) endpoint = ep
            val o = JSONObject().put("publicKey", pub).put("endpoint", ep)
            val psk = p["presharedkey"] ?: ""
            if (psk.isNotEmpty()) o.put("preSharedKey", psk)
            o.put("keepAlive", (p["persistentkeepalive"]?.toIntOrNull() ?: 0).let { if (it in 1..60) it else 25 })
            val allowed = list(p["allowedips"] ?: "").ifEmpty { listOf("0.0.0.0/0", "::/0") }
            o.put("allowedIPs", JSONArray(allowed))
            peerArray.put(o)
        }
        val settings = JSONObject()
            .put("secretKey", key)
            .put("address", JSONArray(addresses))
            .put("peers", peerArray)
            .put("mtu", (iface["mtu"]?.toIntOrNull() ?: 1280).coerceIn(1200, 1420))
            .put("noKernelTun", true)
        val out = JSONObject().put("protocol", "wireguard").put("settings", settings)
        return Parsed("", endpoint, out)
    }
}
