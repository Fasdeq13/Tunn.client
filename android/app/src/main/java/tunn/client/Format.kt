package tunn.client

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun formatBytes(bytes: Long): String {
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return if (i == 0) "$bytes Б" else "%.1f %s".format(value, units[i])
}

fun formatSpeed(bytes: Long): String = formatBytes(bytes) + "/с"

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

fun displayName(context: Context, uri: Uri): String {
    var name: String? = null
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
    } catch (e: Exception) {
    }
    val raw = name ?: uri.lastPathSegment ?: ""
    return raw.removeSuffix(".conf").trim()
}
