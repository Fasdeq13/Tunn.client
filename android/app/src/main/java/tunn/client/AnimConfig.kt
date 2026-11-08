package tunn.client

import org.json.JSONObject

data class AnimConfig(
    val enabled: Boolean = true,
    val intensity: Float = 1f,
    val tempo: Float = 1f,
    val burst: Boolean = true,
    val haptics: Boolean = true,
    val iconFx: Boolean = true,
    val idleMotion: Boolean = true,
    val press: Boolean = true
) {
    fun toJson(): String = JSONObject()
        .put("en", enabled)
        .put("int", intensity.toDouble())
        .put("tempo", tempo.toDouble())
        .put("burst", burst)
        .put("hap", haptics)
        .put("icon", iconFx)
        .put("idle", idleMotion)
        .put("press", press)
        .toString()

    companion object {
        fun fromJson(text: String?): AnimConfig {
            if (text.isNullOrBlank()) return AnimConfig()
            return try {
                val d = AnimConfig()
                val o = JSONObject(text)
                AnimConfig(
                    enabled = o.optBoolean("en", d.enabled),
                    intensity = o.optDouble("int", d.intensity.toDouble()).toFloat().coerceIn(0f, 2f),
                    tempo = o.optDouble("tempo", d.tempo.toDouble()).toFloat().coerceIn(0.2f, 3f),
                    burst = o.optBoolean("burst", d.burst),
                    haptics = o.optBoolean("hap", d.haptics),
                    iconFx = o.optBoolean("icon", d.iconFx),
                    idleMotion = o.optBoolean("idle", d.idleMotion),
                    press = o.optBoolean("press", d.press)
                )
            } catch (e: Throwable) {
                AnimConfig()
            }
        }
    }
}
