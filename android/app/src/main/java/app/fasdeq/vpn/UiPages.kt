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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        Section("Тема") {
            ChoiceRow("Режим", listOf("Система", "Светлая", "Тёмная"), s.theme) { a.set("theme", it) }
            SwitchRow("Цвета системы", "Палитра из обоев, Android 12 и новее. Выключите, чтобы выбрать свой цвет", s.dynamic) { a.set("dynamic", it) }
            SwitchRow("Чёрный фон", "Для OLED-экранов в тёмной теме", s.amoled) { a.set("amoled", it) }
        }
        Section("Цвет акцента") {
            SwatchGrid(accents, s.accentHex) {
                a.set("accentHex", it)
                a.set("dynamic", false)
            }
            HexField(s.accentHex) {
                a.set("accentHex", it)
                a.set("dynamic", false)
            }
        }
        Section("Фон") {
            SwitchRow("Свой цвет фона", "Карточки и текст подстроятся под цвет. Светлый фон включает светлую тему", s.bgOn) { a.set("bgOn", it) }
            if (s.bgOn) {
                SwatchGrid(backgrounds, s.bgHex) { a.set("bgHex", it) }
                HexField(s.bgHex) { a.set("bgHex", it) }
            }
        }
        Section("Значок") {
            BaseRow("Иконка приложения", "Варианты и своя картинка", { open(Sheet.IconPick) })
        }
    }
}

@Composable
fun ConnectPage(a: ComposeActivity, s: Snap, onBack: () -> Unit) {
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
            if (s.appMode != 0) TextRow("Пакеты через запятую", s.apps, "org.telegram.messenger, com.discord", false) { a.set("tuning.apps", it) }
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
        Section("Tunn") {
            BaseRow("Tunn", "Версия 3.4 · создатель fasdeq13")
        }
        Section("Участники") {
            BaseRow("Fasdeq13", "Создатель", { a.call("open", "https://github.com/Fasdeq13/FasdeqVPN") })
            BaseRow("montik")
            BaseRow("fezix64")
        }
        Section("Официальный канал") {
            BaseRow("Tunn в Telegram", "t.me/Tunn_client", { a.call("open", "https://t.me/Tunn_client") }, leading = {
                ServiceIcon(1, MaterialTheme.colorScheme.primary, 26)
            })
        }
    }
}
