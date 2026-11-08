package tunn.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object AppIcon {
    class Variant(val id: String, val alias: String, val name: String, val note: String)

    val variants = listOf(
        Variant("default", "IconDefault", "Tunn", "Фирменный градиент"),
        Variant("dark", "IconDark", "Тёмная", "Тёмный фон"),
        Variant("light", "IconLight", "Светлая", "Светлый фон"),
        Variant("you", "IconYou", "Material You", "Цвет берётся из обоев"),
        Variant("youlight", "IconYouLight", "Material You светлая", "Светлый тон из обоев")
    )

    private fun component(c: Context, v: Variant) = ComponentName(c.packageName, "tunn.client." + v.alias)

    fun current(c: Context): String {
        val pm = c.packageManager
        for (v in variants) {
            val state = pm.getComponentEnabledSetting(component(c, v))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return v.id
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && v.id == "default") return v.id
        }
        return "default"
    }

    fun set(c: Context, id: String) {
        val target = variants.firstOrNull { it.id == id } ?: return
        val pm = c.packageManager
        pm.setComponentEnabledSetting(component(c, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (v in variants) {
            if (v.id != id) pm.setComponentEnabledSetting(component(c, v), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    private fun square(c: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val src = c.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val side = minOf(src.width, src.height)
        val crop = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        val out = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, crop, Rect(0, 0, 432, 432), null)
        return out
    }

    fun pinCustom(c: Context, uri: Uri): String? {
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return "Ярлыки недоступны"
        if (!sm.isRequestPinShortcutSupported) return "Лаунчер не поддерживает закрепление значков"
        val bitmap = try {
            square(c, uri)
        } catch (e: Exception) {
            null
        } ?: return "Не удалось открыть картинку"
        val label = c.applicationInfo.loadLabel(c.packageManager).toString()
        val info = ShortcutInfo.Builder(c, "custom_" + System.currentTimeMillis())
            .setShortLabel(label)
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(Intent(c, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        return if (sm.requestPinShortcut(info, null)) null else "Не удалось добавить значок"
    }

    fun snapshot(c: Context): JSONObject {
        val list = JSONArray()
        for (v in variants) list.put(JSONObject().put("id", v.id).put("name", v.name).put("note", v.note))
        return JSONObject().put("current", current(c)).put("variants", list)
    }
}
