package app.fasdeq.vpn

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.net.VpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ComposeActivity : BaseUiActivity() {
    private lateinit var bridge: WebBridge
    val snap = mutableStateOf(Snap())
    val live = mutableStateOf(Live())
    val toast = mutableStateOf<String?>(null)

    private val waiters = ConcurrentHashMap<Int, (JSONObject) -> Unit>()
    private var seq = 0

    override fun onPushed(json: String) {
        try {
            snap.value = parseSnap(json)
        } catch (e: Throwable) {
        }
    }

    override fun onReply(id: Int, value: JSONObject) {
        val f = waiters.remove(id) ?: return
        runOnUiThread { f(value) }
    }

    override fun applyBars(dark: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }

    fun call(name: String, payload: Any? = null, done: ((JSONObject) -> Unit)? = null) {
        val id = ++seq
        if (done != null) waiters[id] = done
        val raw = when (payload) {
            null -> ""
            is String -> JSONObject.quote(payload)
            else -> payload.toString()
        }
        bridge.act(name, raw, id)
    }

    fun set(path: String, value: Any?) {
        call("set", JSONObject().put("path", path).put("value", value ?: JSONObject.NULL))
    }

    fun say(text: String) {
        toast.value = text
    }

    fun reapplyIfOn() {
        val st = FasdeqApp.state
        if (st.status != Status.On) return
        lifecycleScope.launch {
            st.disconnect()
            delay(700)
            requestConnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotifications()
        if (savedInstanceState == null) {
            val st = FasdeqApp.state
            if (st.autoConnect && st.status == Status.Off && st.selected != null && VpnService.prepare(this) == null) {
                st.connect()
            }
        }
        bridge = WebBridge(this)
        bridge.push()
        setContent { AppRoot(this) }

        lifecycleScope.launch {
            Updates.check(this@ComposeActivity, false)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var last = stateHash()
                var lastAt = 0L
                var lastPing = bridge.pingHash()
                var lastPingAt = 0L
                var lastLive = Live()
                bridge.push()
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val h = stateHash()
                    val ph = bridge.pingHash()
                    if ((h != last && now - lastAt >= 250) || (ph != lastPing && now - lastPingAt >= 400)) {
                        if (h != last) lastAt = now
                        if (ph != lastPing) lastPingAt = now
                        last = h
                        lastPing = ph
                        bridge.push()
                    }
                    val s = FasdeqApp.state
                    val l = Live(s.rx, s.tx, s.downSpeed, s.upSpeed, s.uptime)
                    if (l != lastLive) {
                        lastLive = l
                        live.value = l
                    }
                    delay(250)
                }
            }
        }
    }

    private fun stateHash(): Int {
        val s = FasdeqApp.state
        return listOf(
            s.status, s.selectedId, s.error,
            s.servers.toList(), s.groups.toList(),
            s.themeMode, s.dynamicColors, s.amoled, s.glass, s.glassLevel, s.glassBg, s.autoConnect, s.dnsPreset, s.dnsCustom, s.mtu,
            s.pingMode, s.pingTimeout, s.autoPing, s.sortPing, s.tuning,
            s.cores.toList(), s.sbCores.toList(), s.activeCore, s.coreBusy, s.releases, s.sbReleases, s.autoCheck, s.checkInterval,
            Updates.state.hashCode(), Plugins.busy, Plugins.catalog, Plugins.stamp
        ).hashCode()
    }

    override fun onResume() {
        super.onResume()
        FasdeqApp.state.reloadShared()
        FasdeqApp.state.syncExternal()
        if (::bridge.isInitialized) bridge.push()
    }
}
