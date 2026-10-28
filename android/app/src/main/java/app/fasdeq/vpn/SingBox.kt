package app.fasdeq.vpn

import org.json.JSONArray
import org.json.JSONObject

object SingBox {
    private val protocols = setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "tuic")

    fun supports(outbound: JSONObject): Boolean {
        if (outbound.optString("protocol") !in protocols) return false
        if (outbound.optString("protocol") == "tuic") return true
        val net = outbound.optJSONObject("streamSettings")?.optString("network", "tcp") ?: "tcp"
        return net in setOf("tcp", "ws", "grpc", "httpupgrade", "h2")
    }

    private fun server(o: JSONObject): JSONObject {
        val s = o.optJSONObject("settings") ?: JSONObject()
        val v = s.optJSONArray("vnext")?.optJSONObject(0)
        if (v != null) return v
        return s.optJSONArray("servers")?.optJSONObject(0) ?: JSONObject()
    }

    private fun tls(stream: JSONObject?): JSONObject? {
        if (stream == null) return null
        val sec = stream.optString("security", "none")
        if (sec == "tls") {
            val t = stream.optJSONObject("tlsSettings") ?: JSONObject()
            val o = JSONObject().put("enabled", true)
            if (t.optString("serverName").isNotEmpty()) o.put("server_name", t.optString("serverName"))
            if (t.optBoolean("allowInsecure", false)) o.put("insecure", true)
            val alpn = t.optJSONArray("alpn")
            if (alpn != null && alpn.length() > 0) o.put("alpn", alpn)
            val fp = t.optString("fingerprint")
            if (fp.isNotEmpty()) o.put("utls", JSONObject().put("enabled", true).put("fingerprint", fp))
            return o
        }
        if (sec == "reality") {
            val r = stream.optJSONObject("realitySettings") ?: JSONObject()
            return JSONObject()
                .put("enabled", true)
                .put("server_name", r.optString("serverName"))
                .put("utls", JSONObject().put("enabled", true).put("fingerprint", r.optString("fingerprint", "chrome").ifEmpty { "chrome" }))
                .put("reality", JSONObject().put("enabled", true).put("public_key", r.optString("publicKey", r.optString("password"))).put("short_id", r.optString("shortId")))
        }
        return null
    }

    private fun transport(stream: JSONObject?): JSONObject? {
        if (stream == null) return null
        return when (stream.optString("network", "tcp")) {
            "ws" -> {
                val w = stream.optJSONObject("wsSettings") ?: JSONObject()
                val o = JSONObject().put("type", "ws").put("path", w.optString("path", "/"))
                val host = w.optJSONObject("headers")?.optString("Host", "") ?: ""
                if (host.isNotEmpty()) o.put("headers", JSONObject().put("Host", host))
                o
            }
            "grpc" -> JSONObject().put("type", "grpc").put("service_name", stream.optJSONObject("grpcSettings")?.optString("serviceName", "") ?: "")
            "httpupgrade" -> {
                val h = stream.optJSONObject("httpupgradeSettings") ?: JSONObject()
                JSONObject().put("type", "httpupgrade").put("path", h.optString("path", "/")).put("host", h.opt("host")?.toString()?.trim('[', ']', '"') ?: "")
            }
            "h2" -> JSONObject().put("type", "http")
            else -> null
        }
    }

    fun buildConfig(outbound: JSONObject, port: Int): String {
        val protocol = outbound.optString("protocol")
        val s = server(outbound)
        val stream = outbound.optJSONObject("streamSettings")
        val user = s.optJSONArray("users")?.optJSONObject(0) ?: JSONObject()
        val o = JSONObject()
            .put("tag", "proxy")
            .put("server", s.optString("address"))
            .put("server_port", s.optInt("port"))
        when (protocol) {
            "vless" -> {
                o.put("type", "vless").put("uuid", user.optString("id"))
                if (user.optString("flow").isNotEmpty()) o.put("flow", user.optString("flow"))
            }
            "vmess" -> o.put("type", "vmess").put("uuid", user.optString("id")).put("security", user.optString("security", "auto")).put("alter_id", user.optInt("alterId", 0))
            "trojan" -> o.put("type", "trojan").put("password", s.optString("password"))
            "shadowsocks" -> o.put("type", "shadowsocks").put("method", s.optString("method")).put("password", s.optString("password"))
            "socks" -> o.put("type", "socks").put("version", "5")
            "tuic" -> {
                val t = outbound.optJSONObject("tuic") ?: JSONObject()
                o.put("type", "tuic").put("uuid", s.optString("uuid")).put("password", s.optString("password"))
                    .put("congestion_control", t.optString("congestion_control", "bbr"))
                    .put("udp_relay_mode", t.optString("udp_relay_mode", "native"))
                if (t.optBoolean("zero_rtt", false)) o.put("zero_rtt_handshake", true)
                val ts = stream?.optJSONObject("tlsSettings") ?: JSONObject()
                val tlsObj = JSONObject().put("enabled", true)
                    .put("server_name", ts.optString("serverName", s.optString("address")))
                    .put("alpn", ts.optJSONArray("alpn") ?: JSONArray().put("h3"))
                if (ts.optBoolean("allowInsecure", false)) tlsObj.put("insecure", true)
                o.put("tls", tlsObj)
            }
            else -> throw IllegalStateException("sing-box не поддерживает протокол $protocol")
        }
        if (protocol != "socks" && protocol != "shadowsocks" && protocol != "tuic") {
            tls(stream)?.let { o.put("tls", it) }
            transport(stream)?.let { o.put("transport", it) }
        }
        return JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("inbounds", JSONArray().put(JSONObject().put("type", "socks").put("tag", "in").put("listen", "127.0.0.1").put("listen_port", port)))
            .put("outbounds", JSONArray().put(o).put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put("route", JSONObject().put("final", "proxy"))
            .toString()
    }
}
