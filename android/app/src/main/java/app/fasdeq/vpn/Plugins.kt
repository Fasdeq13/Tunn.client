package app.fasdeq.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class PluginInfo(
    val key: String,
    val id: String,
    val version: String,
    val dir: File,
    val manifest: JSONObject
) {
    val name: String get() = manifest.optString("name", id)
    val kinds: List<String>
        get() {
            val a = manifest.optJSONArray("kinds") ?: return emptyList()
            return (0 until a.length()).map { a.optString(it) }
        }
    val protocol: JSONObject? get() = manifest.optJSONObject("protocol")
    val lib: File?
        get() {
            val n = protocol?.optString("lib", "") ?: ""
            if (n.isEmpty()) return null
            val f = File(dir, n)
            return if (f.isFile) f else null
        }
}

data class CatalogVersion(val version: String, val url: String, val size: Long, val notes: String)

data class CatalogFamily(val family: String, val name: String, val versions: List<CatalogVersion>)

object Plugins {
    const val CATALOG_URL = "https://raw.githubusercontent.com/Fasdeq13/FasdeqVPN/main/plugins/catalog.json"
    private val CATALOG_URLS = listOf(
        CATALOG_URL,
        "https://github.com/Fasdeq13/FasdeqVPN/raw/main/plugins/catalog.json",
        "https://cdn.jsdelivr.net/gh/Fasdeq13/FasdeqVPN@main/plugins/catalog.json"
    )
    val families = listOf("wireguard-go" to "WireGuard", "amneziawg" to "AmneziaWG")

    @Volatile
    var catalog: List<CatalogFamily>? = null

    @Volatile
    var busy: String? = null

    @Volatile
    var stamp = 0

    private const val PREFS = "fasdeq"
    private const val STATE = "plugins_state"
    private const val ENGINE = "wg_engine"
    private const val AWG_ENGINE = "awg_engine"
    private const val WG_MODE = "wg_mode"
    private const val AWG_PARAMS = "awg_params"
    private val allowedExt = setOf("json", "so", "js", "css", "png", "svg", "txt", "md", "webp", "jpg")

    private fun root(context: Context): File {
        val d = File(context.filesDir, "plugins")
        d.mkdirs()
        return d
    }

    fun rootDir(context: Context): File = root(context)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun state(context: Context): JSONObject = try {
        JSONObject(prefs(context).getString(STATE, "{}") ?: "{}")
    } catch (e: Throwable) {
        JSONObject()
    }

    private fun saveState(context: Context, o: JSONObject) {
        stamp++
        prefs(context).edit().putString(STATE, o.toString()).apply()
    }

    fun list(context: Context): List<PluginInfo> {
        val out = ArrayList<PluginInfo>()
        val dirs = root(context).listFiles() ?: return out
        for (d in dirs.sortedBy { it.name }) {
            val m = File(d, "plugin.json")
            if (!d.isDirectory || !m.isFile) continue
            try {
                val o = JSONObject(m.readText())
                val id = o.getString("id")
                val ver = o.optString("version", "1")
                out.add(PluginInfo(d.name, id, ver, d, o))
            } catch (e: Throwable) {
            }
        }
        return out
    }

    fun enabled(context: Context, key: String): Boolean {
        val s = state(context).optJSONObject(key)
        if (s != null && s.has("enabled")) return s.optBoolean("enabled", true)
        return list(context).firstOrNull { it.key == key }?.manifest?.optBoolean("defaultEnabled", true) ?: true
    }

    fun setEnabled(context: Context, key: String, value: Boolean) {
        val st = state(context)
        val s = st.optJSONObject(key) ?: JSONObject()
        s.put("enabled", value)
        st.put(key, s)
        saveState(context, st)
    }

    fun settings(context: Context, info: PluginInfo): JSONObject {
        val saved = state(context).optJSONObject(info.key)?.optJSONObject("settings") ?: JSONObject()
        val out = JSONObject()
        val schema = info.manifest.optJSONArray("settings") ?: JSONArray()
        for (i in 0 until schema.length()) {
            val f = schema.optJSONObject(i) ?: continue
            val k = f.optString("key")
            if (k.isEmpty()) continue
            out.put(k, if (saved.has(k)) saved.get(k) else f.opt("default") ?: JSONObject.NULL)
        }
        return out
    }

    fun setSetting(context: Context, key: String, name: String, value: Any?) {
        val st = state(context)
        val s = st.optJSONObject(key) ?: JSONObject()
        val vals = s.optJSONObject("settings") ?: JSONObject()
        vals.put(name, value ?: JSONObject.NULL)
        s.put("settings", vals)
        st.put(key, s)
        saveState(context, st)
    }

    fun delete(context: Context, key: String) {
        val d = File(root(context), key)
        if (d.isDirectory && d.parentFile == root(context)) d.deleteRecursively()
        stamp++
        val st = state(context)
        st.remove(key)
        saveState(context, st)
        if (wgEngine(context) == key) setWgEngine(context, "")
        if (awgEngine(context) == key) setAwgEngine(context, "")
    }

    fun wgEngine(context: Context): String = prefs(context).getString(ENGINE, "") ?: ""

    fun awgEngine(context: Context): String = prefs(context).getString(AWG_ENGINE, "") ?: ""

    fun setAwgEngine(context: Context, key: String) {
        stamp++
        prefs(context).edit().putString(AWG_ENGINE, key).apply()
    }

    fun wgMode(context: Context): Int = prefs(context).getInt(WG_MODE, 0).coerceIn(0, 2)

    fun setWgMode(context: Context, value: Int) {
        stamp++
        prefs(context).edit().putInt(WG_MODE, value.coerceIn(0, 2)).apply()
    }

    fun awgParams(context: Context): JSONObject = try {
        JSONObject(prefs(context).getString(AWG_PARAMS, "{}") ?: "{}")
    } catch (e: Throwable) {
        JSONObject()
    }

    fun setAwgParams(context: Context, value: JSONObject) {
        val clean = JSONObject()
        for (k in Wg.awgKeys) {
            val v = value.optString(k, "").trim()
            if (v.isNotEmpty()) clean.put(k, v)
        }
        for (k in Wg.extraKeys) {
            val v = value.optString(k, "").trim()
            if (v.isNotEmpty()) clean.put(k, v)
        }
        clean.put("_force", value.optBoolean("_force", false))
        stamp++
        prefs(context).edit().putString(AWG_PARAMS, clean.toString()).apply()
    }

    private fun versionKey(v: String): List<Long> = Regex("[0-9]+").findAll(v).map { it.value.toLongOrNull() ?: 0L }.toList()

    private fun compareVersions(a: String, b: String): Int {
        val x = versionKey(a)
        val y = versionKey(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0L }
            val q = y.getOrElse(i) { 0L }
            if (p != q) return p.compareTo(q)
        }
        return 0
    }

    fun effectiveConf(context: Context, conf: String): String =
        if (wgMode(context) == 2 && Wg.isAwg(conf)) Wg.stripAwg(conf) else conf


    private fun pick(all: List<PluginInfo>, wanted: String, preferred: String): PluginInfo? {
        val mine = all.filter { family(it) == wanted }
        if (mine.isEmpty()) return null
        return mine.firstOrNull { it.key == preferred } ?: mine.sortedWith { a, b -> compareVersions(a.version, b.version) }.last()
    }

    fun setWgEngine(context: Context, key: String) {
        stamp++
        prefs(context).edit().putString(ENGINE, key).apply()
    }

    private fun safeName(n: String): Boolean =
        n.isNotEmpty() && !n.contains("..") && !n.startsWith("/") && !n.contains('\\')

    fun install(context: Context, input: InputStream, fileName: String): PluginInfo {
        val raw = input.readBytes()
        require(raw.size in 4..200_000_000) { "Пустой или слишком большой файл" }
        val files = LinkedHashMap<String, ByteArray>()
        if (raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/').removePrefix("./")
                    require(safeName(name)) { "Недопустимый путь в архиве" }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in allowedExt) continue
                    files[name] = zip.readBytes()
                }
            }
        } else if (raw.size > 4 && raw[0] == 0x7f.toByte() && raw[1] == 'E'.code.toByte()) {
            val base = fileName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "plugin" }
            val m = JSONObject()
                .put("id", base)
                .put("name", base)
                .put("version", "1")
                .put("kinds", JSONArray().put("protocol"))
                .put("protocol", JSONObject().put("label", base).put("lib", "lib.so"))
            files["plugin.json"] = m.toString().toByteArray()
            files["lib.so"] = raw
        } else {
            throw IllegalArgumentException("Нужен .zip с plugin.json или .so")
        }
        val prefix = files.keys.firstOrNull { it.endsWith("plugin.json") }?.removeSuffix("plugin.json") ?: ""
        val manifestBytes = files[prefix + "plugin.json"] ?: throw IllegalArgumentException("В архиве нет plugin.json")
        val manifest = JSONObject(String(manifestBytes))
        val id = manifest.optString("id", "")
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "Неверный id плагина" }
        val version = manifest.optString("version", "1").replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val key = "$id@$version"
        val target = File(root(context), key)
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        for ((name, bytes) in files) {
            if (!name.startsWith(prefix)) continue
            val rel = name.removePrefix(prefix)
            val f = File(target, rel)
            require(f.canonicalPath.startsWith(target.canonicalPath + File.separator)) { "Недопустимый путь" }
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            if (rel.endsWith(".so")) f.setReadOnly()
        }
        stamp++
        val info = list(context).firstOrNull { it.key == key } ?: throw IllegalStateException("Не удалось установить")
        val lib = info.protocol?.optString("lib", "") ?: ""
        if (lib.isNotEmpty() && !File(target, lib).isFile) {
            target.deleteRecursively()
            throw IllegalArgumentException("В архиве нет библиотеки $lib")
        }
        return info
    }

    fun seedBuiltins(context: Context) {
        val p = prefs(context)
        val names = try {
            context.assets.list("builtin-plugins")?.filter { it.endsWith(".zip") }
        } catch (e: Throwable) {
            null
        } ?: return
        for (n in names) {
            val flag = "seed_" + n
            if (p.getBoolean(flag, false)) continue
            try {
                context.assets.open("builtin-plugins/$n").use { install(context, it, n) }
                p.edit().putBoolean(flag, true).apply()
            } catch (e: Throwable) {
            }
        }
    }

    fun download(url: String): ByteArray = Net.get(url, "application/octet-stream", 90_000)

    private fun matches(info: PluginInfo, conf: String): Boolean {
        val fam = family(info)
        if (fam == "amneziawg") return Wg.isAwg(conf)
        if (fam == "wireguard-go") return false
        val m = info.protocol?.optJSONObject("match") ?: return false
        val lower = conf.trim().lowercase()
        val schemes = m.optJSONArray("schemes")
        if (schemes != null) {
            for (i in 0 until schemes.length()) {
                if (lower.startsWith(schemes.optString(i).lowercase() + "://")) return true
            }
        }
        val contains = m.optJSONArray("contains")
        if (contains != null && contains.length() > 0) {
            var all = true
            for (i in 0 until contains.length()) {
                if (!lower.contains(contains.optString(i).lowercase())) all = false
            }
            if (all) return true
        }
        val regex = m.optString("regex", "")
        if (regex.isNotEmpty()) {
            try {
                if (Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(conf)) return true
            } catch (e: Throwable) {
            }
        }
        return false
    }

    fun forConf(context: Context, conf: String): PluginInfo? {
        if (Wg.isConf(conf)) return null
        val all = list(context).filter { enabled(context, it.key) && it.protocol != null && it.lib != null }
        return all.firstOrNull { matches(it, conf) }
    }

    fun usesAwg(context: Context, conf: String): Boolean {
        if (!Wg.isConf(conf)) return false
        val mode = wgMode(context)
        return mode == 1 || (mode == 0 && Wg.isAwg(conf))
    }

    fun awgConf(context: Context, conf: String): String {
        val params = awgParams(context)
        return Wg.applyParams(conf, params, params.optBoolean("_force", false) || wgMode(context) == 1)
    }

    fun wholeConfig(context: Context, text: String): Boolean {
        val t = text.trim()
        if (t.contains("\n").not()) return false
        return list(context).any { enabled(context, it.key) && it.protocol?.optBoolean("multiline", false) == true && matches(it, t) }
    }

    fun prepare(context: Context, text: String): Prepared? {
        val t = text.trim().removePrefix("\uFEFF")
        if (t.isEmpty()) return null
        if (list(context).none { enabled(context, it.key) && it.protocol != null }) return null
        if (wholeConfig(context, t)) return Prepared(listOf(Source(t, false)), null)
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val mine = lines.filter { l -> list(context).any { enabled(context, it.key) && it.lib != null && matches(it, l) } }
        if (mine.isEmpty()) return null
        val rest = lines.filter { it !in mine }
        val sources = ArrayList<Source>()
        mine.forEach { sources.add(Source(it, false)) }
        if (rest.isNotEmpty()) {
            val other = Importer.prepare(rest.joinToString("\n"))
            sources.addAll(other.items)
        }
        return Prepared(sources, null)
    }

    fun describe(context: Context, conf: String): Parsed? {
        val p = forConf(context, conf) ?: return null
        val proto = p.protocol ?: return null
        var endpoint = ""
        val rx = proto.optString("endpointRegex", "")
        if (rx.isNotEmpty()) {
            try {
                val m = Regex(rx, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(conf)
                if (m != null) endpoint = m.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString(":")
            } catch (e: Throwable) {
            }
        }
        if (endpoint.isEmpty()) {
            val m1 = Regex("^\\s*endpoint\\s*=\\s*(\\S+)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(conf)
            val m2 = Regex("^\\s*remote\\s+(\\S+)\\s+(\\d+)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(conf)
            val m3 = Regex("^[a-z0-9+.-]+://(?:[^@/]*@)?([^/?#:]+|\\[[^\\]]+\\]):(\\d+)", RegexOption.IGNORE_CASE).find(conf.trim())
            endpoint = when {
                m1 != null -> m1.groupValues[1]
                m2 != null -> m2.groupValues[1] + ":" + m2.groupValues[2]
                m3 != null -> m3.groupValues[1] + ":" + m3.groupValues[2]
                else -> ""
            }
        }
        var name = ""
        val frag = conf.trim().substringAfter('#', "")
        if (frag.isNotEmpty() && !conf.trim().contains('\n')) name = try { java.net.URLDecoder.decode(frag, "UTF-8") } catch (e: Throwable) { frag }
        if (name.isEmpty()) name = Regex("^\\s*#\\s*name\\s*[:=]\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(conf)?.groupValues?.get(1)?.trim() ?: ""
        if (name.isEmpty()) name = endpoint.substringBefore(':').ifEmpty { p.name }
        return Parsed(name, endpoint, JSONObject())
    }

    fun label(context: Context, conf: String): String? = forConf(context, conf)?.protocol?.optString("label", "")?.ifBlank { null }

    fun pluginConfig(context: Context, info: PluginInfo, conf: String): String {
        var text = effectiveConf(context, conf)
        if (family(info) == "amneziawg") {
            val params = awgParams(context)
            text = Wg.applyParams(text, params, params.optBoolean("_force", false) || wgMode(context) == 1)
        }
        return JSONObject().put("conf", text).put("settings", settings(context, info)).toString()
    }

    fun rules(context: Context): JSONObject {
        val out = JSONObject()
        val keys = listOf("bypassApps", "onlyApps", "directDomains", "proxyDomains", "directIps", "blockDomains")
        val sets = HashMap<String, LinkedHashSet<String>>()
        keys.forEach { sets[it] = LinkedHashSet() }
        for (p in list(context)) {
            if (!enabled(context, p.key)) continue
            val r = p.manifest.optJSONObject("rules") ?: continue
            val vals = settings(context, p)
            for (k in keys) {
                val a = r.optJSONArray(k)
                if (a != null) for (i in 0 until a.length()) a.optString(i).trim().takeIf { it.isNotEmpty() }?.let { sets[k]!!.add(it) }
                val sk = r.optJSONObject("fromSettings")?.optString(k, "") ?: ""
                if (sk.isNotEmpty()) {
                    val v = vals.optString(sk, "")
                    v.split(',', ' ', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { sets[k]!!.add(it) }
                }
            }
        }
        for (k in keys) out.put(k, JSONArray(sets[k]!!.toList()))
        return out
    }

    fun uiFiles(context: Context): List<String> {
        val out = ArrayList<String>()
        for (p in list(context)) {
            if (!enabled(context, p.key)) continue
            val ui = p.manifest.optJSONObject("ui") ?: continue
            val css = ui.optString("css", "")
            val js = ui.optString("js", "")
            if (css.isNotEmpty() && safeName(css) && File(p.dir, css).isFile) out.add("css:" + p.key + "/" + css)
            if (js.isNotEmpty() && safeName(js) && File(p.dir, js).isFile) out.add("js:" + p.key + "/" + js)
        }
        return out
    }

    fun loadCatalog(): String? {
        busy = "Загружаю каталог версий…"
        return try {
            val o = JSONObject(String(Net.first(CATALOG_URLS, "application/json", 25_000), Charsets.UTF_8))
            val arr = o.optJSONArray("engines") ?: JSONArray()
            val list = ArrayList<CatalogFamily>()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val vs = e.optJSONArray("versions") ?: JSONArray()
                val versions = (0 until vs.length()).map {
                    val v = vs.getJSONObject(it)
                    CatalogVersion(v.optString("version"), v.optString("url"), v.optLong("size", 0L), v.optString("notes", ""))
                }.filter { it.version.isNotEmpty() && it.url.startsWith("https://") }
                list.add(CatalogFamily(e.optString("family"), e.optString("name", e.optString("family")), versions))
            }
            catalog = list
            null
        } catch (e: Throwable) {
            e.message ?: "Не удалось загрузить каталог"
        } finally {
            busy = null
        }
    }

    fun installFromUrl(context: Context, url: String, select: Boolean): String? {
        require(url.startsWith("https://")) { "Нужна ссылка https" }
        busy = "Скачиваю плагин…"
        return try {
            val bytes = download(url)
            busy = "Устанавливаю…"
            val info = install(context, ByteArrayInputStream(bytes), url.substringAfterLast('/').substringBefore('?'))
            if (select && family(info) == "amneziawg") setAwgEngine(context, info.key)
            else if (select && info.protocol?.optBoolean("handlesWireGuard", false) == true) setWgEngine(context, info.key)
            null
        } catch (e: Throwable) {
            e.message ?: "Не удалось установить"
        } finally {
            busy = null
        }
    }

    fun family(info: PluginInfo): String = info.manifest.optString("family", info.id)

    fun snapshot(context: Context): JSONObject {
        val out = JSONObject()
        val items = JSONArray()
        for (p in list(context)) {
            val ui = p.manifest.optJSONObject("ui")
            items.put(
                JSONObject()
                    .put("key", p.key)
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("version", p.version)
                    .put("family", family(p))
                    .put("author", p.manifest.optString("author", ""))
                    .put("description", p.manifest.optString("description", ""))
                    .put("kinds", JSONArray(p.kinds))
                    .put("enabled", enabled(context, p.key))
                    .put("wg", p.protocol?.optBoolean("handlesWireGuard", false) == true)
                    .put("schema", p.manifest.optJSONArray("settings") ?: JSONArray())
                    .put("values", settings(context, p))
                    .put("hasUi", ui != null)
            )
        }
        out.put("list", items)
        out.put("engine", wgEngine(context))
        out.put("awgEngine", awgEngine(context))
        out.put("wgMode", wgMode(context))
        out.put("awgParams", awgParams(context))
        out.put("awgKeys", JSONArray(Wg.awgKeys))
        out.put("busy", busy ?: JSONObject.NULL)
        out.put("ui", JSONArray(uiFiles(context)))
        val cat = catalog
        out.put(
            "catalog",
            if (cat == null) JSONObject.NULL else JSONArray(
                cat.map { f ->
                    JSONObject().put("family", f.family).put("name", f.name).put(
                        "versions",
                        JSONArray(f.versions.map { JSONObject().put("version", it.version).put("url", it.url).put("size", it.size).put("notes", it.notes) })
                    )
                }
            )
        )
        val fam = JSONArray()
        for ((id, name) in families) fam.put(JSONObject().put("family", id).put("name", name))
        out.put("families", fam)
        return out
    }
}
