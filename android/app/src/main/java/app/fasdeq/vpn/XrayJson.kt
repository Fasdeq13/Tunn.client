package app.fasdeq.vpn

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

object XrayJson {
    private val protocols = listOf("vless", "vmess", "trojan", "shadowsocks")

    fun prepare(text: String): Prepared? {
        val t = text.trim().removePrefix("﻿")
        if (!t.startsWith("[") && !t.startsWith("{")) return null
        if (!t.contains("\"outbounds\"")) return null
        val configs = ArrayList<JSONObject>()
        try {
            if (t.startsWith("[")) {
                val arr = JSONArray(t)
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { configs.add(it) }
            } else {
                configs.add(JSONObject(t))
            }
        } catch (e: Exception) {
            return null
        }
        val links = ArrayList<String>()
        for (c in configs) {
            val l = try { link(c) } catch (e: Exception) { null }
            if (l != null) links.add(l)
        }
        if (links.isEmpty()) return Prepared(emptyList(), "В конфигах нет поддерживаемых серверов")
        return Prepared(links.distinct().map { Source(it, false) }, null)
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    private fun host(a: String): String = if (a.contains(':') && !a.startsWith("[")) "[$a]" else a

    private fun pick(cfg: JSONObject): JSONObject? {
        val list = cfg.optJSONArray("outbounds") ?: return null
        var any: JSONObject? = null
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            if (o.optString("protocol").lowercase() !in protocols) continue
            if (o.optString("tag") == "proxy") return o
            if (any == null) any = o
        }
        return any
    }

    private fun link(cfg: JSONObject): String? {
        val ob = pick(cfg) ?: return null
        val proto = ob.optString("protocol").lowercase()
        val st = ob.optJSONObject("settings") ?: JSONObject()
        val name = cfg.optString("remarks").ifBlank { cfg.optString("ps").ifBlank { cfg.optString("name").ifBlank { ob.optString("tag") } } }
        val ss = ob.optJSONObject("streamSettings") ?: JSONObject()
        var net = ss.optString("network", "tcp").lowercase()
        if (net == "splithttp") net = "xhttp"
        if (net == "raw" || net.isEmpty()) net = "tcp"
        val sec = ss.optString("security", "none").lowercase().ifEmpty { "none" }
        val q = ArrayList<Pair<String, String>>()
        q.add("type" to net)
        q.add("security" to sec)
        val tls = ss.optJSONObject("tlsSettings") ?: JSONObject()
        val rl = ss.optJSONObject("realitySettings") ?: JSONObject()
        if (sec == "tls") {
            tls.optString("serverName").takeIf { it.isNotEmpty() }?.let { q.add("sni" to it) }
            tls.optString("fingerprint").takeIf { it.isNotEmpty() }?.let { q.add("fp" to it) }
            tls.optJSONArray("alpn")?.let { a -> if (a.length() > 0) q.add("alpn" to (0 until a.length()).joinToString(",") { a.getString(it) }) }
            if (tls.optBoolean("allowInsecure", false)) q.add("allowInsecure" to "1")
        } else if (sec == "reality") {
            rl.optString("serverName").takeIf { it.isNotEmpty() }?.let { q.add("sni" to it) }
            rl.optString("fingerprint").takeIf { it.isNotEmpty() }?.let { q.add("fp" to it) }
            rl.optString("publicKey").takeIf { it.isNotEmpty() }?.let { q.add("pbk" to it) }
            rl.optString("shortId").takeIf { it.isNotEmpty() }?.let { q.add("sid" to it) }
            rl.optString("spiderX").takeIf { it.isNotEmpty() }?.let { q.add("spx" to it) }
            rl.optString("mldsa65Verify").takeIf { it.isNotEmpty() }?.let { q.add("pqv" to it) }
        }
        ss.optJSONObject("finalmask")?.let { q.add("fm" to it.toString()) }
        when (net) {
            "ws" -> {
                val w = ss.optJSONObject("wsSettings") ?: JSONObject()
                w.optString("path").takeIf { it.isNotEmpty() }?.let { q.add("path" to it) }
                val hd = w.optJSONObject("headers")
                val hh = w.optString("host").ifEmpty { hd?.optString("Host").orEmpty().ifEmpty { hd?.optString("host").orEmpty() } }
                if (hh.isNotEmpty()) q.add("host" to hh)
            }
            "grpc" -> {
                val g = ss.optJSONObject("grpcSettings") ?: JSONObject()
                g.optString("serviceName").takeIf { it.isNotEmpty() }?.let { q.add("serviceName" to it) }
                if (g.optBoolean("multiMode", false)) q.add("mode" to "multi")
            }
            "xhttp" -> {
                val x = ss.optJSONObject("xhttpSettings") ?: ss.optJSONObject("splithttpSettings") ?: JSONObject()
                x.optString("path").takeIf { it.isNotEmpty() }?.let { q.add("path" to it) }
                x.optString("host").takeIf { it.isNotEmpty() }?.let { q.add("host" to it) }
                x.optString("mode").takeIf { it.isNotEmpty() }?.let { q.add("mode" to it) }
                x.optJSONObject("extra")?.let { q.add("extra" to it.toString()) }
            }
            "httpupgrade" -> {
                val u = ss.optJSONObject("httpupgradeSettings") ?: JSONObject()
                u.optString("path").takeIf { it.isNotEmpty() }?.let { q.add("path" to it) }
                u.optString("host").takeIf { it.isNotEmpty() }?.let { q.add("host" to it) }
            }
            "tcp" -> {
                val tc = ss.optJSONObject("tcpSettings") ?: ss.optJSONObject("rawSettings") ?: JSONObject()
                val hd = tc.optJSONObject("header")
                if (hd != null && hd.optString("type") == "http") {
                    q.add("headerType" to "http")
                    val req = hd.optJSONObject("request")
                    req?.optJSONArray("path")?.optString(0)?.takeIf { it.isNotEmpty() }?.let { q.add("path" to it) }
                    val hv = req?.optJSONObject("headers")?.opt("Host")
                    val hs = when (hv) {
                        is JSONArray -> hv.optString(0)
                        is String -> hv
                        else -> ""
                    }
                    if (hs.isNotEmpty()) q.add("host" to hs)
                }
            }
        }
        fun qs(extra: List<Pair<String, String>> = emptyList()) =
            (q + extra).joinToString("&") { enc(it.first) + "=" + enc(it.second) }
        if (proto == "vless" || proto == "vmess") {
            val v = st.optJSONArray("vnext")?.optJSONObject(0) ?: st
            val u = v.optJSONArray("users")?.optJSONObject(0) ?: v
            val addr = v.optString("address")
            val port = v.optInt("port", 0)
            val id = u.optString("id")
            if (addr.isEmpty() || port <= 0 || id.isEmpty()) return null
            if (proto == "vless") {
                val extra = ArrayList<Pair<String, String>>()
                extra.add("encryption" to u.optString("encryption", "none").ifEmpty { "none" })
                u.optString("flow").takeIf { it.isNotEmpty() }?.let { extra.add("flow" to it) }
                return "vless://" + enc(id) + "@" + host(addr) + ":" + port + "?" + qs(extra) + "#" + enc(name)
            }
            val m = JSONObject()
                .put("v", "2").put("ps", name).put("add", addr).put("port", port.toString()).put("id", id)
                .put("aid", u.optInt("alterId", 0).toString()).put("scy", u.optString("security", "auto").ifEmpty { "auto" })
                .put("net", net).put("type", "none").put("host", "").put("path", "")
                .put("tls", if (sec == "tls") "tls" else "").put("sni", "").put("alpn", "").put("fp", "")
            for ((k, value) in q) {
                when (k) {
                    "host" -> m.put("host", value)
                    "path", "serviceName" -> m.put("path", value)
                    "sni" -> m.put("sni", value)
                    "alpn" -> m.put("alpn", value)
                    "fp" -> m.put("fp", value)
                    "headerType" -> m.put("type", value)
                }
            }
            return "vmess://" + Base64.getEncoder().encodeToString(m.toString().toByteArray())
        }
        val sv = st.optJSONArray("servers")?.optJSONObject(0) ?: st
        val addr = sv.optString("address")
        val port = sv.optInt("port", 0)
        if (addr.isEmpty() || port <= 0) return null
        if (proto == "trojan") {
            val pw = sv.optString("password")
            if (pw.isEmpty()) return null
            return "trojan://" + enc(pw) + "@" + host(addr) + ":" + port + "?" + qs() + "#" + enc(name)
        }
        val method = sv.optString("method")
        if (method.isEmpty() || !sv.has("password")) return null
        val cred = Base64.getUrlEncoder().withoutPadding().encodeToString((method + ":" + sv.optString("password")).toByteArray())
        return "ss://" + cred + "@" + host(addr) + ":" + port + "#" + enc(name)
    }
}
