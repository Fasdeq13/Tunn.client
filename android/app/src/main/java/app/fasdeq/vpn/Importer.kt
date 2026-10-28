package app.fasdeq.vpn

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

class Source(val value: String, val isUrl: Boolean, val name: String = "")

class Prepared(val items: List<Source>, val error: String?)

object Importer {

    private val directSchemes = listOf("vless://", "vmess://", "trojan://", "ss://", "tuic://")
    private val deepSchemes = setOf(
        "happ", "v2rayng", "v2rayn", "clash", "clashmeta", "sing-box", "hiddify",
        "v2box", "streisand", "nekobox", "v2raytun", "flclash", "koala-clash"
    )
    private val urlKeys = setOf("url", "link", "sublink", "subscription", "suburl", "uri", "href", "source")
    private val nameKeys = listOf("remarks", "name", "title", "ps", "alias")

    private fun prepareWg(text: String): Prepared? {
        val t = text.trim().removePrefix("\uFEFF")
        if (t.isEmpty() || Wg.isConf(t)) return null
        if (!t.contains("://") && !t.contains('\n')) {
            val decoded = decodeBase64(t)?.trim()
            if (decoded != null && Wg.isConf(decoded)) return Prepared(listOf(Source(decoded, false)), null)
            if (decoded != null && decoded.contains("://")) return prepareWg(decoded)
        }
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val mine = lines.filter { Wg.isScheme(it) || Wg.isAmnezia(it) }
        if (mine.isEmpty()) return null
        val items = ArrayList<Source>()
        val errors = ArrayList<String>()
        for (line in mine) {
            try {
                Wg.convert(line).forEach { items.add(Source(it, false)) }
            } catch (e: Exception) {
                errors.add(e.message ?: "Не удалось распознать")
            }
        }
        val rest = lines.filter { it !in mine }
        if (rest.isNotEmpty()) items.addAll(prepare(rest.joinToString("\n")).items)
        if (items.isEmpty()) return Prepared(emptyList(), errors.firstOrNull() ?: "Не удалось распознать")
        return Prepared(items.distinctBy { it.value }, null)
    }

    fun prepare(text: String): Prepared {
        val wg = prepareWg(text)
        if (wg != null) return wg
        val xr = XrayJson.prepare(text)
        if (xr != null) return xr
        val hook = try {
            Plugins.prepare(FasdeqApp.appContext, text)
        } catch (e: Throwable) {
            null
        }
        if (hook != null) return hook
        val native = NativeCore.call { Native.prepareImport(text) }
        if (native != null) {
            val arr = native.getJSONArray("items")
            val items = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Source(o.getString("value"), o.getBoolean("url"), o.optString("name", ""))
            }
            return Prepared(items, null)
        }
        return prepareKt(text)
    }

    private fun prepareKt(text: String): Prepared {
        val t = text.trim().removePrefix("\uFEFF")
        if (t.isEmpty()) return Prepared(emptyList(), "Пусто")
        if (t.startsWith("PK")) {
            return Prepared(
                emptyList(),
                "Резервная копия в zip не поддерживается. В v2rayNG выберите «Экспорт конфигураций в буфер обмена» и вставьте текст сюда"
            )
        }
        if (t.contains("[Interface]", ignoreCase = true)) {
            return Prepared(listOf(Source(t, false)), null)
        }
        val items = ArrayList<Source>()
        val errors = ArrayList<String>()
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                val root: Any = if (t.startsWith("{")) JSONObject(t) else JSONArray(t)
                walk(root, "", null, items, errors)
            } catch (e: Exception) {
                errors.add("Файл не удалось прочитать как JSON")
            }
            if (items.isEmpty()) {
                return Prepared(emptyList(), errors.firstOrNull() ?: "В файле не найдено серверов или подписок")
            }
            return Prepared(items.distinctBy { it.value }, null)
        }
        var lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size == 1 && !lines[0].contains("://")) {
            val decoded = decodeBase64(lines[0])
            if (decoded != null && decoded.contains("://")) {
                lines = decoded.lines().map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        for (line in lines) {
            val src = parseLine(line, errors)
            if (src != null) items.add(src)
        }
        if (items.isEmpty()) {
            val fallback = Links.extract(t)
            if (fallback.isNotEmpty()) {
                return Prepared(
                    fallback.map { Source(it, it.startsWith("http", true)) },
                    null
                )
            }
            return Prepared(emptyList(), errors.firstOrNull() ?: "Не удалось распознать")
        }
        return Prepared(items.distinctBy { it.value }, null)
    }

    private fun parseLine(line: String, errors: MutableList<String>): Source? {
        val lower = line.lowercase()
        if (directSchemes.any { lower.startsWith(it) }) return Source(line, false)
        if (lower.startsWith("http://") || lower.startsWith("https://")) return Source(line, true)
        if (lower.startsWith("sub://")) {
            val decoded = decodeBase64(line.substring(6).substringBefore('#'))
            if (decoded != null && decoded.startsWith("http", true)) {
                return Source(decoded.trim(), true, decodeName(line.substringAfter('#', "")))
            }
            return null
        }
        return deep(line, errors)
    }

    private fun decodeName(raw: String): String = try {
        URLDecoder.decode(raw, "UTF-8").trim()
    } catch (e: Exception) {
        raw.trim()
    }

    private fun deep(line: String, errors: MutableList<String>): Source? {
        val i = line.indexOf("://")
        if (i <= 0) return null
        val scheme = line.substring(0, i).lowercase()
        if (scheme !in deepSchemes) return null
        val rest = line.substring(i + 3)
        if (rest.lowercase().startsWith("crypt")) {
            errors.add("Зашифрованная ссылка Happ не поддерживается. Попросите обычную ссылку подписки (https://…)")
            return null
        }
        val uri = try {
            Uri.parse(line)
        } catch (e: Exception) {
            null
        }
        var name = uri?.getQueryParameter("name") ?: uri?.fragment ?: ""
        var target = uri?.getQueryParameter("url")
        if (target == null) {
            var r = rest.substringBefore('#')
            if (r.contains('/')) r = r.substringAfter('/')
            if (r.startsWith("http%", true) || r.startsWith("vless%", true) || r.startsWith("vmess%", true) ||
                r.startsWith("trojan%", true) || r.startsWith("ss%", true)
            ) {
                r = try {
                    URLDecoder.decode(r, "UTF-8")
                } catch (e: Exception) {
                    r
                }
            }
            target = r
        }
        target = target.trim()
        name = decodeName(name)
        val lower = target.lowercase()
        return when {
            directSchemes.any { lower.startsWith(it) } -> Source(target, false)
            lower.startsWith("http://") || lower.startsWith("https://") -> Source(target, true, name)
            else -> null
        }
    }

    private fun decodeBase64(raw: String): String? = try {
        val cleaned = raw.trim().replace('-', '+').replace('_', '/')
        String(Base64.getMimeDecoder().decode(cleaned), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun walk(node: Any?, key: String, sibling: JSONObject?, out: MutableList<Source>, errors: MutableList<String>) {
        when (node) {
            is JSONObject -> {
                val link = profileLink(node)
                if (link != null) {
                    out.add(Source(link, false))
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    walk(node.opt(k), k, node, out, errors)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) walk(node.opt(i), key, sibling, out, errors)
            }
            is String -> {
                val v = node.trim()
                val lower = v.lowercase()
                if (directSchemes.any { lower.startsWith(it) }) {
                    out.add(Source(v, false))
                } else if ((lower.startsWith("http://") || lower.startsWith("https://")) && key.lowercase() in urlKeys) {
                    var name = ""
                    if (sibling != null) {
                        for (nk in nameKeys) {
                            val found = sibling.keys().asSequence().firstOrNull { it.equals(nk, true) }
                            if (found != null) {
                                val candidate = sibling.optString(found).trim()
                                if (candidate.isNotEmpty()) {
                                    name = candidate
                                    break
                                }
                            }
                        }
                    }
                    out.add(Source(v, true, name))
                } else {
                    val src = deep(v, errors)
                    if (src != null) out.add(src)
                }
            }
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun profileLink(o: JSONObject): String? {
        val m = HashMap<String, String>()
        val iter = o.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            val v = o.opt(k)
            if (v is String || v is Number || v is Boolean) m[k.lowercase()] = v.toString()
        }
        val addr = m["address"] ?: return null
        val port = m["port"]?.toIntOrNull() ?: return null
        val id = m["id"] ?: m["password"] ?: return null
        if (addr.isBlank() || id.isBlank()) return null
        val type = m["configtype"]?.toIntOrNull() ?: if (m.containsKey("alterid")) 1 else 0
        val remarks = (m["remarks"] ?: m["ps"] ?: addr).ifBlank { addr }
        val net = (m["network"] ?: "tcp").ifBlank { "tcp" }
        val tls = m["streamsecurity"] ?: m["tls"] ?: ""
        val sni = m["sni"] ?: ""
        val host = m["requesthost"] ?: m["host"] ?: ""
        val path = m["path"] ?: ""
        val header = (m["headertype"] ?: m["type"] ?: "none").ifBlank { "none" }
        return when (type) {
            1 -> {
                val json = JSONObject()
                    .put("v", "2")
                    .put("ps", remarks)
                    .put("add", addr)
                    .put("port", port.toString())
                    .put("id", id)
                    .put("aid", (m["alterid"] ?: "0").ifBlank { "0" })
                    .put("scy", (m["security"] ?: "auto").ifBlank { "auto" })
                    .put("net", net)
                    .put("type", header)
                    .put("host", host)
                    .put("path", path)
                    .put("tls", if (tls == "tls") "tls" else "")
                    .put("sni", sni)
                "vmess://" + Base64.getEncoder().encodeToString(json.toString().toByteArray())
            }
            5 -> {
                val q = ArrayList<String>()
                q.add("encryption=none")
                q.add("type=" + enc(net))
                q.add("security=" + enc(tls.ifBlank { "none" }))
                if (sni.isNotBlank()) q.add("sni=" + enc(sni))
                if (host.isNotBlank()) q.add("host=" + enc(host))
                if (path.isNotBlank()) q.add("path=" + enc(path))
                if (header != "none") q.add("headerType=" + enc(header))
                m["flow"]?.takeIf { it.isNotBlank() }?.let { q.add("flow=" + enc(it)) }
                m["fingerprint"]?.takeIf { it.isNotBlank() }?.let { q.add("fp=" + enc(it)) }
                m["publickey"]?.takeIf { it.isNotBlank() }?.let { q.add("pbk=" + enc(it)) }
                m["shortid"]?.takeIf { it.isNotBlank() }?.let { q.add("sid=" + enc(it)) }
                m["spiderx"]?.takeIf { it.isNotBlank() }?.let { q.add("spx=" + enc(it)) }
                "vless://" + id + "@" + addr + ":" + port + "?" + q.joinToString("&") + "#" + enc(remarks)
            }
            6 -> {
                val q = ArrayList<String>()
                q.add("type=" + enc(net))
                q.add("security=" + enc(tls.ifBlank { "tls" }))
                if (sni.isNotBlank()) q.add("sni=" + enc(sni))
                if (host.isNotBlank()) q.add("host=" + enc(host))
                if (path.isNotBlank()) q.add("path=" + enc(path))
                "trojan://" + enc(id) + "@" + addr + ":" + port + "?" + q.joinToString("&") + "#" + enc(remarks)
            }
            3 -> {
                val method = (m["security"] ?: m["method"] ?: "aes-256-gcm").ifBlank { "aes-256-gcm" }
                val user = Base64.getUrlEncoder().withoutPadding().encodeToString("$method:$id".toByteArray())
                "ss://" + user + "@" + addr + ":" + port + "#" + enc(remarks)
            }
            else -> null
        }
    }
}
