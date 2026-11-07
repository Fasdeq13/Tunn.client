package app.fasdeq.vpn

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

object Themes {
    class Theme(val id: String, val name: String, val author: String, val json: JSONObject)

    private fun prefs(c: Context) = c.getSharedPreferences("themes", Context.MODE_PRIVATE)

    var rev by mutableIntStateOf(0)
        private set

    private var cache: Pair<String, Theme?>? = null

    fun list(c: Context): List<Theme> {
        val out = ArrayList<Theme>()
        for ((k, v) in prefs(c).all) {
            if (!k.startsWith("t:")) continue
            try {
                val o = JSONObject(v as String)
                out.add(Theme(k.substring(2), o.optString("name", k.substring(2)), o.optString("author"), o))
            } catch (e: Exception) {
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun activeId(c: Context): String = prefs(c).getString("active", "") ?: ""

    fun active(c: Context): Theme? {
        rev // subscribe
        val id = activeId(c)
        if (id.isEmpty()) return null
        cache?.let { if (it.first == id + rev) return it.second }
        val t = list(c).firstOrNull { it.id == id }
        cache = id + rev to t
        return t
    }

    fun install(c: Context, text: String): String? {
        val o = try { JSONObject(text) } catch (e: Exception) { return "Это не JSON: " + (e.message ?: "") }
        if (!o.has("dark") && !o.has("light") && !o.has("tune")) return "В теме нет ни палитры (dark/light), ни настроек (tune)"
        var id = o.optString("id").trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        if (id.isEmpty()) id = o.optString("name", "theme").trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-").ifEmpty { "theme" }
        if (!o.has("name")) o.put("name", id)
        for (side in listOf("dark", "light")) {
            val p = o.optJSONObject(side) ?: continue
            for (k in p.keys()) if (parseHex(p.optString(k)) == null) return "Неверный цвет $side.$k: ${p.optString(k)}"
        }
        prefs(c).edit().putString("t:$id", o.toString()).putString("active", id).apply()
        rev++
        return null
    }

    fun select(c: Context, id: String) {
        prefs(c).edit().putString("active", id).apply()
        rev++
    }

    fun remove(c: Context, id: String) {
        val e = prefs(c).edit().remove("t:$id")
        if (activeId(c) == id) e.putString("active", "")
        e.apply()
        rev++
    }

    fun forcedMode(t: Theme?): Int = when (t?.json?.optString("mode")) { "light" -> 1; "dark" -> 2; else -> 0 }

    fun tune(t: Theme?): Map<String, Float> {
        val o = t?.json?.optJSONObject("tune") ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optDouble(it).toFloat() }
    }

    fun paint(base: ColorScheme, t: Theme?, dark: Boolean): ColorScheme {
        val p = t?.json?.optJSONObject(if (dark) "dark" else "light") ?: return base
        fun c(k: String): Color? = parseHex(p.optString(k))
        val cont = c("container")
        var s = base
        s = s.copy(
            primary = c("primary") ?: s.primary, onPrimary = c("onPrimary") ?: s.onPrimary,
            primaryContainer = c("primaryContainer") ?: s.primaryContainer, onPrimaryContainer = c("onPrimaryContainer") ?: s.onPrimaryContainer,
            secondary = c("secondary") ?: s.secondary,
            background = c("background") ?: s.background, onBackground = c("onBackground") ?: c("onSurface") ?: s.onBackground,
            surface = c("surface") ?: s.surface, onSurface = c("onSurface") ?: s.onSurface,
            surfaceVariant = c("surfaceVariant") ?: s.surfaceVariant, onSurfaceVariant = c("onSurfaceVariant") ?: s.onSurfaceVariant,
            outline = c("outline") ?: s.outline, error = c("error") ?: s.error
        )
        if (cont != null) s = s.copy(surfaceContainer = cont, surfaceContainerHigh = cont, surfaceContainerHighest = cont, surfaceContainerLow = cont)
        return s
    }
}
