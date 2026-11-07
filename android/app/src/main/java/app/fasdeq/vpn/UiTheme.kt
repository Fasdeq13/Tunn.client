package app.fasdeq.vpn

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun parseHex(text: String): Color? {
    var t = text.trim().removePrefix("#")
    if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
    if (t.length != 6) return null
    val v = t.toLongOrNull(16) ?: return null
    return Color((0xFF000000 or v).toInt())
}

fun toHex(c: Color): String {
    val r = (c.red * 255f + 0.5f).toInt()
    val g = (c.green * 255f + 0.5f).toInt()
    val b = (c.blue * 255f + 0.5f).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

class Hsl(val h: Float, val s: Float, val l: Float)

fun toHsl(c: Color): Hsl {
    val r = c.red
    val g = c.green
    val b = c.blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d < 1e-5f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return Hsl(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), l)
}

fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}

private fun lum(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

private fun on(c: Color): Color = if (lum(c) > 0.55f) Color(0xFF101414) else Color(0xFFFFFFFF)

fun accentScheme(accent: Color, dark: Boolean, bg: Color?, amoled: Boolean, satK: Float = 1f, surfK: Float = 1f): ColorScheme {
    val a = toHsl(accent)
    val h = a.h
    val s = (max(a.s, if (a.s < 0.08f) 0f else 0.35f) * satK).coerceIn(0f, 1f)
    val tint = if (a.s < 0.08f) 0f else 1f
    val primary = if (dark) hsl(h, s, max(a.l, 0.68f)) else hsl(h, s, min(a.l, 0.42f))
    val bgLight = bg != null && lum(bg) > 0.55f
    val useDark = if (bg != null) !bgLight else dark
    val base = bg ?: if (useDark) hsl(h, 0.10f * tint, if (amoled) 0f else 0.075f) else hsl(h, 0.16f * tint, 0.97f)
    val bh = toHsl(base)
    fun step(k: Float): Color =
        if (useDark) hsl(bh.h, bh.s, (bh.l + k * surfK).coerceAtMost(0.95f)) else hsl(bh.h, bh.s, (bh.l - k * surfK).coerceAtLeast(0.05f))
    val onSurface = if (useDark) hsl(bh.h, 0.06f, 0.9f) else hsl(bh.h, 0.1f, 0.1f)
    val variant = if (useDark) hsl(bh.h, 0.06f, 0.72f) else hsl(bh.h, 0.08f, 0.32f)
    val pc = if (useDark) hsl(h, s * 0.55f, 0.24f) else hsl(h, s * 0.75f, 0.88f)
    val sc = if (useDark) hsl(h, s * 0.25f, 0.27f) else hsl(h, s * 0.35f, 0.89f)
    val container = if (amoled && useDark && bg == null) Color(0xFF0D0D0D) else step(0.05f)
    val colors = if (useDark) {
        darkColorScheme(
            primary = primary, onPrimary = on(primary),
            primaryContainer = pc, onPrimaryContainer = hsl(h, 0.6f * tint, 0.9f),
            secondary = hsl(h, s * 0.3f, 0.75f), secondaryContainer = sc, onSecondaryContainer = hsl(h, 0.3f * tint, 0.9f),
            tertiary = hsl(h + 60f, s * 0.5f, 0.75f),
            background = base, onBackground = onSurface, surface = base, onSurface = onSurface,
            surfaceVariant = step(0.1f), onSurfaceVariant = variant,
            surfaceContainer = container, surfaceContainerHigh = step(0.08f), surfaceContainerHighest = step(0.12f),
            outline = hsl(bh.h, 0.05f, 0.55f), error = Color(0xFFFFB4AB)
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = on(primary),
            primaryContainer = pc, onPrimaryContainer = hsl(h, 0.7f * tint, 0.12f),
            secondary = hsl(h, s * 0.4f, 0.35f), secondaryContainer = sc, onSecondaryContainer = hsl(h, 0.4f * tint, 0.12f),
            tertiary = hsl(h + 60f, s * 0.5f, 0.38f),
            background = base, onBackground = onSurface, surface = base, onSurface = onSurface,
            surfaceVariant = step(0.08f), onSurfaceVariant = variant,
            surfaceContainer = step(0.04f), surfaceContainerHigh = step(0.07f), surfaceContainerHighest = step(0.1f),
            outline = hsl(bh.h, 0.05f, 0.5f), error = Color(0xFFBA1A1A)
        )
    }
    return colors
}

@Composable
fun isDarkFor(theme: Int): Boolean = when (theme) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun TunnTheme(s0: Snap, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val pack = Themes.active(ctx)
    val s = if (pack == null) s0 else s0.copy(tune = s0.tune + Themes.tune(pack), theme = Themes.forcedMode(pack).let { if (it == 0) s0.theme else it })
    var dark = isDarkFor(s.theme)
    val bg = if (s.bgOn && !s.glass) parseHex(s.bgHex) else null
    if (bg != null) dark = lum(bg) <= 0.55f
    val custom = parseHex(s.accentHex)
    val wall = s.glass || s.glassBg != 0L
    val fluent = !s.glass && s.t("fluent", 0f) > 0.5f
    var scheme: ColorScheme = if (fluent) fluentScheme(dark, if (!s.dynamic) custom else null) else if (s.glass) glassScheme(dark, if (s.t("gAcc", 0f) > 0.5f) custom else null) else if (s.dynamic && bg == null && Build.VERSION.SDK_INT >= 31) {
        var d = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        if (dark && s.amoled) {
            d = d.copy(
                background = Color.Black, surface = Color.Black,
                surfaceContainer = Color(0xFF0D0D0D), surfaceContainerHigh = Color(0xFF151515), surfaceContainerHighest = Color(0xFF1D1D1D)
            )
        }
        d
    } else {
        accentScheme(custom ?: Color(0xFF4FD8C8), dark, bg, dark && s.amoled, s.t("mSat", 100f) / 100f, s.t("mSurf", 100f) / 100f)
    }
    scheme = Themes.paint(scheme, pack, dark)
    if (wall && !s.glass) scheme = scheme.copy(background = Color.Transparent, surface = Color.Transparent)
    val ui = UiTune(
        s.t("uiRadius", 100f) / 100f * (if (fluent) 0.36f else 1f), s.t("uiSpace", 100f) / 100f,
        s.t("swStyle", if (fluent) 2f else 0f).toInt(), s.t("cardStyle", 0f).toInt(), fluent
    )
    val inner: @Composable () -> Unit = {
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            LocalUi provides ui,
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * s.t("uiFont", 100f) / 100f)
        ) {
            if (wall) BgHost(s, dark, s.glass, content)
            else androidx.compose.runtime.CompositionLocalProvider(LocalGlass provides GlassEnv(false, dark, 55, null, androidx.compose.ui.unit.IntSize.Zero)) { content() }
        }
    }
    SideEffect {
        (ctx as? BaseUiActivity)?.applyBars(dark)
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(34.dp)
        ),
        content = inner
    )
}

fun fluentScheme(dark: Boolean, accent: Color?): ColorScheme {
    val acc = accent ?: if (dark) Color(0xFF60CDFF) else Color(0xFF0067C0)
    val onAcc = if (dark) Color(0xFF003250) else Color.White
    val bg = if (dark) Color(0xFF202020) else Color(0xFFF3F3F3)
    val card = if (dark) Color(0xFF2B2B2B) else Color(0xFFFBFBFB)
    val high = if (dark) Color(0xFF323232) else Color(0xFFFFFFFF)
    val fg = if (dark) Color.White else Color(0xFF1A1A1A)
    val soft = if (dark) Color(0xFFC8C8C8) else Color(0xFF5E5E5E)
    val stroke = if (dark) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)
    val tintC = lerp(card, acc, if (dark) 0.22f else 0.14f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = acc, onPrimary = onAcc, primaryContainer = tintC, onPrimaryContainer = fg,
        secondary = acc, secondaryContainer = tintC, onSecondaryContainer = fg, tertiary = acc,
        background = bg, onBackground = fg, surface = bg, onSurface = fg,
        surfaceVariant = card, onSurfaceVariant = soft,
        surfaceContainerLowest = bg, surfaceContainerLow = bg, surfaceContainer = card, surfaceContainerHigh = high, surfaceContainerHighest = high,
        outline = stroke, outlineVariant = stroke,
        error = if (dark) Color(0xFFFF99A4) else Color(0xFFC42B1C)
    )
}

fun glassScheme(dark: Boolean, accent: Color? = null): ColorScheme {
    val blue = if (accent != null) { val h = toHsl(accent); if (dark) hsl(h.h, h.s, max(h.l, 0.6f)) else hsl(h.h, h.s, min(h.l, 0.5f)) } else if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val fg = if (dark) Color.White else Color(0xFF101114)
    val soft = if (dark) Color(0xFFEBEBF5).copy(alpha = 0.62f) else Color(0xFF3C3C43).copy(alpha = 0.68f)
    val glass = { a: Float -> if (dark) Color(0xFF1C1C24).copy(alpha = a) else Color.White.copy(alpha = a) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = blue, onPrimary = Color.White,
        primaryContainer = blue.copy(alpha = 0.30f), onPrimaryContainer = fg,
        secondary = blue, secondaryContainer = glass(0.30f), onSecondaryContainer = fg,
        tertiary = blue,
        background = Color.Transparent, onBackground = fg, surface = Color.Transparent, onSurface = fg,
        surfaceVariant = glass(0.28f), onSurfaceVariant = soft,
        surfaceContainer = glass(0.34f), surfaceContainerHigh = glass(if (dark) 0.78f else 0.86f), surfaceContainerHighest = glass(0.42f),
        outline = soft, outlineVariant = soft.copy(alpha = 0.3f),
        error = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    )
}
