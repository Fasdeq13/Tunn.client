package app.fasdeq.vpn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.max
import kotlin.math.min

class GlassEnv(
    val on: Boolean, val dark: Boolean, val level: Int, val blurred: ImageBitmap?, val root: IntSize,
    val sharpSh: BitmapShader? = null, val blurSh: BitmapShader? = null, val scaleS: Float = 1f, val scaleB: Float = 1f,
    val reg: GlassRegistry? = null,
    val prm: GlassParams = GlassParams(),
    val wall: Boolean = false
)

class GlassParams(
    val blur: Float = 50f, val tint: Float = 50f, val refr: Float = 100f, val edge: Float = 18f, val disp: Float = 100f,
    val spec: Float = 100f, val sat: Float = 155f, val merge: Float = 10f, val drop: Float = 100f
)

fun Snap.t(key: String, def: Float): Float = tune[key] ?: def

fun Snap.glassParams() = GlassParams(
    t("gBlur", 50f), t("gTint", 50f), t("gRefr", 100f), t("gEdge", 18f), t("gDisp", 100f),
    t("gSpec", 100f), t("gSat", 155f), t("gMerge", 10f), t("gDrop", 100f)
)

class UiTune(val radius: Float = 1f, val space: Float = 1f, val swIos: Boolean = false, val card: Int = 0)

val LocalUi = compositionLocalOf { UiTune() }

@Composable
fun cr(v: Int): androidx.compose.ui.unit.Dp = (v * LocalUi.current.radius).dp

@Composable
fun sp(v: Int): androidx.compose.ui.unit.Dp = (v * LocalUi.current.space).dp

val LocalGlass = compositionLocalOf { GlassEnv(false, false, 55, null, IntSize.Zero) }

class GRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val cl: Float, val ct: Float, val cr: Float, val cb: Float)

class GlassRegistry {
    val items = androidx.compose.runtime.mutableStateMapOf<Any, GRect>()
    val drops = androidx.compose.runtime.mutableStateMapOf<Any, GRect>()
}

private const val MAXN = 24

private const val GLASS_AGSL = """
uniform shader bg;
uniform shader bgb;
uniform float scaleS;
uniform float scaleB;
uniform float edgeW;
uniform float strength;
uniform float disp;
uniform float specM;
uniform float dropM;
uniform float4 tint;
uniform float sat;
uniform float dark;
uniform float k;
uniform int n;
uniform float4 rA[24];
uniform float4 rB[24];
uniform float4 rC[24];
uniform int nd;
uniform float4 dA[8];
uniform float4 dB[8];
uniform float4 dC[8];

float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float sdBox(float2 p, float2 b) {
    float2 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}

float smin(float a, float b, float kk) {
    float h = max(kk - abs(a - b), 0.0) / kk;
    return min(a, b) - h * h * kk * 0.25;
}

float field(float2 p) {
    float d = 100000.0;
    for (int i = 0; i < 24; i++) {
        if (i >= n) break;
        float4 a = rA[i];
        float4 cl = rC[i];
        float2 hs = a.zw * 0.5;
        float di = sdRound(p - (a.xy + hs), hs, min(rB[i].x, min(hs.x, hs.y)));
        di = max(di, sdBox(p - (cl.xy + cl.zw) * 0.5, (cl.zw - cl.xy) * 0.5));
        d = smin(d, di, k);
    }
    return d;
}

float dropField(float2 p) {
    float d = 100000.0;
    for (int i = 0; i < 8; i++) {
        if (i >= nd) break;
        float4 a = dA[i];
        float4 cl = dC[i];
        float2 hs = a.zw * 0.5;
        float di = sdRound(p - (a.xy + hs), hs, min(dB[i].x, min(hs.x, hs.y)));
        di = max(di, sdBox(p - (cl.xy + cl.zw) * 0.5, (cl.zw - cl.xy) * 0.5));
        d = smin(d, di, k * 0.7);
    }
    return d;
}

half4 shade(float2 fc, float d, float2 nrm, float eW, float str, float tA, float rimM) {
    float depth = max(-d, 0.0);
    float t = clamp(1.0 - depth / eW, 0.0, 1.0);
    float f = t * t;
    float2 d0 = nrm * f * str;
    float3 blurC = float3(
        bgb.eval((fc + d0) * scaleB).r,
        bgb.eval((fc + d0 * (1.0 + 0.10 * disp)) * scaleB).g,
        bgb.eval((fc + d0 * (1.0 + 0.22 * disp)) * scaleB).b);
    float3 sharpC = float3(
        bg.eval((fc + d0) * scaleS).r,
        bg.eval((fc + d0 * (1.0 + 0.10 * disp)) * scaleS).g,
        bg.eval((fc + d0 * (1.0 + 0.22 * disp)) * scaleS).b);
    float3 col = mix(blurC, sharpC, clamp(f * 0.6 + (rimM - 1.0) * 1.2, 0.0, 0.85));
    float luma = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(luma), col, sat);
    col = mix(col, tint.rgb, tA * (1.0 - f * 0.35));
    float2 L = normalize(float2(-0.6, -0.8));
    float s1 = max(dot(nrm, L), 0.0);
    float s2 = max(dot(nrm, -L), 0.0);
    float rim = smoothstep(2.4 * rimM, 0.0, depth);
    float spec = rim * (pow(s1, 1.4) + 0.45 * pow(s2, 1.4)) * rimM * specM;
    float glow = smoothstep(eW, 0.0, depth) * (dark > 0.5 ? 0.07 : 0.10) * (0.5 + 0.5 * s1) * rimM * specM;
    float sheen = smoothstep(eW * 2.0, 0.0, depth) * max(-nrm.y, 0.0) * (dark > 0.5 ? 0.05 : 0.12);
    col = col + (dark > 0.5 ? 0.70 : 0.95) * spec + glow + sheen;
    col = col - (dark > 0.5 ? 0.0 : 0.05) * smoothstep(1.6, 0.0, depth) * (1.0 - spec);
    float a = clamp(0.5 - d, 0.0, 1.0);
    return half4(half3(clamp(col, 0.0, 1.0)) * half(a), half(a));
}

half4 main(float2 fc) {
    float e = 1.0;
    half4 base = half4(0.0);
    float d = field(fc);
    if (d <= 1.0) {
        float2 g = float2(
            field(fc + float2(e, 0.0)) - field(fc - float2(e, 0.0)),
            field(fc + float2(0.0, e)) - field(fc - float2(0.0, e)));
        base = shade(fc, d, g / max(length(g), 0.0001), edgeW, strength, tint.a, 1.0);
    }
    if (nd > 0) {
        float dd = dropField(fc);
        if (dd <= 1.0) {
            float2 g2 = float2(
                dropField(fc + float2(e, 0.0)) - dropField(fc - float2(e, 0.0)),
                dropField(fc + float2(0.0, e)) - dropField(fc - float2(0.0, e)));
            half4 top = shade(fc, dd, g2 / max(length(g2), 0.0001), edgeW * 0.7, strength * 1.5 * dropM, tint.a * 0.3, 1.3);
            return top + base * (1.0 - top.a);
        }
    }
    return base;
}
"""

object GlassBg {
    private fun file(c: Context) = File(c.filesDir, "glass_bg.jpg")

    fun save(c: Context, uri: android.net.Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return "Не удалось открыть картинку"
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
            val bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return "Не удалось открыть картинку"
            file(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            null
        } catch (e: Exception) {
            "Не удалось сохранить картинку"
        }
    }

    fun clear(c: Context) {
        file(c).delete()
    }

    fun load(c: Context): Bitmap? = try {
        if (file(c).exists()) BitmapFactory.decodeFile(file(c).absolutePath) else null
    } catch (e: Throwable) {
        null
    }

    fun generated(w: Int, h: Int, dark: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
        val bottom = if (dark) 0xFF141417.toInt() else 0xFFFFFFFF.toInt()
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        fun blob(cx: Float, cy: Float, r: Float, color: Int) {
            p.shader = RadialGradient(cx * w, cy * h, r * max(w, h), color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            cv.drawCircle(cx * w, cy * h, r * max(w, h), p)
        }
        val spot = if (dark) 0x40FFFFFF else 0x30000000
        blob(0.2f, 0.15f, 0.5f, spot)
        blob(0.9f, 0.8f, 0.55f, spot)
        return b
    }

    private fun cropTo(src: Bitmap, w: Int, h: Int): Bitmap {
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()
        val scale = max(w / sw, h / sh)
        val cw = (w / scale).toInt().coerceIn(1, src.width)
        val ch = (h / scale).toInt().coerceIn(1, src.height)
        val x = (src.width - cw) / 2
        val y = (src.height - ch) / 2
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(src, x, y, cw, ch), w, h, true)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        if (r < 1) return
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, len: Int, lines: Int, stride: Int, step: Int) {
            for (l in 0 until lines) {
                val base = l * stride
                var sr = 0; var sg = 0; var sb = 0
                for (i in -r..r) {
                    val c = src[base + (i.coerceIn(0, len - 1)) * step]
                    sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
                }
                val n = 2 * r + 1
                for (i in 0 until len) {
                    dst[base + i * step] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                    val add = src[base + (i + r + 1).coerceIn(0, len - 1) * step]
                    val rem = src[base + (i - r).coerceIn(0, len - 1) * step]
                    sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                    sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                    sb += (add and 255) - (rem and 255)
                }
            }
        }
        repeat(3) {
            pass(px, tmp, w, h, w, 1)
            pass(tmp, px, h, w, 1, w)
        }
    }

    class Result(val full: ImageBitmap, val blurred: ImageBitmap)

    fun build(c: Context, rootW: Int, rootH: Int, dark: Boolean, blurK: Float, bgBlur: Float): Result {
        val w = max(rootW, 2)
        val h = max(rootH, 2)
        val user = load(c)
        val fw = min(w, 1080)
        val fh = max(2, (fw.toLong() * h / w).toInt())
        var full = if (user != null) cropTo(user, fw, fh) else generated(fw, fh, dark)
        val sw = 110
        val sh = max(2, sw * h / w)
        if (bgBlur > 0f) {
            val bs = Bitmap.createScaledBitmap(full, sw, sh, true)
            val bp = IntArray(sw * sh)
            bs.getPixels(bp, 0, sw, 0, 0, sw, sh)
            boxBlur(bp, sw, sh, 1 + (bgBlur / 100f * 9f).toInt())
            val bo = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            bo.setPixels(bp, 0, sw, 0, 0, sw, sh)
            full = Bitmap.createScaledBitmap(bo, fw, fh, true)
        }
        val small = Bitmap.createScaledBitmap(full, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        val r = 1 + (blurK / 100f * 7f).toInt()
        boxBlur(px, sw, sh, r)
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, sw, 0, 0, sw, sh)
        return Result(full.asImageBitmap(), out.asImageBitmap())
    }
}

private val saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.55f) })

private fun lerpf(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
fun Modifier.glass(shape: Shape, fallback: Color = Color.Transparent): Modifier {
    val g = LocalGlass.current
    if (!g.on || g.blurred == null || g.root.width == 0) {
        return if (fallback == Color.Transparent) this else this.drawBehind {
            val o = shape.createOutline(size, layoutDirection, this)
            val path = outlinePath(o)
            drawPath(path, fallback)
        }
    }
    var pos by remember { mutableStateOf(Offset.Zero) }
    val t = g.level / 100f
    if (Build.VERSION.SDK_INT >= 33 && g.sharpSh != null && g.blurSh != null && g.reg != null) return this.glassMerged(shape, g.reg)
    val tint = if (g.dark) Color(0xFF16181F).copy(alpha = lerpf(0.58f, 0.10f, t)) else Color.White.copy(alpha = lerpf(0.62f, 0.12f, t))
    val blurred = g.blurred
    val root = g.root
    return this.onGloballyPositioned { pos = it.positionInWindow() }.drawBehind {
        val o = shape.createOutline(size, layoutDirection, this)
        val path = outlinePath(o)
        clipPath(path) {
            drawImage(
                blurred,
                dstOffset = IntOffset((-pos.x).toInt(), (-pos.y).toInt()),
                dstSize = root,
                colorFilter = saturate,
                filterQuality = FilterQuality.Low
            )
            drawRect(tint)
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (g.dark) 0.14f else 0.45f), Color.Transparent),
                    startY = 0f, endY = size.height * 0.55f
                )
            )
        }
        val rim = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (g.dark) 0.60f else 0.95f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = if (g.dark) 0.32f else 0.70f)
            ),
            start = Offset(0f, 0f), end = Offset(size.width, size.height)
        )
        drawPath(path, rim, style = Stroke(1.2.dp.toPx()))
        if (!g.dark) drawPath(path, Color.Black.copy(alpha = 0.05f), style = Stroke(0.6.dp.toPx()))
    }
}

@Composable
private fun Modifier.glassMerged(shape: Shape, reg: GlassRegistry, drop: Boolean = false): Modifier {
    val map = if (drop) reg.drops else reg.items
    val key = remember { Any() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    androidx.compose.runtime.DisposableEffect(key) { onDispose { map.remove(key) } }
    return this.onGloballyPositioned { c ->
        val clip = c.boundsInWindow()
        if (!c.isAttached || clip.width <= 0f || clip.height <= 0f) {
            map.remove(key)
        } else {
            val p = c.positionInWindow()
            val sz = androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat())
            val o = shape.createOutline(sz, dir, density)
            val r = if (o is androidx.compose.ui.graphics.Outline.Rounded) o.roundRect.topLeftCornerRadius.x else 0f
            map[key] = GRect(p.x, p.y, sz.width, sz.height, r, clip.left, clip.top, clip.right, clip.bottom)
        }
    }
}

@androidx.annotation.RequiresApi(33)
@Composable
private fun GlassLayer(g: GlassEnv, reg: GlassRegistry) {
    val shader = remember { RuntimeShader(GLASS_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val t = g.level / 100f
    val pm = g.prm
    val tintA = (lerpf(0.50f, 0.06f, t) * pm.tint / 50f).coerceIn(0f, 0.95f)
    val tint = if (g.dark) floatArrayOf(0.09f, 0.10f, 0.14f) else floatArrayOf(1f, 1f, 1f)
    shader.setInputShader("bg", g.sharpSh!!)
    shader.setInputShader("bgb", g.blurSh!!)
    Box(Modifier.fillMaxSize().drawBehind {
        val list = reg.items.values.toList().take(MAXN)
        val a = FloatArray(MAXN * 4)
        val b = FloatArray(MAXN * 4)
        val c = FloatArray(MAXN * 4)
        list.forEachIndexed { i, r ->
            a[i * 4] = r.x; a[i * 4 + 1] = r.y; a[i * 4 + 2] = r.w; a[i * 4 + 3] = r.h
            b[i * 4] = r.r
            c[i * 4] = r.cl; c[i * 4 + 1] = r.ct; c[i * 4 + 2] = r.cr; c[i * 4 + 3] = r.cb
        }
        val dl = reg.drops.values.toList().take(8)
        val da = FloatArray(32)
        val db = FloatArray(32)
        val dc = FloatArray(32)
        dl.forEachIndexed { i, r ->
            da[i * 4] = r.x; da[i * 4 + 1] = r.y; da[i * 4 + 2] = r.w; da[i * 4 + 3] = r.h
            db[i * 4] = r.r
            dc[i * 4] = r.cl; dc[i * 4 + 1] = r.ct; dc[i * 4 + 2] = r.cr; dc[i * 4 + 3] = r.cb
        }
        shader.setIntUniform("nd", dl.size)
        shader.setFloatUniform("dA", da)
        shader.setFloatUniform("dB", db)
        shader.setFloatUniform("dC", dc)
        shader.setIntUniform("n", list.size)
        shader.setFloatUniform("rA", a)
        shader.setFloatUniform("rB", b)
        shader.setFloatUniform("rC", c)
        shader.setFloatUniform("scaleS", g.scaleS)
        shader.setFloatUniform("scaleB", g.scaleB)
        shader.setFloatUniform("edgeW", pm.edge * density)
        shader.setFloatUniform("strength", (10f + 26f * t) * density * pm.refr / 100f)
        shader.setFloatUniform("k", max(pm.merge, 0.5f) * density)
        shader.setFloatUniform("disp", pm.disp / 100f)
        shader.setFloatUniform("specM", pm.spec / 100f)
        shader.setFloatUniform("dropM", pm.drop / 100f)
        shader.setFloatUniform("tint", tint[0], tint[1], tint[2], tintA)
        shader.setFloatUniform("sat", pm.sat / 100f)
        shader.setFloatUniform("dark", if (g.dark) 1f else 0f)
        drawRect(brush)
    })
}

@Composable
fun BgHost(s: Snap, dark: Boolean, glassOn: Boolean, content: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val built = if (size.width > 0) remember(size, dark, s.glassBg, s.t("gBlur", 50f), s.t("bgBlur", 0f)) { GlassBg.build(ctx, size.width, size.height, dark, s.t("gBlur", 50f), s.t("bgBlur", 0f)) } else null
    val sharpSh = if (built != null) remember(built) { BitmapShader(built.full.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) } else null
    val blurSh = if (built != null) remember(built) { BitmapShader(built.blurred.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) } else null
    val rw = max(size.width, 1).toFloat()
    val reg = remember { GlassRegistry() }
    val env = GlassEnv(
        glassOn, dark, s.glassLevel, built?.blurred, size, sharpSh, blurSh,
        (built?.full?.width ?: 1) / rw, (built?.blurred?.width ?: 1) / rw,
        if (glassOn && Build.VERSION.SDK_INT >= 33 && sharpSh != null) reg else null,
        s.glassParams(), true
    )
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        if (built != null) {
            Image(built.full, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = (s.t("bgDim", 0f) / 100f * 0.8f + (if (glassOn && dark) 0.12f else 0f)).coerceIn(0f, 0.92f))) })
        }
        if (Build.VERSION.SDK_INT >= 33 && built != null && env.reg != null) GlassLayer(env, reg)
        androidx.compose.runtime.CompositionLocalProvider(LocalGlass provides env) { content() }
    }
}


@Composable
fun Modifier.glassDrop(shape: Shape): Modifier {
    val g = LocalGlass.current
    if (g.on && Build.VERSION.SDK_INT >= 33 && g.reg != null) return this.glassMerged(shape, g.reg, true)
    return this.drawBehind {
        val o = shape.createOutline(size, layoutDirection, this)
        val path = outlinePath(o)
        drawPath(path, Color.White.copy(alpha = if (g.dark) 0.22f else 0.55f))
        drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(1.dp.toPx()))
    }
}

private fun outlinePath(o: androidx.compose.ui.graphics.Outline): Path = when (o) {
    is androidx.compose.ui.graphics.Outline.Rectangle -> Path().apply { addRect(o.rect) }
    is androidx.compose.ui.graphics.Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
    is androidx.compose.ui.graphics.Outline.Generic -> o.path
}
