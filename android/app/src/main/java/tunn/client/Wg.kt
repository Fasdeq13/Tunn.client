package tunn.client

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.Inflater

object Wg {
    val schemes = setOf("wireguard", "wg", "awg", "amneziawg", "amnezia-wg", "amnezia")
    val awgKeys = listOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5")

    fun isScheme(line: String): Boolean {
        val i = line.indexOf("://")
        return i > 0 && line.substring(0, i).trim().lowercase() in schemes
    }

    fun isAmnezia(line: String): Boolean = line.trim().lowercase().startsWith("vpn://")

    fun isConf(text: String): Boolean = text.contains("[Interface]", ignoreCase = true)

    fun isAwg(conf: String): Boolean {
        if (!isConf(conf)) return false
        var inside = false
        for (line in conf.lines()) {
            val t = line.trim()
            if (t.startsWith("[")) {
                inside = t.equals("[interface]", ignoreCase = true)
                continue
            }
            if (!inside) continue
            val i = t.indexOf('=')
            if (i <= 0) continue
            if (t.substring(0, i).trim().lowercase() in awgKeys && t.substring(i + 1).trim().isNotEmpty()) return true
        }
        return false
    }

    fun kind(conf: String): String = if (!isConf(conf)) "" else if (isAwg(conf)) "awg" else "wg"

    private fun dec(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun pad(s: String): String {
        val n = s.trim().replace('+', '-').replace('/', '_').replace("=", "")
        return n + "=".repeat((4 - n.length % 4) % 4)
    }

    fun fromUri(text: String): String {
        val raw = text.trim()
        val end = raw.indexOf("://")
        require(end > 0) { "Не удалось распознать ссылку" }
        var rest = raw.substring(end + 3)
        val hash = rest.indexOf('#')
        if (hash >= 0) rest = rest.substring(0, hash)
        var query = ""
        val q = rest.indexOf('?')
        if (q >= 0) {
            query = rest.substring(q + 1)
            rest = rest.substring(0, q)
        }
        val at = rest.lastIndexOf('@')
        require(at > 0) { "В ссылке нет приватного ключа" }
        val priv = dec(rest.substring(0, at)).trim()
        val target = rest.substring(at + 1).trimEnd('/')
        require(priv.isNotEmpty() && target.isNotEmpty()) { "В ссылке не хватает данных" }
        val p = HashMap<String, String>()
        query.split('&').forEach { pair ->
            val i = pair.indexOf('=')
            if (i > 0) p[pair.substring(0, i).lowercase()] = dec(pair.substring(i + 1))
        }
        val pub = p["publickey"] ?: p["public_key"] ?: p["pbk"] ?: p["peer"] ?: ""
        val address = p["address"] ?: p["ip"] ?: ""
        require(pub.isNotEmpty()) { "В ссылке нет публичного ключа сервера" }
        require(address.isNotEmpty()) { "В ссылке нет адреса клиента" }
        val sb = StringBuilder("[Interface]\n")
        sb.append("PrivateKey = ").append(priv).append('\n')
        sb.append("Address = ").append(address).append('\n')
        (p["dns"] ?: "").takeIf { it.isNotBlank() }?.let { sb.append("DNS = ").append(it).append('\n') }
        (p["mtu"] ?: "").takeIf { it.isNotBlank() }?.let { sb.append("MTU = ").append(it).append('\n') }
        for (k in awgKeys) {
            val v = p[k] ?: continue
            if (v.isNotBlank()) sb.append(k.replaceFirstChar { it.uppercase() }).append(" = ").append(v).append('\n')
        }
        sb.append("\n[Peer]\n")
        sb.append("PublicKey = ").append(pub).append('\n')
        (p["presharedkey"] ?: p["psk"] ?: "").takeIf { it.isNotBlank() }?.let { sb.append("PresharedKey = ").append(it).append('\n') }
        sb.append("AllowedIPs = ").append((p["allowedips"] ?: "").ifBlank { "0.0.0.0/0, ::/0" }).append('\n')
        sb.append("Endpoint = ").append(target).append('\n')
        (p["keepalive"] ?: p["persistentkeepalive"] ?: "").takeIf { it.isNotBlank() }?.let { sb.append("PersistentKeepalive = ").append(it).append('\n') }
        return sb.toString().trim()
    }

    private fun inflate(data: ByteArray, skip: Int): ByteArray? = try {
        val inf = Inflater()
        inf.setInput(data, skip, data.size - skip)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        if (out.size() == 0) null else out.toByteArray()
    } catch (e: Exception) {
        null
    }

    fun fromAmnezia(text: String): List<String> {
        val body = text.trim().substring(6).substringBefore('#')
        val bytes = try {
            Base64.getUrlDecoder().decode(pad(body))
        } catch (e: Exception) {
            throw IllegalArgumentException("Неверная ссылка Amnezia")
        }
        val json = (inflate(bytes, 4) ?: inflate(bytes, 0) ?: bytes).toString(Charsets.UTF_8).trim()
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Не удалось прочитать ссылку Amnezia")
        }
        val dns1 = root.optString("dns1", "1.1.1.1").ifBlank { "1.1.1.1" }
        val dns2 = root.optString("dns2", "1.0.0.1").ifBlank { "1.0.0.1" }
        val out = ArrayList<String>()
        val containers = root.optJSONArray("containers")
        if (containers != null) {
            for (i in 0 until containers.length()) {
                val c = containers.optJSONObject(i) ?: continue
                for (key in listOf("awg", "wireguard")) {
                    val o = c.optJSONObject(key) ?: continue
                    val last = o.optString("last_config", "")
                    if (last.isBlank()) continue
                    val cfg = try {
                        JSONObject(last).optString("config", "")
                    } catch (e: Exception) {
                        last
                    }
                    if (cfg.isNotBlank() && isConf(cfg)) {
                        out.add(cfg.replace("\$PRIMARY_DNS", dns1).replace("\$SECONDARY_DNS", dns2).replace("\r", "").trim())
                    }
                }
            }
        }
        require(out.isNotEmpty()) { "В ссылке Amnezia нет конфигурации AmneziaWG или WireGuard" }
        return out
    }

    fun convert(line: String): List<String> = when {
        isAmnezia(line) -> fromAmnezia(line)
        isScheme(line) -> listOf(fromUri(line))
        else -> throw IllegalArgumentException("Не удалось распознать ссылку")
    }

    fun stripAwg(conf: String): String {
        var section = ""
        val out = ArrayList<String>()
        for (line in conf.lines()) {
            val t = line.trim()
            if (t.startsWith("[")) {
                section = t.lowercase()
                out.add(line)
                continue
            }
            if (section == "[interface]") {
                val i = t.indexOf('=')
                if (i > 0 && t.substring(0, i).trim().lowercase() in awgKeys) continue
            }
            out.add(line)
        }
        return out.joinToString("\n")
    }

    val extraKeys = listOf("mtu", "keepalive", "dns", "port")

    private val canon = mapOf(
        "privatekey" to "PrivateKey", "address" to "Address", "dns" to "DNS", "mtu" to "MTU", "listenport" to "ListenPort",
        "jc" to "Jc", "jmin" to "Jmin", "jmax" to "Jmax", "s1" to "S1", "s2" to "S2", "s3" to "S3", "s4" to "S4",
        "h1" to "H1", "h2" to "H2", "h3" to "H3", "h4" to "H4", "i1" to "I1", "i2" to "I2", "i3" to "I3", "i4" to "I4", "i5" to "I5",
        "publickey" to "PublicKey", "presharedkey" to "PresharedKey", "allowedips" to "AllowedIPs",
        "endpoint" to "Endpoint", "persistentkeepalive" to "PersistentKeepalive"
    )
    private val dropped = setOf("preup", "postup", "predown", "postdown", "table", "saveconfig")

    class Doc(val iface: LinkedHashMap<String, String>, val peers: MutableList<LinkedHashMap<String, String>>)

    fun parseDoc(conf: String): Doc {
        val iface = LinkedHashMap<String, String>()
        val peers = ArrayList<LinkedHashMap<String, String>>()
        var section = ""
        for (raw in conf.replace("\r", "").lines()) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";")) continue
            if (t.startsWith("[")) {
                section = t.lowercase()
                if (section == "[peer]") peers.add(LinkedHashMap())
                continue
            }
            val i = t.indexOf('=')
            if (i <= 0) continue
            val k = t.substring(0, i).trim().lowercase()
            val v = t.substring(i + 1).trim()
            if (k in dropped) continue
            if (section == "[interface]") iface[k] = v
            else if (section == "[peer]" && peers.isNotEmpty()) peers.last()[k] = v
        }
        return Doc(iface, peers)
    }

    fun render(d: Doc): String {
        val sb = StringBuilder("[Interface]\n")
        for ((k, v) in d.iface) sb.append(canon[k] ?: k).append(" = ").append(v).append('\n')
        for (p in d.peers) {
            sb.append("\n[Peer]\n")
            for ((k, v) in p) sb.append(canon[k] ?: k).append(" = ").append(v).append('\n')
        }
        return sb.toString().trim()
    }

    fun hostOf(ep: String): String {
        val e = ep.trim()
        if (e.startsWith("[")) {
            val end = e.indexOf(']')
            return if (end > 1) e.substring(1, end) else e
        }
        return e.substringBeforeLast(':')
    }

    fun portOf(ep: String): Int {
        val e = ep.trim()
        val tail = if (e.startsWith("[")) e.substringAfter("]").removePrefix(":") else e.substringAfterLast(':', "")
        return tail.toIntOrNull() ?: 51820
    }

    private fun resolve(host: String): String {
        val all = try {
            java.net.InetAddress.getAllByName(host)
        } catch (e: Exception) {
            throw IllegalStateException("Не удалось найти адрес сервера $host")
        }
        val pick = all.firstOrNull { it is java.net.Inet4Address } ?: all.first()
        return (pick.hostAddress ?: host).substringBefore('%')
    }

    fun quickResolved(conf: String): String {
        val d = parseDoc(conf)
        for (p in d.peers) {
            val ep = p["endpoint"] ?: continue
            val addr = resolve(hostOf(ep))
            val port = portOf(ep)
            p["endpoint"] = if (addr.contains(':')) "[$addr]:$port" else "$addr:$port"
        }
        return render(d)
    }

    fun applyParams(conf: String, params: JSONObject, force: Boolean): String {
        if (!isConf(conf)) return conf
        val d = parseDoc(conf)
        if (force || isAwg(conf)) {
            for (k in awgKeys) {
                val v = params.optString(k, "").trim()
                if (v.isNotEmpty()) d.iface[k] = v
            }
        }
        params.optString("mtu", "").trim().toIntOrNull()?.let { if (it in 1200..1500) d.iface["mtu"] = it.toString() }
        params.optString("dns", "").trim().takeIf { it.isNotEmpty() }?.let { d.iface["dns"] = it }
        params.optString("keepalive", "").trim().toIntOrNull()?.let {
            if (it in 0..600) for (p in d.peers) p["persistentkeepalive"] = it.toString()
        }
        params.optString("port", "").trim().toIntOrNull()?.let { port ->
            if (port in 1..65535) {
                for (p in d.peers) {
                    val ep = p["endpoint"] ?: continue
                    val host = hostOf(ep)
                    p["endpoint"] = if (host.contains(':')) "[$host]:$port" else "$host:$port"
                }
            }
        }
        return render(d)
    }
}
