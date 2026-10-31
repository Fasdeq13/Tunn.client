package app.fasdeq.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.system.Os
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class FasdeqVpnService : VpnService() {

    private val lock = Any()

    @Volatile private var iface: ParcelFileDescriptor? = null
    @Volatile private var controller: CoreController? = null
    @Volatile private var coreFd = -1
    @Volatile private var pluginHandle = 0L
    @Volatile private var awgHandle = -1
    @Volatile private var stopping = false
    @Volatile private var halted = false
    @Volatile private var generation = 0
    @Volatile private var epoch = 0
    @Volatile private var serverName = ""
    @Volatile private var serverId: String? = null
    @Volatile private var connectedAt = 0L
    @Volatile private var child: java.lang.Process? = null
    @Volatile private var userStop = false
    @Volatile private var lastParams: Params? = null
    @Volatile private var retries = 0
    @Volatile private var recovering = false
    @Volatile private var connectedMono = 0L
    @Volatile private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var wake: PowerManager.WakeLock? = null

    private class Params(
        val outbound: String,
        val core: String,
        val dns: List<String>,
        val mtu: Int,
        val tuning: Tuning = Tuning(),
        val rules: String = "",
        val pluginLib: String = "",
        val pluginConf: String = "",
        val awgConf: String = "",
        val engine: String = "xray",
        val dpi: Boolean = false
    )

    private fun handler(gen: Int) = object : CoreCallbackHandler {
        override fun startup(): Long = 0

        override fun shutdown(): Long {
            if (gen == generation && !stopping) recover("Соединение прервано")
            return 0
        }

        override fun onEmitStatus(code: Long, message: String?): Long = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            userStop = true
            halt(null, true)
            return START_NOT_STICKY
        }
        var params: Params? = null
        if (intent?.action == ACTION_START) {
            val outbound = intent.getStringExtra(EXTRA_OUTBOUND)
            if (outbound != null) {
                params = Params(
                    outbound,
                    intent.getStringExtra(EXTRA_CORE) ?: "",
                    (intent.getStringExtra(EXTRA_DNS) ?: "1.1.1.1,8.8.8.8").split(',').filter { it.isNotBlank() },
                    intent.getIntExtra(EXTRA_MTU, 1500),
                    Tuning.fromJson(intent.getStringExtra(EXTRA_TUNING)),
                    intent.getStringExtra(EXTRA_RULES) ?: "",
                    intent.getStringExtra(EXTRA_PLUGIN_LIB) ?: "",
                    intent.getStringExtra(EXTRA_PLUGIN_CONF) ?: "",
                    intent.getStringExtra(EXTRA_AWG_CONF) ?: "",
                    intent.getStringExtra(EXTRA_ENGINE) ?: "xray",
                    intent.getBooleanExtra(EXTRA_DPI, false)
                )
            }
            serverName = intent.getStringExtra(EXTRA_NAME) ?: ""
            serverId = intent.getStringExtra(EXTRA_ID)
        }
        enterForeground(false)
        if (params == null) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        epoch++
        halted = false
        recovering = false
        userStop = false
        retries = 0
        lastParams = params
        start(params, false)
        return START_NOT_STICKY
    }

    private fun buildNotification(connected: Boolean): Notification {
        val channel = NotificationChannel(CHANNEL, "Состояние VPN", NotificationManager.IMPORTANCE_MIN)
        channel.setShowBadge(false)
        channel.setSound(null, null)
        channel.enableVibration(false)
        getSystemService(NotificationManager::class.java).deleteNotificationChannel("vpn_state")
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FasdeqVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val target = if (serverName.isBlank()) "серверу" else serverName
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle("Tunn")
            .setContentText(if (connected) "Вы подключены к $target" else "Подключение к $target…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_power),
                    "Отключить",
                    stop
                ).build()
            )
        if (connected && connectedAt > 0) {
            builder.setShowWhen(true).setWhen(connectedAt).setUsesChronometer(true)
        }
        return builder.build()
    }

    private fun enterForeground(connected: Boolean) {
        val notification = buildNotification(connected)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Throwable) {
        }
    }

    private fun acquireWake() {
        releaseWake()
        try {
            val pm = getSystemService(PowerManager::class.java)
            val lockObj = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tunn:vpn")
            lockObj.setReferenceCounted(false)
            lockObj.acquire()
            wake = lockObj
        } catch (e: Throwable) {
        }
    }

    private fun releaseWake() {
        try {
            wake?.let { if (it.isHeld) it.release() }
        } catch (e: Throwable) {
        }
        wake = null
    }

    private fun pidOf(p: java.lang.Process): Int = try {
        val f = p.javaClass.getDeclaredField("pid")
        f.isAccessible = true
        f.getInt(p)
    } catch (e: Throwable) {
        -1
    }

    private fun pidFile() = File(filesDir, "core.pid")

    private fun killOrphan() {
        try {
            val f = pidFile()
            if (f.exists()) {
                val pid = f.readText().trim().toIntOrNull()
                if (pid != null && pid > 1) Process.killProcess(pid)
                f.delete()
            }
        } catch (e: Throwable) {
        }
    }

    private fun killChild() {
        val p = child
        child = null
        if (p != null) {
            try {
                p.destroyForcibly()
            } catch (e: Throwable) {
            }
            val pid = pidOf(p)
            if (pid > 1) {
                try {
                    Process.killProcess(pid)
                } catch (e: Throwable) {
                }
            }
        }
        try {
            pidFile().delete()
        } catch (e: Throwable) {
        }
    }

    private fun alive(p: java.lang.Process): Boolean = try {
        p.exitValue()
        false
    } catch (e: IllegalThreadStateException) {
        true
    }

    private fun startChild(path: String, outbound: JSONObject, singbox: Boolean = false): Int {
        killOrphan()
        val port = ServerSocket(0).use { it.localPort }
        Links.resolve(outbound)
        val cfg = File(filesDir, "core_run.json")
        cfg.writeText(if (singbox) SingBox.buildConfig(outbound, port) else Links.buildInner(outbound, port))
        val label = if (singbox) "sing-box" else "Xray"
        val process = ProcessBuilder(listOf(Cores.linker(), path, "run", "-c", cfg.absolutePath))
            .redirectErrorStream(true)
            .directory(filesDir)
            .start()
        child = process
        val pid = pidOf(process)
        if (pid > 1) {
            try {
                pidFile().writeText(pid.toString())
            } catch (e: Throwable) {
            }
        }
        val tail = ArrayList<String>()
        Thread {
            try {
                process.inputStream.bufferedReader().forEachLine {
                    synchronized(tail) {
                        tail.add(it)
                        if (tail.size > 8) tail.removeAt(0)
                    }
                }
            } catch (e: Throwable) {
            }
        }.start()
        val deadline = SystemClock.elapsedRealtime() + 7000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!alive(process)) {
                val last = synchronized(tail) { tail.lastOrNull() } ?: "без вывода"
                throw IllegalStateException("Ядро $label завершилось: $last")
            }
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
                Thread {
                    try {
                        process.waitFor()
                    } catch (e: Throwable) {
                    }
                    if (child === process && !stopping && !halted) recover("Ядро $label остановилось")
                }.start()
                return port
            } catch (e: Exception) {
                Thread.sleep(150)
            }
        }
        throw IllegalStateException("Ядро $label не запустилось")
    }

    private fun start(params: Params, isRetry: Boolean) {
        Thread {
            synchronized(lock) {
                try {
                    closeCore(3000)
                    generation++
                    stopping = false
                    val gen = generation
                    if (params.awgConf.isNotEmpty()) {
                        startAwg(params)
                        return@synchronized
                    }
                    if (params.pluginLib.isNotEmpty()) {
                        startPlugin(params)
                        return@synchronized
                    }
                    val rules = parseRules(params.rules)
                    val outbound = JSONObject(params.outbound)
                    val config: String
                    if (params.dpi) {
                        val port = DpiProxy.start(this)
                        config = Links.buildConfig(Links.socksOutbound(port), params.mtu, params.tuning, rules)
                    } else if (params.engine == CorePlugins.SINGBOX) {
                        val port = startChild(if (params.core.isNotEmpty() && File(params.core).exists()) params.core else CorePlugins.singboxFile(this).absolutePath, outbound, true)
                        config = Links.buildConfig(Links.socksOutbound(port), params.mtu, params.tuning, rules)
                    } else if (params.core.isNotEmpty() && File(params.core).exists()) {
                        val port = startChild(params.core, outbound)
                        config = Links.buildConfig(Links.socksOutbound(port), params.mtu, params.tuning, rules)
                    } else {
                        config = Links.buildConfig(outbound, params.mtu, params.tuning, rules)
                    }
                    val t = params.tuning
                    val builder = Builder()
                        .setSession("Tunn")
                        .setMtu(params.mtu)
                        .addAddress("172.19.0.1", 30)
                        .addRoute("0.0.0.0", 0)
                    if (t.ipv6) {
                        builder.addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0)
                    }
                    if (Build.VERSION.SDK_INT >= 29) builder.setMetered(!t.unmetered)
                    applyApps(builder, t, rules)
                    for (dns in params.dns) {
                        try {
                            builder.addDnsServer(dns)
                        } catch (e: Exception) {
                        }
                    }
                    val tun = builder.establish()
                        ?: throw IllegalStateException("Не удалось создать VPN-интерфейс")
                    iface = tun
                    val core = Libv2ray.newCoreController(handler(gen))
                    controller = core
                    val fd = tun.dup().detachFd()
                    coreFd = fd
                    core.startLoop(config, fd)
                    connectedAt = System.currentTimeMillis()
                    connectedMono = SystemClock.elapsedRealtime()
                            registerNet()
                    acquireWake()
                    VpnBridge.write(this, true, serverId)
                    VpnBridge.send(this, true, null)
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(true))
                } catch (e: Throwable) {
                    closeCore(1500)
                    if (isRetry && !userStop && retries < MAX_RETRIES) {
                        recover(e.message ?: "Не удалось запустить")
                        return@synchronized
                    }
                    releaseWake()
                                VpnBridge.write(this, false, null)
                    VpnBridge.send(this, false, e.message ?: "Не удалось запустить")
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }.start()
    }

    private fun parseRules(raw: String): JSONObject = try {
        if (raw.isBlank()) JSONObject() else JSONObject(raw)
    } catch (e: Throwable) {
        JSONObject()
    }

    private fun strings(a: org.json.JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }

    private fun applyApps(builder: Builder, t: Tuning, rules: JSONObject) {
        val apps = t.appList()
        val only = if (t.appMode == 0) strings(rules.optJSONArray("onlyApps")) else emptyList()
        val bypass = strings(rules.optJSONArray("bypassApps"))
        if ((t.appMode == 2 && apps.isNotEmpty()) || only.isNotEmpty()) {
            for (a in (if (t.appMode == 2) apps else only)) {
                try {
                    builder.addAllowedApplication(a)
                } catch (e: Exception) {
                }
            }
        } else {
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
            }
            val list = (if (t.appMode == 1) apps else emptyList()) + bypass
            for (a in list) {
                try {
                    builder.addDisallowedApplication(a)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun cidr(text: String): Pair<String, Int>? {
        val i = text.indexOf('/')
        val host = (if (i < 0) text else text.substring(0, i)).trim()
        if (host.isEmpty()) return null
        val prefix = if (i < 0) (if (host.contains(':')) 128 else 32) else text.substring(i + 1).trim().toIntOrNull() ?: return null
        return Pair(host, prefix)
    }

    private fun startPlugin(params: Params) {
        val opened = JSONObject(Native.pluginOpen(params.pluginLib))
        if (!opened.optBoolean("ok")) throw IllegalStateException(opened.optString("error", "Не удалось загрузить плагин"))
        val handle = opened.getLong("handle")
        pluginHandle = handle
        val prep = JSONObject(Native.pluginCall(handle, "prepare", params.pluginConf, -1, 0L))
        if (!prep.optBoolean("ok")) throw IllegalStateException(prep.optString("error", "Плагин не подготовил туннель"))
        val v = prep.getJSONObject("value")
        if (v.has("error")) throw IllegalStateException(v.optString("error"))
        val t = params.tuning
        val builder = Builder().setSession("Tunn").setMtu(v.optInt("mtu", params.mtu))
        val addresses = strings(v.optJSONArray("address"))
        if (addresses.isEmpty()) builder.addAddress("172.19.0.1", 30)
        for (a in addresses) {
            val c = cidr(a) ?: continue
            try {
                builder.addAddress(c.first, c.second)
            } catch (e: Exception) {
            }
        }
        val routes = strings(v.optJSONArray("routes")).ifEmpty { if (t.ipv6) listOf("0.0.0.0/0", "::/0") else listOf("0.0.0.0/0") }
        for (r in routes) {
            val c = cidr(r) ?: continue
            try {
                builder.addRoute(c.first, c.second)
            } catch (e: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(!t.unmetered)
        applyApps(builder, t, parseRules(params.rules))
        val dns = strings(v.optJSONArray("dns")).ifEmpty { params.dns }
        for (d in dns) {
            try {
                builder.addDnsServer(d)
            } catch (e: Exception) {
            }
        }
        val tun = builder.establish() ?: throw IllegalStateException("Не удалось создать VPN-интерфейс")
        iface = tun
        val fd = tun.dup().detachFd()
        coreFd = fd
        val started = JSONObject(Native.pluginCall(handle, "start", params.pluginConf, fd, 0L))
        if (!started.optBoolean("ok")) throw IllegalStateException(started.optString("error", "Плагин не запустился"))
        connectedAt = System.currentTimeMillis()
                    connectedMono = SystemClock.elapsedRealtime()
        tunnelWatch(generation) { pluginStats(handle) }
        registerNet()
        acquireWake()
        VpnBridge.write(this, true, serverId)
        VpnBridge.send(this, true, null)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(true))
    }

    private fun startAwg(params: Params) {
        val doc = Wg.parseDoc(params.awgConf)
        if (doc.peers.isEmpty() || (doc.iface["privatekey"] ?: "").isEmpty()) throw IllegalStateException("Неверная конфигурация AmneziaWG")
        val quick = Wg.quickResolved(params.awgConf)
        val t = params.tuning
        val mtu = (doc.iface["mtu"]?.toIntOrNull() ?: 1280).coerceIn(1200, 1500)
        val builder = Builder().setSession("Tunn").setMtu(mtu)
        var added = 0
        for (a in (doc.iface["address"] ?: "").split(',')) {
            val c = cidr(a.trim()) ?: continue
            try {
                builder.addAddress(c.first, c.second)
                added++
            } catch (e: Exception) {
            }
        }
        if (added == 0) builder.addAddress("172.19.0.1", 30)
        val routes = ArrayList<String>()
        for (p in doc.peers) routes.addAll((p["allowedips"] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() })
        for (r in routes.ifEmpty { listOf("0.0.0.0/0", "::/0") }) {
            val c = cidr(r) ?: continue
            if (!t.ipv6 && c.first.contains(':')) continue
            try {
                builder.addRoute(c.first, c.second)
            } catch (e: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(!t.unmetered)
        applyApps(builder, t, parseRules(params.rules))
        val dns = (doc.iface["dns"] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { params.dns }
        for (d in dns) {
            try {
                builder.addDnsServer(d)
            } catch (e: Exception) {
            }
        }
        builder.setBlocking(true)
        val tun = builder.establish() ?: throw IllegalStateException("Не удалось создать VPN-интерфейс")
        iface = tun
        val fd = tun.dup().detachFd()
        coreFd = fd
        val handle = Awg.turnOn("fasdeq", fd, quick, dataDir.absolutePath)
        if (handle < 0) throw IllegalStateException("AmneziaWG не запустился (код $handle)")
        awgHandle = handle
        try {
            protect(Awg.socketV4(handle))
            protect(Awg.socketV6(handle))
        } catch (e: Throwable) {
        }
        connectedAt = System.currentTimeMillis()
                    connectedMono = SystemClock.elapsedRealtime()
        tunnelWatch(generation) { Awg.stats(handle) }
        registerNet()
        acquireWake()
        VpnBridge.write(this, true, serverId)
        VpnBridge.send(this, true, null)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(true))
    }

    private fun pluginStats(handle: Long): JSONObject? = try {
        val raw = JSONObject(Native.pluginCall(handle, "stats", "", -1, 0L))
        if (raw.optBoolean("ok")) JSONObject(raw.getString("value")) else null
    } catch (e: Throwable) {
        null
    }

    private fun tunnelWatch(gen: Int, stats: () -> JSONObject?) {
        Thread {
            try {
                val begin = SystemClock.elapsedRealtime()
                while (gen == generation && !stopping && !halted && !userStop) {
                    Thread.sleep(3_000L)
                    val v = stats() ?: continue
                    if (v.optLong("hs", 0L) > 0L) return@Thread
                    if (SystemClock.elapsedRealtime() - begin > 25_000L) {
                        halt("Нет рукопожатия с сервером. Проверьте ключи, порт и параметры AmneziaWG (Jc, S1, S2, H1-H4)")
                        return@Thread
                    }
                }
            } catch (e: Throwable) {
            }
        }.start()
    }

    private fun applyUnderlying(network: Network?) {
        try {
            setUnderlyingNetworks(if (network == null) null else arrayOf(network))
        } catch (e: Throwable) {
        }
    }

    private fun registerNet() {
        unregisterNet()
        val cm: ConnectivityManager? = try {
            getSystemService(ConnectivityManager::class.java)
        } catch (e: Throwable) {
            null
        }
        if (cm == null) return
        applyUnderlying(try {
            cm.activeNetwork
        } catch (e: Throwable) {
            null
        })
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                applyUnderlying(network)
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Throwable) {
        }
    }

    private fun unregisterNet() {
        val cb = netCallback
        netCallback = null
        if (cb != null) {
            try {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb)
            } catch (e: Throwable) {
            }
        }
    }

    private fun closeTun() {
        val fd = coreFd
        coreFd = -1
        if (fd >= 0) {
            try {
                val link = Os.readlink("/proc/self/fd/$fd")
                if (link.contains("tun")) ParcelFileDescriptor.adoptFd(fd).close()
            } catch (e: Throwable) {
            }
        }
        try {
            iface?.close()
        } catch (e: Throwable) {
        }
        iface = null
    }

    private fun closeCore(wait: Long) {
        DpiProxy.stop()
        stopping = true
        unregisterNet()
        val core = controller
        controller = null
        val awg = awgHandle
        awgHandle = -1
        if (awg >= 0) {
            val off = Thread {
                try {
                    Awg.turnOff(awg)
                } catch (e: Throwable) {
                }
            }
            off.start()
            off.join(wait)
        }
        val plugin = pluginHandle
        pluginHandle = 0L
        if (plugin != 0L) {
            val stopper = Thread {
                try {
                    Native.pluginCall(plugin, "stop", "", -1, 0L)
                    Native.pluginClose(plugin)
                } catch (e: Throwable) {
                }
            }
            stopper.start()
            stopper.join(wait)
        }
        if (core != null) {
            val worker = Thread {
                try {
                    core.stopLoop()
                } catch (e: Throwable) {
                }
            }
            worker.start()
            worker.join(wait)
        }
        closeTun()
        killChild()
    }

    private fun recover(message: String) {
        val params = lastParams
        if (connectedMono > 0L && SystemClock.elapsedRealtime() - connectedMono > 60_000L) retries = 0
        if (params == null || userStop || halted || retries >= MAX_RETRIES) {
            halt(message)
            return
        }
        synchronized(this) {
            if (recovering) return
            recovering = true
        }
        val mine = epoch
        val wait = RETRY_DELAYS[retries.coerceAtMost(RETRY_DELAYS.lastIndex)]
        retries++
        stopping = true
        connectedMono = 0L
        Thread {
            try {
                enterForeground(false)
            } catch (e: Throwable) {
            }
            try {
                closeCore(1500)
            } catch (e: Throwable) {
            }
            try {
                Thread.sleep(wait)
            } catch (e: Throwable) {
            }
            recovering = false
            if (mine == epoch && !userStop && !halted) {
                stopping = false
                start(params, true)
            }
        }.start()
    }

    private fun halt(message: String?, force: Boolean = false) {
        synchronized(this) {
            if (halted && !force) return
            halted = true
        }
        val mine = epoch
        stopping = true
        VpnBridge.write(this, false, null)
        VpnBridge.send(this, false, message)
        releaseWake()
        stopForegroundCompat()
        Thread {
            try {
                closeCore(1500)
            } catch (e: Throwable) {
            }
            try {
                Thread.sleep(120)
            } catch (e: Throwable) {
            }
            if (mine == epoch) {
                if (userStop) Process.killProcess(Process.myPid()) else stopSelf()
            }
        }.start()
    }

    override fun onRevoke() {
        userStop = true
        halt("VPN отключён: соединение занято другим VPN-приложением или системой")
    }

    override fun onDestroy() {
        userStop = true
        if (!halted) halt(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.fasdeq.vpn.START"
        const val ACTION_STOP = "app.fasdeq.vpn.STOP"
        const val EXTRA_OUTBOUND = "outbound"
        const val EXTRA_CORE = "core"
        const val EXTRA_DNS = "dns"
        const val EXTRA_MTU = "mtu"
        const val EXTRA_TUNING = "tuning"
        const val EXTRA_NAME = "name"
        const val EXTRA_ID = "id"
        const val EXTRA_RULES = "rules"
        const val EXTRA_PLUGIN_LIB = "plugin_lib"
        const val EXTRA_PLUGIN_CONF = "plugin_conf"
        const val EXTRA_AWG_CONF = "awg_conf"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_DPI = "dpi"
        private const val CHANNEL = "vpn_state_quiet"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RETRIES = 4
        private val RETRY_DELAYS = longArrayOf(1500L, 3000L, 6000L, 10000L)
    }
}
