package app.fasdeq.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.random.Random

object SbCores {
    private fun root(c: Context): File = File(c.filesDir, "sbcores").also { it.mkdirs() }

    fun list(c: Context): List<CoreInfo> {
        val out = ArrayList<CoreInfo>()
        for (dir in root(c).listFiles() ?: return out) {
            val bin = File(dir, "sing-box")
            val meta = File(dir, "meta.json")
            if (!dir.isDirectory || !bin.exists() || !meta.exists()) continue
            try {
                val o = JSONObject(meta.readText())
                out.add(CoreInfo(dir.name, o.optString("label"), o.optString("version"), bin.absolutePath))
            } catch (e: Exception) {
            }
        }
        return out.sortedBy { it.label }
    }

    fun active(c: Context): String = try { File(root(c), "active").readText().trim() } catch (e: Exception) { "" }

    fun setActive(c: Context, id: String) {
        try { File(root(c), "active").writeText(id) } catch (e: Exception) {}
    }

    fun pathOf(c: Context, id: String): String? {
        if (id.isEmpty()) return null
        val f = File(root(c), "$id/sing-box")
        return if (f.exists()) f.absolutePath else null
    }

    fun delete(c: Context, id: String) {
        File(root(c), id).deleteRecursively()
        if (active(c) == id) setActive(c, "")
    }

    private fun untar(gz: ByteArray): ByteArray {
        val input = GZIPInputStream(ByteArrayInputStream(gz))
        val header = ByteArray(512)
        while (true) {
            var read = 0
            while (read < 512) {
                val n = input.read(header, read, 512 - read)
                if (n < 0) throw IllegalStateException("В архиве нет файла sing-box")
                read += n
            }
            if (header.all { it == 0.toByte() }) throw IllegalStateException("В архиве нет файла sing-box")
            val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000')
            val size = String(header, 124, 12, Charsets.US_ASCII).trim { it == '\u0000' || it == ' ' }.toLong(8)
            val data = ByteArray(size.toInt())
            var got = 0
            while (got < data.size) {
                val n = input.read(data, got, data.size - got)
                if (n < 0) break
                got += n
            }
            val pad = ((512 - size % 512) % 512).toInt()
            input.skip(pad.toLong())
            if (name.substringAfterLast('/') == "sing-box" && size > 1024) return data
        }
    }

    fun install(c: Context, raw: ByteArray, label: String): CoreInfo {
        val binary = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) untar(raw) else raw
        require(binary.size > 1024) { "Файл слишком мал" }
        val id = "s" + Integer.toHexString(Random.nextInt(0x100000, 0xFFFFFF))
        val dir = File(root(c), id)
        dir.mkdirs()
        val bin = File(dir, "sing-box")
        try {
            bin.writeBytes(binary)
            bin.setReadable(true, false)
            bin.setExecutable(true, false)
            val out = Cores.run(bin.absolutePath, listOf("version"), 8)
            val m = Regex("sing-box version\\s+([0-9][^\\s]*)").find(out)
                ?: throw IllegalStateException("Это не ядро sing-box или оно не подходит: " + out.trim().take(120))
            val ver = m.groupValues[1]
            val title = label.ifBlank { "sing-box $ver" }
            File(dir, "meta.json").writeText(JSONObject().put("label", title).put("version", ver).toString())
            return CoreInfo(id, title, ver, bin.absolutePath)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun releases(): List<Release> {
        val arch = if (Cores.abi() == "arm64-v8a") "arm64" else "armv7"
        val arr = JSONArray(String(Net.get("https://api.github.com/repos/SagerNet/sing-box/releases?per_page=20", "application/vnd.github+json"), Charsets.UTF_8))
        val out = ArrayList<Release>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optBoolean("prerelease", false)) continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val n = a.optString("name")
                if (n.contains("android-$arch") && n.endsWith(".tar.gz")) {
                    out.add(Release(r.optString("tag_name"), a.optString("browser_download_url"), a.optLong("size")))
                }
            }
        }
        require(out.isNotEmpty()) { "Для этой архитектуры нет готовых сборок sing-box" }
        return out
    }
}
