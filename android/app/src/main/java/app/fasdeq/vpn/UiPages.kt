@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.fasdeq.vpn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private val accents = listOf(
    "#4FD8C8", "#26A69A", "#00BCD4", "#29B6F6", "#42A5F5", "#5C6BC0",
    "#7E57C2", "#AB47BC", "#EC407A", "#EF5350", "#FF7043", "#FFA726",
    "#FFCA28", "#D4E157", "#9CCC65", "#66BB6A", "#8D6E63", "#78909C",
    "#FF8A80", "#FF80AB", "#EA80FC", "#B388FF", "#82B1FF", "#80D8FF",
    "#A7FFEB", "#CCFF90", "#FFE57F", "#FFFFFF"
)

private val backgrounds = listOf(
    "#000000", "#0B0F10", "#101414", "#12141C", "#1A1423", "#1C1410",
    "#0E1A14", "#14212B", "#F5FAFA", "#FFF8F0", "#F3F0FF", "#FFFFFF"
)

@Composable
fun Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = parseHex(hex) ?: Color.Gray
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c).border(if (selected) 3.dp else 1.dp, ring, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = if (toHsl(c).l > 0.55f) Color.Black else Color.White)
        }
    }
}

@Composable
fun SwatchGrid(list: List<String>, current: String, onPick: (String) -> Unit) {
    val cur = parseHex(current)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in list.chunked(6)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (h in row) Swatch(h, cur != null && parseHex(h) == cur) { onPick(h) }
                for (i in row.size until 6) Spacer(Modifier.size(44.dp))
            }
        }
    }
}

@Composable
fun HexField(current: String, onValid: (String) -> Unit) {
    val text = remember(current) { mutableStateOf(current) }
    val ok = parseHex(text.value) != null || text.value.isEmpty()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val c = parseHex(text.value)
        Box(Modifier.size(44.dp).clip(CircleShape).background(c ?: MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = text.value,
            onValueChange = {
                text.value = it.take(7)
                val p = parseHex(it)
                if (p != null && it.removePrefix("#").length >= 6) onValid(toHex(p))
            },
            label = { Text("HEX, например #4FD8C8") },
            singleLine = true,
            isError = !ok,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LookPage(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, onBack: () -> Unit) {
    SubPage("Оформление", onBack) {
        Section("Стиль") {
            SwitchRow("Liquid Glass", "Стекло как в iOS 26: размытие, преломление на кромках, блики. На Android 13+ настоящая линза на шейдере, на старых упрощённо. Цвета акцента не меняются", s.glass) { a.set("glass", it) }
        }
        if (s.glass) {
            Section("Стекло") {
                ChoiceRow("Тема", listOf("Система", "Светлая", "Тёмная"), s.theme) { a.set("theme", it) }
                SliderRow("Степень стекла", s.glassLevel.toFloat(), 0f, 100f, if (s.glassLevel < 35) "матовое" else if (s.glassLevel < 70) "среднее" else "прозрачное") { a.set("glassLevel", it.toInt()) }
            }
            Section("Фон") {
                BaseRow("Выбрать картинку", "Фото под стеклом. Сейчас: " + (if (s.glassBg != 0L) "своя картинка" else "стандартный фон"), onClick = {
                    a.call("pickGlassBg") { r ->
                        val e = r.optString("error")
                        if (e.isNotEmpty()) a.say(e)
                    }
                })
                if (s.glassBg != 0L) BaseRow("Вернуть стандартный фон", null, onClick = { a.call("clearGlassBg") })
            }
        }
        if (!s.glass) Section("Тема") {
            ChoiceRow("Режим", listOf("Система", "Светлая", "Тёмная"), s.theme, enabled = !s.bgOn) { a.set("theme", it) }
            ChoiceRow("Цвета", listOf("Системные", "Свои"), if (s.dynamic) 0 else 1, enabled = !s.bgOn) { a.set("dynamic", it == 0) }
            SwitchRow("Чёрный фон", "Для OLED-экранов в тёмной теме", s.amoled, enabled = !s.bgOn) { a.set("amoled", it) }
        }
        if (!s.glass) Section("Цвет акцента") {
            Dim(!s.dynamic || s.bgOn) {
                Column {
                    SwatchGrid(accents, s.accentHex) {
                        a.set("accentHex", it)
                        a.set("dynamic", false)
                    }
                    HexField(s.accentHex) {
                        a.set("accentHex", it)
                        a.set("dynamic", false)
                    }
                }
            }
        }
        if (!s.glass) Section("Фон") {
            SwitchRow("Свой цвет фона", "Пока включён, тема, системные цвета и чёрный фон неактивны, но их значения сохраняются", s.bgOn) { a.set("bgCustom", it) }
            if (s.bgOn) {
                SwatchGrid(backgrounds, s.bgHex) { a.set("bgHex", it) }
                HexField(s.bgHex) { a.set("bgHex", it) }
            }
        }
        Section("Кнопка питания") {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                PowerButton(s.status, 120.dp, s.btnShape, s.btnLobes, s.btnAmp, s.btnSpin) {}
            }
            ChoiceRow("Форма", listOf("Печенька", "Круг", "Квадрат", "Цветок"), s.btnShape) { a.set("btnShape", it) }
            SliderRow("Лепестки", s.btnLobes.toFloat(), 3f, 16f, "${s.btnLobes}", enabled = s.btnShape == 0 || s.btnShape == 3) { a.set("btnLobes", it.toInt()) }
            SliderRow("Выраженность", s.btnAmp, 0f, 0.14f, String.format("%.0f%%", s.btnAmp * 700f), enabled = s.btnShape == 0 || s.btnShape == 3) { a.set("btnAmp", it.toDouble()) }
            SliderRow("Размер", s.btnSize.toFloat(), 140f, 260f, "${s.btnSize} dp") { a.set("btnSize", it.toInt()) }
            SwitchRow("Вращение", "Кнопка медленно вращается", s.btnSpin) { a.set("btnSpin", it) }
        }
        Section("Значок") {
            BaseRow("Иконка приложения", "Варианты и своя картинка", { open(Sheet.IconPick) })
        }
    }
}

@Composable
fun ConnectPage(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, onBack: () -> Unit) {
    SubPage("Подключение", onBack) {
        Section("Основное") {
            SwitchRow("Подключаться при запуске", null, s.autoConnect) { a.set("autoConnect", it) }
            ChoiceRow("DNS", DnsPresets.names, s.dns) { a.set("dns", it) }
            if (s.dns == 4) TextRow("Свои DNS через запятую", s.dnsCustom, "1.1.1.1, 8.8.8.8") { a.set("dnsCustom", it) }
            SwitchRow(
                "Xray для неподдерживаемых серверов",
                "sing-box ведёт всё, что умеет. Остальное (XHTTP и др.) идёт через Xray. Выключите, и такие серверы станут неактивными",
                s.xrayFallback
            ) { a.set("xrayFallback", it); a.reapplyIfOn() }
            SliderRow("MTU", s.mtu.toFloat(), 1280f, 1500f, 21, { it.toInt().toString() }) { a.set("mtu", it.toInt()) }
        }
        Section("Низкоуровневые") {
            SwitchRow("IPv6", "Выключите, если сайты зависают", s.ipv6) { a.set("tuning.ipv6", it) }
            SwitchRow("Sniffing", "Определять домен по HTTP, TLS и QUIC", s.sniff) { a.set("tuning.sniff", it) }
            SwitchRow("Блокировать QUIC", "Браузеры перейдут на TCP", s.quic) { a.set("tuning.quic", it) }
            SwitchRow("Локальная сеть напрямую", "10.x, 172.16.x, 192.168.x мимо VPN", s.lan) { a.set("tuning.lan", it) }
            SwitchRow("Сеть без лимита", "Система не считает трафик VPN мобильным", s.unm) { a.set("tuning.unm", it) }
            SwitchRow("Mux", "Не работает с XTLS Vision", s.mux) { a.set("tuning.mux", it) }
        }
        Section("Приложения") {
            ChoiceRow("Через VPN идут", listOf("Все", "Исключить", "Только"), s.appMode) { a.set("tuning.am", it) }
            if (s.appMode != 0) {
                val n = s.apps.split(',', '\n', ' ').count { it.isNotBlank() }
                BaseRow("Выбрать приложения", if (n == 0) "Ничего не выбрано" else "Выбрано: $n", { open(Sheet.Apps) })
            }
        }
    }
}

private val kindNames = mapOf("protocol" to "протокол", "rules" to "правила", "ui" to "интерфейс")

@Composable
fun PluginsPage(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, onBack: () -> Unit) {
    SubPage("Плагины и ядра", onBack) {
        Button(
            { open(Sheet.AddPlugin) },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
            enabled = s.plugBusy == null
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить плагин", maxLines = 1)
        }
        Box(Modifier.padding(horizontal = 16.dp)) { BusyLine(s.plugBusy) }
        Section("Ядра и протоколы") {
            for (p in s.plugins) {
                BaseRow(
                    p.name + if (p.primary && p.enabled) " · основное" else "",
                    p.description + if (!p.present) " · нет в сборке" else "",
                    onClick = { open(Sheet.Plugin(p.id)) },
                    trailing = {
                        if (p.present) {
                            Switch(checked = p.enabled, onCheckedChange = {
                                val cores = s.plugins.filter { x -> x.core }
                                if (p.core && p.enabled && cores.none { x -> x.id != p.id && x.present && x.enabled }) {
                                    a.say("Нужно хотя бы одно ядро")
                                } else {
                                    a.call("corePlugin", JSONObject().put("id", p.id).put("op", "enable").put("value", it)) { a.reapplyIfOn() }
                                }
                            })
                        }
                    }
                )
            }
        }
        Section("Версии ядер") {
            BaseRow("sing-box", "Выбрать или скачать версию", { open(Sheet.SbCores) })
            BaseRow("Xray", "Встроенное, своё или скачать", { open(Sheet.XrayCores) })
        }
        Section("Установленные плагины") {
            if (s.installed.isEmpty()) {
                BaseRow("Плагинов пока нет", "Нажмите «Добавить плагин»: из файла, по ссылке или из каталога")
            }
            for (p in s.installed) {
                BaseRow(
                    p.name + " " + p.version,
                    (p.kinds.joinToString(" · ") { kindNames[it] ?: it } + (if (p.author.isNotEmpty()) " · " + p.author else "") +
                        (if (p.hasUi) " · интерфейс не поддерживается" else "")),
                    onClick = if (p.schema.isNotEmpty()) ({ open(Sheet.PluginSettings(p.key)) }) else null,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = p.enabled, onCheckedChange = {
                                a.call("pluginEnable", JSONObject().put("key", p.key).put("value", it))
                            })
                            IconButton({ a.call("pluginDelete", p.key) }) {
                                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PingPage(a: ComposeActivity, s: Snap, onBack: () -> Unit) {
    SubPage("Пинг и сервисы", onBack) {
        Section("Проверка пинга") {
            ChoiceRow("Способ", listOf("Авто", "TCP", "UDP", "Оба", "ICMP"), s.pingMode) { a.set("pingMode", it) }
            ChoiceRow("Таймаут", listOf("2 с", "4 с", "8 с"), s.pingTimeout) { a.set("pingTimeout", it) }
            SwitchRow("Проверять автоматически", null, s.autoPing) { a.set("autoPing", it) }
            SwitchRow("Сортировать по пингу", null, s.sortPing) { a.set("sortPing", it) }
        }
        Section("Сервисы") {
            SwitchRow("Проверять автоматически", "Discord, Telegram и YouTube через каждый сервер, пока VPN включён", s.autoCheck) { a.set("autoCheck", it) }
            if (s.autoCheck) SliderRow("Интервал", s.checkInterval, 5f, 240f, 46, { it.toInt().toString() + " мин" }) { a.set("checkInterval", it.toDouble()) }
            BaseRow("Проверить сейчас", null, { a.call("checkServices"); a.say("Проверяю сервисы…") })
        }
    }
}

@Composable
fun SubsPage(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, onBack: () -> Unit) {
    SubPage("Подписки", onBack) {
        Section("Клиент") {
            ChoiceRow("Под кого маскироваться", listOf("Tunn", "Happ", "v2RayTun", "Incy"), s.mask) { a.set("mask", it) }
            BaseRow("HWID", "Идентификатор устройства: показать, копировать, сбросить", { open(Sheet.Hwid) })
        }
    }
}

@Composable
fun UpdatesPage(a: ComposeActivity, s: Snap, onBack: () -> Unit) {
    SubPage("Обновления", onBack) {
        Section("Версии") {
            BaseRow("Проверить обновления", "github.com/Fasdeq13/FasdeqVPN/releases", {
                a.say("Проверяю…")
                a.call("checkUpdate") { r ->
                    val e = r.optString("error")
                    a.say(if (e.isNotEmpty() && e != "null") e else if (r.optBoolean("available")) "Доступна версия " + r.optString("tag") else "Установлена последняя версия")
                }
            })
            BaseRow("Открыть страницу релизов", "Скачать APK вручную", { a.call("open", "https://github.com/Fasdeq13/FasdeqVPN/releases/") })
        }
    }
}

@Composable
fun AboutPage(a: ComposeActivity, onBack: () -> Unit) {
    SubPage("О приложении", onBack) {
        val tapTimes = remember { mutableListOf<Long>() }
        var egg by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Section("Tunn") {
            BaseRow("Tunn", "Версия 3.10 · создатель fasdeq13", {
                val now = android.os.SystemClock.elapsedRealtime()
                tapTimes.add(now)
                tapTimes.removeAll { now - it > 5_000L }
                if (tapTimes.size >= 3) {
                    tapTimes.clear()
                    egg = true
                }
            })
        }
        if (egg) EggPicture(ctx) { egg = false }
        Section("Участники") {
            BaseRow("Fasdeq13", "Создатель", { a.call("open", "https://github.com/Fasdeq13/FasdeqVPN") })
            BaseRow("montik")
            BaseRow("utowike")
        }
        Section("Официальный канал") {
            BaseRow("Tunn в Telegram", "t.me/Tunn_client", { a.call("open", "https://t.me/Tunn_client") }, leading = {
                ServiceIcon(1, MaterialTheme.colorScheme.primary, 26)
            })
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, min: Float, max: Float, label: String, enabled: Boolean = true, onDone: (Float) -> Unit) {
    val v = remember(value) { mutableStateOf(value) }
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Slider(v.value, { v.value = it }, valueRange = min..max, enabled = enabled, onValueChangeFinished = { onDone(v.value) })
    }
}

@Composable
fun EggPicture(ctx: android.content.Context, onClose: () -> Unit) {
    val bmp = remember {
        try {
            val name = ctx.assets.list("")?.firstOrNull { it.startsWith("icon.") }
            if (name == null) null else ctx.assets.open(name).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                androidx.compose.foundation.Image(bmp, "icon", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
            } else {
                Text("Положите icon.png, icon.jpg или icon.jpeg в корень проекта и пересоберите", Modifier.padding(32.dp), color = Color.White)
            }
        }
    }
}


private fun installChain(a: ComposeActivity, ids: List<String>, i: Int = 0) {
    if (i >= ids.size) {
        a.say("Готово")
        return
    }
    a.call("installComponent", ids[i]) { r ->
        val e = r.optString("error")
        if (e.isNotEmpty()) a.say(e) else installChain(a, ids, i + 1)
    }
}

@Composable
fun ComponentsPage(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, onBack: () -> Unit) {
    val busy = s.xrayBusy != null
    SubPage("Компоненты", onBack) {
        Text(
            if (s.dist == "full") "В этой сборке всё уже внутри. Версии ядер можно сменить или докачать."
            else "Здесь можно скачать то, чего нет в этой сборке. Скачанное сразу появляется в настройках и плагинах, неустановленное скрыто.",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(Modifier.padding(horizontal = 16.dp)) { BusyLine(s.xrayBusy) }
        Section("Ядра") {
            ComponentRow(
                "sing-box", "Обязательное ядро, рекомендуется. VLESS, VMess, Trojan, Shadowsocks, TUIC.",
                s.hasSb, s.dist == "full", busy,
                onInstall = { a.say("Скачиваю sing-box…"); installChain(a, listOf("singbox")) },
                onRemove = { a.call("removeComponent", "singbox") },
                onVersions = { open(Sheet.SbCores) }
            )
            ComponentRow(
                "Xray", "Не обязателен, но рекомендуется: берёт то, чего не умеет sing-box (например XHTTP). Можно поставить только его.",
                s.hasXray, s.dist == "full", busy,
                onInstall = { a.say("Скачиваю Xray…"); installChain(a, listOf("xray")) },
                onRemove = { a.call("removeComponent", "xray") },
                onVersions = { open(Sheet.XrayCores) }
            )
        }
        Section("Протоколы") {
            BaseRow("WireGuard", "Встроен во все сборки, ничего скачивать не нужно")
            ComponentRow(
                "AmneziaWG", "Библиотека для AmneziaWG-конфигов (обфусцированный WireGuard).",
                s.hasAwg, s.dist == "full", busy,
                onInstall = { a.say("Скачиваю AmneziaWG…"); installChain(a, listOf("awg")) },
                onRemove = { a.call("removeComponent", "awg") },
                onVersions = null
            )
        }
        if (s.dist != "full") {
            val missing = buildList {
                if (!s.hasSb) add("singbox")
                if (!s.hasXray) add("xray")
                if (!s.hasAwg) add("awg")
            }
            if (missing.isNotEmpty()) {
                Button(
                    { a.say("Скачиваю…"); installChain(a, missing) },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
                    enabled = !busy
                ) { Text("Скачать всё недостающее") }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    title: String, sub: String, installed: Boolean, bundled: Boolean, busy: Boolean,
    onInstall: () -> Unit, onRemove: () -> Unit, onVersions: (() -> Unit)?
) {
    BaseRow(
        title,
        sub + if (installed) (if (bundled) " · в комплекте" else " · установлено") else " · не установлено",
        onClick = if (installed && onVersions != null) onVersions else null,
        trailing = {
            if (!installed) {
                FilledTonalButton(onInstall, enabled = !busy) { Text("Скачать") }
            } else if (!bundled) {
                TextButton(onRemove, enabled = !busy) { Text("Удалить") }
            }
        }
    )
}

@Composable
fun SetupDialog(a: ComposeActivity, s: Snap) {
    var sb by remember { mutableStateOf(true) }
    var xr by remember { mutableStateOf(true) }
    var awg by remember { mutableStateOf(false) }
    val busy = s.xrayBusy != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text("Что скачать?") },
        text = {
            Column {
                Text("В этой сборке нет ядер, их нужно скачать. Позже это можно сделать в Настройки → Компоненты.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                BusyLine(s.xrayBusy)
                SetupCheck("sing-box · обязательное, рекомендуется", sb) { sb = it }
                SetupCheck("Xray · рекомендуется", xr) { xr = it }
                SetupCheck("AmneziaWG", awg) { awg = it }
            }
        },
        confirmButton = {
            Button({
                val ids = buildList { if (sb) add("singbox"); if (xr) add("xray"); if (awg) add("awg") }
                a.call("setupDone")
                if (ids.isNotEmpty()) { a.say("Скачиваю…"); installChain(a, ids) }
            }, enabled = !busy) { Text("Скачать выбранное") }
        },
        dismissButton = {
            TextButton({ a.call("setupDone") }, enabled = !busy) { Text("Позже") }
        }
    )
}

@Composable
private fun SetupCheck(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(value, onChange)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
