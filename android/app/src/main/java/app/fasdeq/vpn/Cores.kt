package app.fasdeq.vpn

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.random.Random

data class CoreInfo(val id: String, val label: String, val version: String, val path: String)

data class Release(val tag: String, val url: String, val size: Long)

object Cores {

    private fun root(context: Context): File {
        val dir = File(context.filesDir, "cores")
        dir.mkdirs()
        return dir
    }

    fun linker(): String =
        if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/bin/linker64" else "/system/bin/linker"

    fun abi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    fun list(context: Context): List<CoreInfo> {
        val result = ArrayList<CoreInfo>()
        val dirs = root(context).listFiles() ?: return result
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            val bin = File(dir, "xray")
            val meta = File(dir, "meta.json")
            if (!bin.exists() || !meta.exists()) continue
            try {
                val o = JSONObject(meta.readText())
                result.add(CoreInfo(dir.name, o.optString("label"), o.optString("version"), bin.absolutePath))
            } catch (e: Exception) {
            }
        }
        return result.sortedBy { it.label }
    }

    fun active(context: Context): String = try {
        File(root(context), "active").readText().trim()
    } catch (e: Exception) {
        ""
    }

    fun setActive(context: Context, id: String) {
        try {
            File(root(context), "active").writeText(id)
        } catch (e: Exception) {
        }
    }

    fun activePath(context: Context): String? {
        val id = active(context)
        if (id.isEmpty()) return null
        val bin = File(root(context), "$id/xray")
        return if (bin.exists()) bin.absolutePath else null
    }

    fun pathOf(context: Context, id: String): String? {
        if (id.isEmpty()) return null
        val f = File(root(context), "$id/xray")
        return if (f.exists()) f.absolutePath else null
    }

    fun delete(context: Context, id: String) {
        File(root(context), id).deleteRecursively()
        if (active(context) == id) setActive(context, "")
    }

    fun builtinVersion(): String = try {
        val m = Class.forName("libv2ray.Libv2ray").getMethod("checkVersionX")
        (m.invoke(null) as? String) ?: ""
    } catch (e: Throwable) {
        ""
    }

    fun run(path: String, args: List<String>, timeoutSeconds: Long): String {
        val command = ArrayList<String>()
        command.add(linker())
        command.add(path)
        command.addAll(args)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val out = ByteArrayOutputStream()
        val reader = Thread {
            try {
                process.inputStream.copyTo(out)
            } catch (e: Exception) {
            }
        }
        reader.start()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        reader.join(1000)
        return out.toString(Charsets.UTF_8.name())
    }

    fun version(path: String): String? {
        val out = try {
            run(path, listOf("version"), 6)
        } catch (e: Exception) {
            throw IllegalStateException("Ядро не запускается на этом устройстве: " + (e.message ?: "ошибка"))
        }
        val m = Regex("Xray\\s+([0-9][^\\s]*)").find(out)
        if (m == null) {
            throw IllegalStateException("Это не ядро Xray или оно не подходит: " + out.trim().take(120))
        }
        return m.groupValues[1]
    }

    fun install(context: Context, input: InputStream, label: String): CoreInfo {
        val raw = input.readBytes()
        require(raw.size > 1024) { "Файл слишком мал" }
        val binary: ByteArray = if (raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()) {
            var found: ByteArray? = null
            ZipInputStream(raw.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && (name == "xray" || name == "xray.bin")) {
                        found = zip.readBytes()
                        break
                    }
                }
            }
            found ?: throw IllegalStateException("В архиве нет файла xray")
        } else raw
        val id = "c" + Integer.toHexString(Random.nextInt(0x100000, 0xFFFFFF))
        val dir = File(root(context), id)
        dir.mkdirs()
        val bin = File(dir, "xray")
        try {
            bin.writeBytes(binary)
            bin.setReadable(true, false)
            bin.setExecutable(true, false)
            val ver = version(bin.absolutePath) ?: ""
            val title = label.ifBlank { "Xray $ver" }
            File(dir, "meta.json").writeText(
                JSONObject().put("label", title).put("version", ver).toString()
            )
            return CoreInfo(id, title, ver, bin.absolutePath)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun http(url: String, accept: String = "application/vnd.github+json"): ByteArray = Net.get(url, accept)

    private const val ASSET = "Xray-android-arm64-v8a.zip"

    private fun fromApi(): List<Release> {
        val arr = JSONArray(String(http("https://api.github.com/repos/XTLS/Xray-core/releases?per_page=20"), Charsets.UTF_8))
        val result = ArrayList<Release>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name") == ASSET) {
                    result.add(Release(r.optString("tag_name"), a.optString("browser_download_url"), a.optLong("size")))
                }
            }
        }
        return result
    }

    private fun fromFeed(): List<Release> {
        val xml = String(http("https://github.com/XTLS/Xray-core/releases.atom", "application/atom+xml"), Charsets.UTF_8)
        val tags = Regex("releases/tag/([^\"<]+)").findAll(xml).map { it.groupValues[1] }.distinct().take(20).toList()
        require(tags.isNotEmpty()) { "Не удалось получить список версий" }
        return tags.map { Release(it, "https://github.com/XTLS/Xray-core/releases/download/$it/$ASSET", 0L) }
    }

    fun releases(): List<Release> {
        require(abi() == "arm64-v8a") { "Готовые сборки Xray для Android есть только для arm64-v8a" }
        val api = try {
            fromApi()
        } catch (e: Exception) {
            emptyList()
        }
        if (api.isNotEmpty()) return api
        return fromFeed()
    }

    fun download(url: String): ByteArray = Net.get(url, "application/octet-stream")
}
