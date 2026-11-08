package tunn.client

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest

data class StoreItem(
    val kind: String,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val file: String,
    val sha256: String,
    val size: Long
)

object Store {
    const val REPO = "Fasdeq13/Tunn-plugins"
    private val bases = listOf(
        "https://raw.githubusercontent.com/$REPO/main/",
        "https://github.com/$REPO/raw/main/",
        "https://cdn.jsdelivr.net/gh/$REPO@main/"
    )

    @Volatile
    var items: List<StoreItem>? = null

    @Volatile
    var busy: String? = null

    @Volatile
    var stamp = 0

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    fun parse(text: String): List<StoreItem> {
        val o = JSONObject(text)
        val out = ArrayList<StoreItem>()
        fun read(key: String, kind: String?) {
            val a = o.optJSONArray(key) ?: return
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val id = e.optString("id")
                val file = e.optString("file")
                if (id.isEmpty() || file.isEmpty() || file.contains("..") || file.startsWith("/")) continue
                val k = kind ?: if ((0 until (e.optJSONArray("kinds")?.length() ?: 0)).any { e.optJSONArray("kinds")!!.optString(it) == "ui-native" }) "ui" else "plugin"
                out.add(StoreItem(k, id, e.optString("name", id), e.optString("version", "1"), e.optString("description"), file, e.optString("sha256").lowercase(), e.optLong("size", 0L)))
            }
        }
        read("plugins", null)
        read("themes", "theme")
        return out
    }

    fun load(): String? {
        busy = "Загружаю каталог…"
        return try {
            val raw = Net.first(bases.map { it + "index.json" }, "application/json", 25_000)
            items = parse(String(raw, Charsets.UTF_8))
            stamp++
            null
        } catch (e: Exception) {
            e.message ?: "Каталог недоступен"
        } finally {
            busy = null
        }
    }

    fun installed(c: Context, it: StoreItem): String? = when (it.kind) {
        "theme" -> Themes.list(c).firstOrNull { t -> t.id == it.id }?.json?.optString("version")?.ifEmpty { "1" }
        else -> Plugins.list(c).filter { p -> p.id == it.id }.maxByOrNull { p -> p.version }?.version
    }

    fun install(c: Context, it: StoreItem): String? {
        busy = "Скачиваю ${it.name}…"
        return try {
            var data: ByteArray? = null
            var last: Exception? = null
            for (b in bases) {
                try {
                    data = Net.get(b + it.file, "application/octet-stream", 60_000, 2)
                    break
                } catch (e: Exception) {
                    last = e
                }
            }
            val bytes = data ?: throw last ?: IllegalStateException("Файл недоступен")
            if (it.sha256.isNotEmpty() && sha256(bytes) != it.sha256) {
                J.e("store", "sha256 mismatch for ${it.id}")
                return "Контрольная сумма не совпала, файл не установлен"
            }
            if (it.kind == "theme") {
                Themes.install(c, String(bytes, Charsets.UTF_8))?.let { return it }
            } else {
                val info = Plugins.install(c, ByteArrayInputStream(bytes), it.file.substringAfterLast('/'))
                if (it.kind == "plugin") Plugins.setEnabled(c, info.key, true)
            }
            J.i("store", "installed ${it.id} ${it.version}")
            stamp++
            null
        } catch (e: Exception) {
            e.message ?: "Не удалось установить"
        } finally {
            busy = null
        }
    }

    fun remove(c: Context, it: StoreItem) {
        if (it.kind == "theme") Themes.remove(c, it.id)
        else for (p in Plugins.list(c).filter { p -> p.id == it.id }) {
            if (UiNative.activeKey(c) == p.key) UiNative.setActive(c, "")
            Plugins.delete(c, p.key)
        }
        stamp++
    }
}
