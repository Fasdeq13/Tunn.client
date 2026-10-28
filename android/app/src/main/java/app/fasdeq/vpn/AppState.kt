package app.fasdeq.vpn

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

enum class Status { Off, Connecting, On, Stopping }

data class Server(
    val id: String,
    val name: String,
    val conf: String,
    val endpoint: String,
    val group: String = ""
)

data class Group(
    val name: String,
    val url: String,
    val used: Long = 0,
    val total: Long = -1,
    val expire: Long = 0
)

object DnsPresets {
    val names = listOf("Cloudflare", "Google", "Quad9", "AdGuard", "Свой")
    val servers = listOf(
        listOf("1.1.1.1", "1.0.0.1"),
        listOf("8.8.8.8", "8.8.4.4"),
        listOf("9.9.9.9", "149.112.112.112"),
        listOf("94.140.14.14", "94.140.15.15")
    )
}

object PingOptions {
    val modes = listOf("Авто", "TCP", "UDP", "Оба", "ICMP")
    val timeouts = listOf(2000, 4000, 8000)
    val timeoutNames = listOf("2 с", "4 с", "8 с")
}

class AppState(private val app: Application) {
    private val prefs = app.getSharedPreferences("fasdeq", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null
    private var checkedAt = prefs.getLong("checks_at", 0L)
    private var recheckJob: Job? = null
    private var timeoutJob: Job? = null
    private var since = 0L
    private var baseRx = 0L
    private var baseTx = 0L
    private var activeId: String? = null
    private var pendingId: String? = null

    val servers = mutableStateListOf<Server>()
    val groups = mutableStateListOf<Group>()
    val pings = mutableStateMapOf<String, PingResult>()
    val checks = mutableStateMapOf<String, ServiceCheck>()
    val cores = mutableStateListOf<CoreInfo>()
    var selectedId by mutableStateOf<String?>(prefs.getString("selected", null))
        private set
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
        private set
    var dynamicColors by mutableStateOf(prefs.getBoolean("dynamic", Build.VERSION.SDK_INT >= 31))
        private set
    var accent by mutableIntStateOf(prefs.getInt("accent", 0))
        private set
    var burnIn by mutableStateOf(prefs.getBoolean("burnin", false))
        private set
    var palette by mutableIntStateOf(prefs.getInt("palette", 0))
        private set
    var amoled by mutableStateOf(prefs.getBoolean("amoled", false))
        private set
    var pingMode by mutableIntStateOf(prefs.getInt("ping_mode", 0))
        private set
    var pingTimeout by mutableIntStateOf(prefs.getInt("ping_timeout", 1))
        private set
    var autoPing by mutableStateOf(prefs.getBoolean("auto_ping", false))
        private set
    var sortPing by mutableStateOf(prefs.getBoolean("sort_ping", false))
        private set
    var autoConnect by mutableStateOf(prefs.getBoolean("auto_connect", false))
        private set
    var dnsPreset by mutableIntStateOf(prefs.getInt("dns_preset", 0))
        private set
    var dnsCustom by mutableStateOf(prefs.getString("dns_custom", "") ?: "")
        private set
    var mtu by mutableIntStateOf(prefs.getInt("mtu", 1500))
        private set
    var bgStyle by mutableIntStateOf(prefs.getInt("bg_style", 0))
        private set
    var checkingServices by mutableStateOf(false)
        private set
    var launchStyle by mutableIntStateOf(prefs.getInt("launch_style", 0))
        private set
    var animSpeed by mutableIntStateOf(prefs.getInt("anim_speed", 2))
        private set
    var contrast by mutableIntStateOf(prefs.getInt("contrast", 0))
    var tuning by mutableStateOf(Tuning.fromJson(prefs.getString("tuning", null)))
        private set
    var anim by mutableStateOf(AnimConfig.fromJson(prefs.getString("anim_cfg", null)))
        private set
    var resetTick by mutableIntStateOf(0)
        private set
    var autoCheck by mutableStateOf(prefs.getBoolean("auto_check", false))
    var checkInterval by mutableFloatStateOf(prefs.getFloat("check_interval", 60f))
    var powerScale by mutableFloatStateOf(prefs.getFloat("power_scale", 1f))
    var textScale by mutableFloatStateOf(prefs.getFloat("text_scale", 1f))
        private set
    var activeCore by mutableStateOf(Cores.active(app))
        private set
    var builtinVersion by mutableStateOf("")
        private set
    var coreBusy by mutableStateOf<String?>(null)
        private set
    var releases by mutableStateOf<List<Release>?>(null)
        private set
    var status by mutableStateOf(Status.Off)
        private set
    var error by mutableStateOf<String?>(null)
    var rx by mutableLongStateOf(0L)
        private set
    var tx by mutableLongStateOf(0L)
        private set
    var downSpeed by mutableLongStateOf(0L)
        private set
    var upSpeed by mutableLongStateOf(0L)
        private set
    var uptime by mutableLongStateOf(0L)
        private set

    val selected: Server?
        get() = servers.firstOrNull { it.id == selectedId }

    val activeServerId: String?
        get() = if (status == Status.On) activeId else null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onCoreState(intent.getBooleanExtra("up", false), intent.getStringExtra("msg"))
        }
    }

    init {
        servers.addAll(load())
        groups.addAll(loadGroups())
        loadChecks()
        if (servers.none { it.id == selectedId }) selectedId = servers.firstOrNull()?.id
        if (SharedStore.read(app) == null) save() else reloadShared()
        refreshCores()
        scope.launch { builtinVersion = withContext(Dispatchers.IO) { Cores.builtinVersion() } }
        if (autoPing) {
            scope.launch {
                delay(500)
                pingAll()
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(VpnBridge.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        syncDpiServer()
        syncExternal()
        scope.launch {
            while (true) {
                delay(1500)
                syncExternal()
            }
        }
    }

    @Volatile private var stopGuardUntil = 0L

    fun reloadShared() {
        val o = SharedStore.read(app) ?: return
        try {
            val arr = o.optJSONArray("servers") ?: JSONArray()
            val list = (0 until arr.length()).map {
                val x = arr.getJSONObject(it)
                Server(
                    x.getString("id"),
                    x.getString("name"),
                    x.getString("conf"),
                    x.optString("endpoint"),
                    x.optString("group")
                )
            }
            val garr = o.optJSONArray("groups") ?: JSONArray()
            val glist = (0 until garr.length()).map {
                val x = garr.getJSONObject(it)
                Group(x.getString("name"), x.optString("url"), x.optLong("used", 0), x.optLong("total", -1), x.optLong("expire", 0))
            }
            servers.clear()
            servers.addAll(list)
            groups.clear()
            groups.addAll(glist)
            val sel = if (o.isNull("selected")) null else o.optString("selected", "").ifEmpty { null }
            selectedId = if (servers.any { it.id == sel }) sel else servers.firstOrNull()?.id
        } catch (e: Exception) {
        }
    }

    fun syncExternal() {
        val saved = VpnBridge.read(app)
        val live = saved.first && VpnBridge.live(app)
        if (live && status == Status.Off && SystemClock.elapsedRealtime() > stopGuardUntil) {
            pendingId = saved.second ?: selectedId
            goUp()
        } else if (!live && status == Status.On) {
            finishDown(null)
        }
    }

    fun changeBgStyle(value: Int) {
        bgStyle = value
        prefs.edit().putInt("bg_style", value).apply()
    }

    fun bestFor(index: Int): String? {
        var bestId: String? = null
        var best = Int.MAX_VALUE
        for ((id, check) in checks) {
            val v = check.ms.getOrNull(index)
            if (v != null && v > 0 && v < best) {
                best = v
                bestId = id
            }
        }
        return bestId
    }

    private fun loadChecks() {
        val raw = prefs.getString("checks", null) ?: return
        try {
            val obj = JSONObject(raw)
            val ids = servers.map { it.id }.toSet()
            for (id in obj.keys()) {
                if (id !in ids) continue
                val arr = obj.getJSONArray(id)
                checks[id] = ServiceCheck(
                    ms = (0 until Services.names.size).map { i ->
                        val v = arr.optInt(i, 0)
                        if (v == 0) null else v
                    }
                )
            }
        } catch (e: Exception) {
        }
    }

    private fun saveChecks() {
        val obj = JSONObject()
        synchronized(checks) {
            for ((id, check) in checks) {
                val arr = JSONArray()
                for (i in 0 until Services.names.size) arr.put(check.ms.getOrNull(i) ?: 0)
                obj.put(id, arr)
            }
        }
        prefs.edit().putString("checks", obj.toString()).putLong("checks_at", checkedAt).apply()
    }

    private fun startRecheck() {
        recheckJob?.cancel()
        recheckJob = scope.launch {
            while (isActive && status == Status.On) {
                if (!autoCheck) {
                    delay(2_000L)
                    continue
                }
                val due = checkedAt + (checkInterval * 60_000f).toLong()
                val wait = due - System.currentTimeMillis()
                if (wait <= 0L) {
                    if (!checkingServices && servers.isNotEmpty()) checkServices(listOfNotNull(servers.firstOrNull { it.id == selectedId }))
                    delay(2_000L)
                } else {
                    delay(wait.coerceIn(500L, 5_000L))
                }
            }
        }
    }

    private fun probeConfigFor(server: Server): String? {
        if (server.conf == CorePlugins.DPI_CONF) return null
        val plugin = try {
            Plugins.forConf(app, server.conf)
        } catch (e: Throwable) {
            null
        }
        if (plugin != null) return null
        if (Plugins.usesAwg(app, server.conf)) return null
        return try {
            val ob = Links.parse(Plugins.effectiveConf(app, server.conf)).outbound
            if (ob.optString("protocol") == "tuic") return null
            Services.probeConfig(ob)
        } catch (e: Exception) {
            null
        }
    }

    private val probeLock = Any()

    fun checkServices(only: List<Server>? = null) {
        if (checkingServices || servers.isEmpty()) return
        val list = only ?: servers.toList()
        if (list.isEmpty()) return
        checkingServices = true
        list.forEach { checks[it.id] = (checks[it.id] ?: ServiceCheck()).copy(busy = true) }
        val unsupported = AtomicBoolean(false)
        scope.launch {
            val gate = Semaphore(8)
            val saver = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(3_000L)
                    saveChecks()
                }
            }
            try {
                list.map { server ->
                    launch(Dispatchers.IO) {
                        gate.withPermit {
                            val config = synchronized(probeLock) { probeConfigFor(server) }
                            var firstFailed = false
                            val last = Services.names.size - 1
                            for (index in 0..last) {
                                if (unsupported.get()) break
                                val value = if (config == null) Services.SKIPPED else Services.check(config, index, if (firstFailed) 1 else 2)
                                if (value == Services.UNSUPPORTED) unsupported.set(true)
                                val stored = if (value > 0 || value == Services.PARTIAL || value == Services.SKIPPED) value else -1
                                if (index == 0 && stored == -1) firstFailed = true
                                synchronized(checks) {
                                    val current = checks[server.id] ?: ServiceCheck(busy = true)
                                    checks[server.id] = current.with(index, stored).copy(busy = index < last)
                                }
                            }
                            synchronized(checks) {
                                val current = checks[server.id]
                                if (current != null && current.busy) checks[server.id] = current.copy(busy = false)
                            }
                        }
                    }
                }.joinAll()
            } finally {
                saver.cancel()
                checkingServices = false
                list.forEach {
                    synchronized(checks) {
                        val current = checks[it.id]
                        if (current != null && current.busy) checks[it.id] = current.copy(busy = false)
                    }
                }
                if (unsupported.get()) {
                    checks.clear()
                    error = "Встроенное ядро не умеет проверять сервисы"
                } else {
                    checkedAt = System.currentTimeMillis()
                }
                saveChecks()
            }
        }
    }

    fun changeLaunchStyle(value: Int) {
        launchStyle = value
        prefs.edit().putInt("launch_style", value).apply()
    }

    fun changeTuning(value: Tuning) {
        tuning = value
        prefs.edit().putString("tuning", value.toJson()).apply()
    }

    fun changeAnim(value: AnimConfig) {
        anim = value
        prefs.edit().putString("anim_cfg", value.toJson()).apply()
    }

    fun changeAnimSpeed(value: Int) {
        animSpeed = value
        prefs.edit().putInt("anim_speed", value).apply()
    }

    fun resetSettings() {
        setTheme(0)
        setDynamic(Build.VERSION.SDK_INT >= 31)
        applyAccent(0)
        changePalette(0)
        changeAmoled(false)
        changeBurnIn(false)
        changeContrast(0)
        changeBgStyle(0)
        changeLaunchStyle(0)
        changeAnimSpeed(2)
        changeAnim(AnimConfig())
        changeTuning(Tuning())
        changePowerScale(1f)
        changeTextScale(1f)
        changePingMode(0)
        changePingTimeout(1)
        changeAutoPing(false)
        changeSortPing(false)
        changeAutoConnect(false)
        changeDnsPreset(0)
        changeDnsCustom("")
        changeMtu(1500)
        changeAutoCheck(true)
        changeCheckInterval(60f)
        resetTick++
    }

    fun changeAutoCheck(value: Boolean) {
        autoCheck = value
        prefs.edit().putBoolean("auto_check", value).apply()
    }

    fun changeCheckInterval(value: Float) {
        checkInterval = value
        prefs.edit().putFloat("check_interval", value).apply()
    }

    fun changePowerScale(value: Float) {
        powerScale = value
        prefs.edit().putFloat("power_scale", value).apply()
    }

    fun changeTextScale(value: Float) {
        textScale = value
        prefs.edit().putFloat("text_scale", value).apply()
    }

    fun changeContrast(value: Int) {
        contrast = value
        prefs.edit().putInt("contrast", value).apply()
    }

    fun changePingMode(value: Int) {
        pingMode = value
        prefs.edit().putInt("ping_mode", value).apply()
    }

    fun changePingTimeout(value: Int) {
        pingTimeout = value
        prefs.edit().putInt("ping_timeout", value).apply()
    }

    fun changeAutoPing(value: Boolean) {
        autoPing = value
        prefs.edit().putBoolean("auto_ping", value).apply()
    }

    fun changeSortPing(value: Boolean) {
        sortPing = value
        prefs.edit().putBoolean("sort_ping", value).apply()
    }

    fun changeAutoConnect(value: Boolean) {
        autoConnect = value
        prefs.edit().putBoolean("auto_connect", value).apply()
    }

    fun changeDnsPreset(value: Int) {
        dnsPreset = value
        prefs.edit().putInt("dns_preset", value).apply()
    }

    fun changeDnsCustom(value: String) {
        dnsCustom = value
        prefs.edit().putString("dns_custom", value).apply()
    }

    fun changeMtu(value: Int) {
        mtu = value
        prefs.edit().putInt("mtu", value).apply()
    }

    fun dnsList(): List<String> {
        if (dnsPreset in DnsPresets.servers.indices) return DnsPresets.servers[dnsPreset]
        val custom = dnsCustom.split(',', ' ', ';', '\n')
            .map { it.trim() }
            .filter { it.matches(Regex("[0-9a-fA-F:.]+")) && (it.contains('.') || it.contains(':')) }
        return custom.ifEmpty { DnsPresets.servers[0] }
    }

    fun changePalette(value: Int) {
        palette = value
        prefs.edit().putInt("palette", value).apply()
    }

    fun changeAmoled(value: Boolean) {
        amoled = value
        prefs.edit().putBoolean("amoled", value).apply()
    }

    fun setTheme(value: Int) {
        themeMode = value
        prefs.edit().putInt("theme", value).apply()
    }

    fun setDynamic(value: Boolean) {
        dynamicColors = value
        prefs.edit().putBoolean("dynamic", value).apply()
    }

    fun changeBurnIn(value: Boolean) {
        burnIn = value
        prefs.edit().putBoolean("burnin", value).apply()
    }

    private fun update(value: Status) {
        status = value
        try {
            TileService.requestListeningState(app, ComponentName(app, FasdeqTileService::class.java))
        } catch (e: Throwable) {
        }
    }

    fun applyAccent(value: Int) {
        accent = value
        prefs.edit().putInt("accent", value).apply()
    }

    private fun ensureGroup(name: String, url: String) {
        val index = groups.indexOfFirst { it.name == name }
        if (index < 0) {
            groups.add(Group(name, url))
        } else if (url.isNotBlank() && groups[index].url != url) {
            groups[index] = groups[index].copy(url = url)
        }
    }

    private val importLock = kotlinx.coroutines.sync.Mutex()

    suspend fun importText(
        text: String,
        nameHint: String = "",
        groupHint: String = "",
        forceGroup: String? = null,
        forceUrl: String = ""
    ): String? = importLock.withLock {
        withContext(Dispatchers.Default) { importTextImpl(text, nameHint, groupHint, forceGroup, forceUrl) }
    }

    private suspend fun importTextImpl(
        text: String,
        nameHint: String,
        groupHint: String,
        forceGroup: String?,
        forceUrl: String
    ): String? {
        val input = text.trim()
        if (input.isEmpty()) return "Вставьте ссылку или конфигурацию"
        class Item(val link: String, val group: String, val url: String, val info: SubInfo?)
        val items = ArrayList<Item>()
        val direct = ArrayList<String>()
        val prepared = Importer.prepare(input)
        if (prepared.error != null) return prepared.error
        for (source in prepared.items) {
            if (source.isUrl) {
                val fetched = try {
                    withContext(Dispatchers.IO) { Links.fetchMeta(source.value) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return "Не удалось загрузить список: " + (e.message ?: "ошибка сети")
                }
                val groupName = forceGroup ?: source.name.ifBlank { fetched.group }
                for (link in Importer.prepare(Links.decodeSubscription(fetched.body)).items) {
                    if (link.isUrl) continue
                    items.add(Item(link.value, groupName, forceUrl.ifBlank { source.value }, fetched.info))
                }
            } else {
                direct.add(source.value)
            }
        }
        val directGroup = forceGroup ?: if (direct.size > 1) groupHint else ""
        for (link in direct) items.add(Item(link, directGroup, forceUrl, null))
        var added = 0
        var failure: String? = null
        val fresh = ArrayList<Server>()
        val known = HashMap<String, Int>()
        val byName = HashMap<String, Int>()
        servers.forEachIndexed { i, s ->
            known.putIfAbsent(Links.identity(s.conf), i)
            byName.putIfAbsent(s.group + "|" + s.name + "|" + s.endpoint, i)
        }
        for (item in items) {
            try {
                val parsed = Plugins.describe(app, item.link) ?: Links.parse(item.link)
                val key = Links.identity(item.link)
                var existing = known[key] ?: -1
                if (existing < 0 && item.group.isNotBlank() && parsed.name.isNotBlank()) {
                    existing = byName[item.group.trim() + "|" + parsed.name + "|" + parsed.endpoint] ?: -1
                }
                if (existing < 0) {
                    val hint = if (items.size == 1) nameHint else ""
                    val title = hint.ifBlank { parsed.name }.ifBlank { "Сервер ${servers.size + 1}" }
                    val id = "fq" + Integer.toHexString(Random.nextInt(0x100000, 0xFFFFFF))
                    val server = Server(id, title, item.link, parsed.endpoint, item.group.trim())
                    servers.add(server)
                    known[key] = servers.size - 1
                    byName.putIfAbsent(server.group + "|" + server.name + "|" + server.endpoint, servers.size - 1)
                    fresh.add(server)
                    if (selectedId == null) selectedId = id
                } else {
                    var current = servers[existing]
                    if (current.conf != item.link) current = current.copy(conf = item.link, endpoint = parsed.endpoint)
                    if (item.group.isNotBlank() && current.group != item.group.trim()) current = current.copy(group = item.group.trim())
                    if (current != servers[existing]) servers[existing] = current
                }
                if (item.group.isNotBlank()) {
                    ensureGroup(item.group.trim(), item.url)
                    item.info?.let { applyInfo(item.group.trim(), it) }
                }
                added++
            } catch (e: Exception) {
                if (failure == null) failure = e.message ?: "Не удалось распознать"
            }
        }
        save()
        if (added > 0 && autoPing && fresh.isNotEmpty()) ping(fresh)
        return if (added == 0) (failure ?: "Не удалось распознать") else null
    }

    private fun applyInfo(name: String, info: SubInfo) {
        val i = groups.indexOfFirst { it.name == name }
        if (i >= 0) groups[i] = groups[i].copy(used = info.used, total = info.total, expire = info.expire)
    }

    suspend fun refreshInfo() {
        for (g in groups.toList()) {
            if (g.url.isBlank()) continue
            try {
                val f = withContext(Dispatchers.IO) { Links.fetchMeta(g.url) }
                f.info?.let { applyInfo(g.name, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        save()
    }

    fun moveServer(id: String, group: String, beforeId: String?) {
        val from = servers.indexOfFirst { it.id == id }
        if (from < 0) return
        val g = group.trim()
        if (g.isNotEmpty() && groups.none { it.name == g }) return
        var s = servers.removeAt(from)
        if (s.group != g) s = s.copy(group = g)
        var at = if (beforeId != null) servers.indexOfFirst { it.id == beforeId } else -1
        if (at < 0) {
            val last = servers.indexOfLast { it.group == g }
            at = if (last >= 0) last + 1 else servers.size
        }
        servers.add(at, s)
        save()
    }

    suspend fun refreshGroup(name: String): String? {
        val group = groups.firstOrNull { it.name == name } ?: return "Группа не найдена"
        if (group.url.isBlank()) return "У группы нет ссылки для обновления"
        val result = importText(group.url, forceGroup = group.name, forceUrl = group.url)
        if (result == null) dedupeGroup(group.name)
        return result
    }

    private fun dedupeGroup(name: String) {
        val byKey = HashMap<String, String>()
        val byTitle = HashMap<String, String>()
        val drop = HashSet<String>()
        for (s in servers.filter { it.group == name }) {
            val key = Links.identity(s.conf)
            val title = s.name + "|" + s.endpoint
            val first = byKey[key] ?: byTitle[title]
            if (first == null) {
                byKey[key] = s.id
                byTitle[title] = s.id
            } else if (s.id == selectedId || s.id == activeId) {
                drop.add(first)
                byKey[key] = s.id
                byTitle[title] = s.id
            } else {
                drop.add(s.id)
            }
        }
        if (drop.isNotEmpty()) removeMany(drop)
    }

    fun remove(server: Server) {
        removeMany(setOf(server.id))
    }

    fun removeMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        if (activeId != null && activeId in ids && (status == Status.On || status == Status.Connecting)) disconnect()
        servers.removeAll { it.id in ids }
        ids.forEach {
            pings.remove(it)
            checks.remove(it)
        }
        saveChecks()
        if (selectedId in ids) selectedId = servers.firstOrNull()?.id
        save()
    }

    fun renameServer(id: String, name: String) {
        val title = name.trim()
        if (title.isEmpty()) return
        val index = servers.indexOfFirst { it.id == id }
        if (index < 0) return
        servers[index] = servers[index].copy(name = title)
        save()
    }

    fun renameGroup(old: String, name: String): String? {
        val title = name.trim()
        if (title.isEmpty()) return "Название не может быть пустым"
        if (title == old) return null
        if (groups.any { it.name == title }) return "Группа с таким названием уже есть"
        val index = groups.indexOfFirst { it.name == old }
        if (index < 0) return "Группа не найдена"
        groups[index] = groups[index].copy(name = title)
        for (i in servers.indices) {
            if (servers[i].group == old) servers[i] = servers[i].copy(group = title)
        }
        save()
        return null
    }

    fun clearGroup(name: String) {
        removeMany(servers.filter { it.group == name }.map { it.id }.toSet())
    }

    fun removeGroup(name: String) {
        clearGroup(name)
        groups.removeAll { it.name == name }
        save()
    }

    fun ping(all: List<Server>): Job {
        val list = all.filter { it.conf != CorePlugins.DPI_CONF }
        if (list.isEmpty()) return Job().also { it.complete() }
        val mode = pingMode
        val timeout = PingOptions.timeouts[pingTimeout.coerceIn(0, PingOptions.timeouts.size - 1)]
        list.forEach { pings[it.id] = PingResult(busy = true) }
        return scope.launch {
            val gate = Semaphore(16)
            list.map { server ->
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        val result = try {
                            Ping.measure(server, mode, timeout)
                        } catch (e: Exception) {
                            PingResult(tcp = Ping.FAIL)
                        }
                        pings[server.id] = result
                    }
                }
            }.joinAll()
        }
    }

    fun pingAll() = ping(servers.toList())

    fun pingGroup(name: String) = ping(servers.filter { it.group == name })

    fun refreshCores() {
        cores.clear()
        cores.addAll(Cores.list(app))
        activeCore = Cores.active(app)
        if (activeCore.isNotEmpty() && cores.none { it.id == activeCore }) {
            Cores.setActive(app, "")
            activeCore = ""
        }
    }

    fun selectCore(id: String) {
        Cores.setActive(app, id)
        activeCore = id
    }

    fun deleteCore(id: String) {
        Cores.delete(app, id)
        refreshCores()
    }

    suspend fun importCore(uri: Uri, label: String): String? {
        coreBusy = "Проверяю ядро…"
        return try {
            withContext(Dispatchers.IO) {
                val stream = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Не удалось открыть файл")
                stream.use { Cores.install(app, it, label) }
            }
            refreshCores()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Не удалось импортировать ядро"
        } finally {
            coreBusy = null
        }
    }

    suspend fun loadReleases(): String? {
        coreBusy = "Загружаю список версий…"
        return try {
            releases = withContext(Dispatchers.IO) { Cores.releases() }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Не удалось получить список версий"
        } finally {
            coreBusy = null
        }
    }

    suspend fun downloadCore(release: Release): String? {
        coreBusy = "Скачиваю Xray ${release.tag}…"
        return try {
            val info = withContext(Dispatchers.IO) {
                val bytes = Cores.download(release.url)
                Cores.install(app, ByteArrayInputStream(bytes), "Xray ${release.tag}")
            }
            refreshCores()
            selectCore(info.id)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Не удалось скачать ядро"
        } finally {
            coreBusy = null
        }
    }

    fun select(id: String) {
        if (selectedId == id) return
        selectedId = id
        save()
        val target = servers.firstOrNull { it.id == id } ?: return
        if (status == Status.On) launchSwitch(target)
    }

    fun connect() {
        val target = selected
        if (target == null) {
            error = "Сначала добавьте сервер"
            return
        }
        if (status != Status.Off) return
        launchSwitch(target)
    }

    fun disconnect() {
        if (status != Status.On && status != Status.Connecting) return
        update(Status.Stopping)
        pollJob?.cancel()
        stopGuardUntil = SystemClock.elapsedRealtime() + 8000
        sendStop()
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(1200)
            if (status == Status.Stopping) sendStop()
            delay(1500)
            if (status == Status.Stopping) {
                try {
                    app.stopService(Intent(app, FasdeqVpnService::class.java))
                } catch (e: Throwable) {
                }
                finishDown(null)
            }
        }
    }

    private fun sendStop() {
        try {
            PendingIntent.getService(
                app,
                2,
                Intent(app, FasdeqVpnService::class.java).setAction(FasdeqVpnService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ).send()
        } catch (e: Throwable) {
            app.stopService(Intent(app, FasdeqVpnService::class.java))
        }
    }

    fun onCoreState(up: Boolean, message: String?) {
        scope.launch {
            if (up) {
                if (status == Status.Connecting || status == Status.Off) {
                    timeoutJob?.cancel()
                    goUp()
                }
            } else {
                timeoutJob?.cancel()
                finishDown(message)
            }
        }
    }

    private fun goUp() {
        activeId = pendingId ?: selectedId
        since = SystemClock.elapsedRealtime()
        val uid = Process.myUid()
        baseRx = TrafficStats.getUidRxBytes(uid)
        baseTx = TrafficStats.getUidTxBytes(uid)
        rx = 0
        tx = 0
        downSpeed = 0
        upSpeed = 0
        uptime = 0
        update(Status.On)
        startPolling()
        startRecheck()
    }

    private fun finishDown(message: String?) {
        pollJob?.cancel()
        recheckJob?.cancel()
        activeId = null
        downSpeed = 0
        upSpeed = 0
        rx = 0
        tx = 0
        uptime = 0
        update(Status.Off)
        if (message != null) error = message
    }

    fun syncDpiServer() {
        val want = CorePlugins.enabled(app, CorePlugins.DPI) && CorePlugins.present(app, CorePlugins.DPI)
        val index = servers.indexOfFirst { it.id == CorePlugins.DPI_ID }
        if (want && index < 0) {
            servers.add(0, Server(CorePlugins.DPI_ID, CorePlugins.DPI_NAME, CorePlugins.DPI_CONF, "DPI", ""))
            if (selectedId == null) selectedId = CorePlugins.DPI_ID
            save()
        } else if (!want && index >= 0) {
            removeMany(setOf(CorePlugins.DPI_ID))
        }
    }

    suspend fun restoreXray(): String? = withContext(Dispatchers.IO) {
        try {
            val release = Cores.releases().firstOrNull() ?: return@withContext "Не удалось получить список версий Xray"
            val bytes = Cores.download(release.url)
            val info = Cores.install(app, java.io.ByteArrayInputStream(bytes), release.tag)
            Cores.setActive(app, info.id)
            CorePlugins.restore(app, CorePlugins.XRAY)
            null
        } catch (e: Exception) {
            e.message ?: "Не удалось скачать Xray"
        }
    }

    private fun launchSwitch(target: Server) {
        val dpi = target.conf == CorePlugins.DPI_CONF
        val plugin = if (dpi) null else try {
            Plugins.forConf(app, target.conf)
        } catch (e: Throwable) {
            null
        }
        val awg = !dpi && plugin == null && Plugins.usesAwg(app, target.conf)
        var engine = CorePlugins.XRAY
        val outbound = if (dpi || plugin != null || awg) "{}" else try {
            val parsed = Links.parse(Plugins.effectiveConf(app, target.conf)).outbound
            engine = CorePlugins.pick(app, parsed) ?: run {
                error = if (parsed.optString("protocol") == "tuic") "Для TUIC включите плагин TUIC (нужно ядро sing-box)" else "Ядро не найдено. Включите ядро в Настройки, Плагины"
                return
            }
            parsed.toString()
        } catch (e: Exception) {
            error = e.message ?: "Неверная конфигурация"
            return
        }
        error = null
        pollJob?.cancel()
        pendingId = target.id
        update(Status.Connecting)
        val intent = Intent(app, FasdeqVpnService::class.java)
            .setAction(FasdeqVpnService.ACTION_START)
            .putExtra(FasdeqVpnService.EXTRA_OUTBOUND, outbound)
            .putExtra(FasdeqVpnService.EXTRA_CORE, Cores.activePath(app) ?: "")
            .putExtra(FasdeqVpnService.EXTRA_DNS, dnsList().joinToString(","))
            .putExtra(FasdeqVpnService.EXTRA_MTU, mtu)
            .putExtra(FasdeqVpnService.EXTRA_TUNING, tuning.toJson())
            .putExtra(FasdeqVpnService.EXTRA_NAME, target.name)
            .putExtra(FasdeqVpnService.EXTRA_ID, target.id)
            .putExtra(FasdeqVpnService.EXTRA_RULES, Plugins.rules(app).toString())
            .putExtra(FasdeqVpnService.EXTRA_ENGINE, engine)
            .putExtra(FasdeqVpnService.EXTRA_DPI, dpi)
        if (awg) {
            val conf = try {
                Plugins.awgConf(app, Plugins.effectiveConf(app, target.conf))
            } catch (e: Exception) {
                error = e.message ?: "Неверная конфигурация"
                return
            }
            intent.putExtra(FasdeqVpnService.EXTRA_AWG_CONF, conf)
        }
        if (plugin != null) {
            intent.putExtra(FasdeqVpnService.EXTRA_PLUGIN_LIB, plugin.lib?.absolutePath ?: "")
            intent.putExtra(FasdeqVpnService.EXTRA_PLUGIN_CONF, Plugins.pluginConfig(app, plugin, target.conf))
        }
        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (e: Exception) {
            update(Status.Off)
            error = e.message ?: "Не удалось запустить службу"
            return
        }
        if (dpi && Dpi.chosen(app) == null && !DpiSearch.isRunning()) DpiService.start(app)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(20000)
            if (status == Status.Connecting && pendingId == target.id) {
                sendStop()
                finishDown("Не удалось подключиться: истекло время ожидания")
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        val uid = Process.myUid()
        pollJob = scope.launch {
            var lastRx = 0L
            var lastTx = 0L
            while (isActive && status == Status.On) {
                val r = (TrafficStats.getUidRxBytes(uid) - baseRx).coerceAtLeast(0L)
                val t = (TrafficStats.getUidTxBytes(uid) - baseTx).coerceAtLeast(0L)
                downSpeed = (r - lastRx).coerceAtLeast(0L)
                upSpeed = (t - lastTx).coerceAtLeast(0L)
                lastRx = r
                lastTx = t
                rx = r
                tx = t
                uptime = (SystemClock.elapsedRealtime() - since) / 1000
                delay(1000)
            }
        }
    }

    private fun load(): List<Server> {
        val raw = prefs.getString("servers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Server(
                    o.getString("id"),
                    o.getString("name"),
                    o.getString("conf"),
                    o.optString("endpoint"),
                    o.optString("group")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadGroups(): List<Group> {
        val raw = prefs.getString("groups", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Group(o.getString("name"), o.optString("url"), o.optLong("used", 0), o.optLong("total", -1), o.optLong("expire", 0))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save() {
        val arr = JSONArray()
        servers.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("conf", it.conf)
                    .put("endpoint", it.endpoint)
                    .put("group", it.group)
            )
        }
        val garr = JSONArray()
        groups.forEach { garr.put(JSONObject().put("name", it.name).put("url", it.url).put("used", it.used).put("total", it.total).put("expire", it.expire)) }
        prefs.edit()
            .putString("servers", arr.toString())
            .putString("groups", garr.toString())
            .putString("selected", selectedId)
            .apply()
        SharedStore.write(app, arr, garr, selectedId)
    }
}
