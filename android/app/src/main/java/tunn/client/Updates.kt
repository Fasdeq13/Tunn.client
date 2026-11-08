package tunn.client

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateState(
    val tag: String = "",
    val url: String = Updates.RELEASES,
    val notes: String = "",
    val available: Boolean = false,
    val checking: Boolean = false,
    val error: String? = null,
    val current: String = "",
    val packName: String = "",
    val packKind: String = "",
    val busy: String = ""
)

object Updates {
    const val RELEASES = "https://github.com/Fasdeq13/FasdeqVPN/releases/"
    private const val API = "https://api.github.com/repos/Fasdeq13/FasdeqVPN/releases/latest"

    @Volatile
    var state = UpdateState()

    @Volatile
    private var checkedAt = 0L

    @Volatile
    var packAsset: PackAsset? = null

    suspend fun applyPack(context: Context): String? {
        val pack = packAsset ?: return "Нет пакета обновления"
        if (state.busy.isNotEmpty()) return null
        val r = withContext(Dispatchers.IO) { UpdatePack.apply(context, pack) { state = state.copy(busy = it) } }
        state = state.copy(busy = "")
        if (r == null) {
            packAsset = null
            state = state.copy(packName = "", packKind = "")
        }
        if (r == "restart") {
            state = state.copy(packName = "", packKind = "", busy = "")
            UpdatePack.restart(context)
            return null
        }
        return r
    }

    private fun currentVersion(context: Context): String = Layer.version().ifEmpty { try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Throwable) {
        ""
    } }

    private fun apkVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Throwable) {
        ""
    }

    private fun numbers(v: String): List<Int> =
        Regex("[0-9]+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()

    fun newer(tag: String, current: String): Boolean {
        val a = numbers(tag)
        val b = numbers(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun skip(context: Context, tag: String) {
        context.getSharedPreferences("tunn", Context.MODE_PRIVATE).edit().putString("update_skip", tag).apply()
        state = state.copy(available = false)
    }

    suspend fun check(context: Context, force: Boolean) {
        if (BuildConfig.FDROID) return
        val now = System.currentTimeMillis()
        if (!force && now - checkedAt < 6L * 3600_000L) return
        val current = currentVersion(context)
        state = state.copy(checking = true, error = null, current = current)
        val result = withContext(Dispatchers.IO) {
            try {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "Tunn")
                try {
                    when (c.responseCode) {
                        404 -> Pair<JSONObject?, String?>(null, null)
                        200 -> Pair<JSONObject?, String?>(JSONObject(c.inputStream.bufferedReader().readText()), null)
                        else -> Pair<JSONObject?, String?>(null, "GitHub вернул " + c.responseCode)
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: Throwable) {
                Pair<JSONObject?, String?>(null, e.message ?: "Нет связи с GitHub")
            }
        }
        checkedAt = System.currentTimeMillis()
        val json = result.first
        if (json == null) {
            state = state.copy(checking = false, error = result.second, available = false)
            return
        }
        val tag = json.optString("tag_name", "")
        val skipped = context.getSharedPreferences("tunn", Context.MODE_PRIVATE).getString("update_skip", "") ?: ""
        val isNew = newer(tag, current)
        val pack = UpdatePack.pick(json.optJSONArray("assets"), current, UpdatePack.appliedFix(context, current))
        packAsset = pack
        state = UpdateState(
            packName = pack?.name ?: "",
            packKind = pack?.kind ?: "",
            tag = tag,
            url = json.optString("html_url", RELEASES).ifBlank { RELEASES },
            notes = json.optString("body", "").take(600),
            available = isNew && (force || tag != skipped),
            checking = false,
            error = null,
            current = current
        )
    }
}
