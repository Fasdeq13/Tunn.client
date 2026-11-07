package app.fasdeq.vpn

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer

object UiNative {
    private const val PREFS = "fasdeq"
    private const val ACTIVE = "ui_native"
    private const val BOOT = "ui_native_boot"
    private const val SCALE = 0.62f

    var rev by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(c: Context): List<PluginInfo> = Plugins.list(c).filter { it.manifest.optJSONObject("uiNative") != null }

    fun libOf(p: PluginInfo): File? {
        val n = p.manifest.optJSONObject("uiNative")?.optString("lib", "") ?: ""
        if (n.isEmpty() || !Plugins.safeName(n)) return null
        val f = File(p.dir, n)
        return if (f.isFile) f else null
    }

    fun activeKey(c: Context): String {
        rev
        return prefs(c).getString(ACTIVE, "") ?: ""
    }

    @Volatile
    private var armed = ""

    fun setActive(c: Context, key: String) {
        armed = ""
        prefs(c).edit().putString(ACTIVE, key).remove(BOOT).commit()
        J.i("ui", "native interface: " + key.ifEmpty { "off" })
        rev++
    }

    fun arm(c: Context): PluginInfo? {
        val key = activeKey(c)
        if (key.isEmpty()) return null
        val p = prefs(c)
        if (armed == key) return list(c).firstOrNull { it.key == key }
        if (p.getString(BOOT, "") == key) {
            J.e("ui", "native interface $key did not survive the previous start, disabled")
            setActive(c, "")
            return null
        }
        val info = list(c).firstOrNull { it.key == key }
        if (info == null || libOf(info) == null) {
            setActive(c, "")
            return null
        }
        p.edit().putString(BOOT, key).commit()
        armed = key
        return info
    }

    fun disarm(c: Context) {
        prefs(c).edit().remove(BOOT).apply()
    }

    fun stateJson(s: Snap, live: Live): String {
        val o = JSONObject()
        o.put("status", s.status)
        val sel = s.selectedServer()
        o.put("server", JSONObject().put("id", sel?.id ?: "").put("name", sel?.name ?: ""))
        val arr = JSONArray()
        for (x in s.servers) {
            arr.put(JSONObject().put("id", x.id).put("name", x.name).put("proto", x.proto).put("group", x.group).put("ping", s.pings[x.id]?.ms ?: -1))
        }
        o.put("servers", arr)
        o.put("rx", live.rx).put("tx", live.tx).put("down", live.down).put("up", live.up).put("uptime", live.uptime)
        o.put("dark", s.theme)
        return o.toString()
    }
}

@Composable
fun NativeUiHost(a: ComposeActivity, s: Snap, plugin: PluginInfo, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val q = remember { 0.62f }
    val latest = remember { mutableStateOf(Pair(s, a.live.value)) }
    latest.value = Pair(s, a.live.value)

    BackHandler { Native.uiBack() }

    LaunchedEffect(size) {
        if (size.width < 16 || size.height < 16) return@LaunchedEffect
        val w = (size.width * q).toInt().coerceAtLeast(16)
        val h = (size.height * q).toInt().coerceAtLeast(16)
        val lib = UiNative.libOf(plugin)
        if (lib == null) {
            UiNative.setActive(ctx, "")
            onExit()
            return@LaunchedEffect
        }
        val res = withContext(Dispatchers.Default) {
            try {
                JSONObject(Native.uiOpen(lib.absolutePath, w, h, density * q))
            } catch (e: Throwable) {
                JSONObject().put("ok", false).put("error", e.message ?: "load failed")
            }
        }
        if (!res.optBoolean("ok")) {
            J.e("ui", "load failed: " + res.optString("error"))
            a.say("Интерфейс не загрузился: " + res.optString("error"))
            UiNative.setActive(ctx, "")
            onExit()
            return@LaunchedEffect
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val buf = ByteBuffer.allocateDirect(w * h * 4)
        var last = ""
        val t0 = System.currentTimeMillis()
        var stable = 0
        try {
            while (isActive) {
                val (st, lv) = latest.value
                val js = UiNative.stateJson(st, lv)
                if (js != last) {
                    last = js
                    Native.uiState(js)
                }
                val r = withContext(Dispatchers.Default) {
                    val code = Native.uiRender(buf, w, h, w * 4, (System.currentTimeMillis() - t0).toInt())
                    if (code == 1) {
                        buf.rewind()
                        bmp.copyPixelsFromBuffer(buf)
                    }
                    code
                }
                if (r == 1) {
                    frame = bmp.asImageBitmap()
                    tick++
                } else if (r < 0) {
                    J.e("ui", "render failed: $r")
                    a.say("Интерфейс вернул ошибку, включён стандартный")
                    UiNative.setActive(ctx, "")
                    onExit()
                    return@LaunchedEffect
                }
                while (true) {
                    val act = Native.uiPoll()
                    if (act.isNullOrEmpty()) break
                    handle(a, act, ctx, onExit)
                }
                if (++stable == 180) UiNative.disarm(ctx)
                delay(16)
            }
        } finally {
            Native.uiClose()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(size) {
                awaitEachGesture {
                    val d = awaitFirstDown()
                    Native.uiTouch(0, d.position.x * q, d.position.y * q)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        if (c.pressed) {
                            Native.uiTouch(1, c.position.x * q, c.position.y * q)
                            c.consume()
                        } else {
                            Native.uiTouch(2, c.position.x * q, c.position.y * q)
                            c.consume()
                            break
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }) {
            val t = tick
            val f = frame
            if (f != null && t >= 0) drawImage(f, dstOffset = IntOffset.Zero, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.Low)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(8.dp).size(34.dp).alpha(0.45f)
                .clip(CircleShape).background(Color.White).clickable {
                    UiNative.setActive(ctx, "")
                    onExit()
                },
            contentAlignment = Alignment.Center
        ) { Text("×", color = Color.Black) }
    }
}

private fun handle(a: ComposeActivity, raw: String, ctx: Context, onExit: () -> Unit) {
    val o = try { JSONObject(raw) } catch (e: Exception) { return }
    when (o.optString("op")) {
        "toggle" -> a.call(if (a.snap.value.status == "On" || a.snap.value.status == "Connecting") "disconnect" else "connect")
        "connect" -> a.call("connect")
        "disconnect" -> a.call("disconnect")
        "select" -> a.call("select", o.optString("id"))
        "exit" -> {
            UiNative.setActive(ctx, "")
            onExit()
        }
    }
}
