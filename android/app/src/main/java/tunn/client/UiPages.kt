@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package tunn.client

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import kotlinx.coroutines.launch
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
import androidx.compose.ui.unit.sp
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
private fun Tune(a: ComposeActivity, s: Snap, key: String, title: String, min: Float, max: Float, def: Float, unit: String = "", enabled: Boolean = true) {
    val v = s.t(key, def)
    SliderRow(title, v, min, max, "${v.toInt()}$unit", enabled) { a.set("tune.$key", it.toDouble()) }
}

@Composable
fun Fold(title: String, startOpen: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Section(title) { content() }
}

@Composable
fun LookModules(a: ComposeActivity, s: Snap, fluentOn: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val themeRev = Themes.rev
    val soRev = UiNative.rev
    val themeId = remember(themeRev) { Themes.activeId(ctx) }
    val themeOn = themeId.isNotEmpty()
    val soKey = remember(soRev) { UiNative.activeKey(ctx) }
    Section("Модули оформления") {
        Text(
            "Каждый эффект включается и выключается отдельно. Выключенный модуль не тратит ресурсы.",
            Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow("Liquid Glass", "Стекло, размытие и линза", s.glass) {
            a.set("glass", it)
            if (it) a.set("tune.fluent", 0.0)
        }
        SwitchRow("Fluent", "Mica и плоские карточки", fluentOn, enabled = !s.glass) { a.set("tune.fluent", if (it) 1.0 else 0.0) }
        SwitchRow("Тема-плагин", if (themeOn) "Активна: " + themeId else "Не выбрана, ниже список", themeOn, enabled = themeOn) { if (!it) Themes.select(ctx, "") }
        SwitchRow("Интерфейс-плагин (.so)", if (soKey.isEmpty()) "Не выбран" else "Включён", soKey.isNotEmpty(), enabled = soKey.isNotEmpty()) { if (!it) UiNative.setActive(ctx, "") }
        SwitchRow("Своя картинка фона", null, s.glassBg != 0L, enabled = s.glassBg != 0L) { if (!it) a.call("clearGlassBg") }
        SwitchRow("Свой цвет фона", null, s.bgOn, enabled = !s.glass) { a.set("bgCustom", it) }
        SwitchRow("Вращение кнопки", "Анимация кнопки питания", s.btnSpin) { a.set("btnSpin", it) }
        BaseRow("Отключить все модули", "Вернуть стандартный Material You без тем, интерфейсов и фона", onClick = {
            a.set("glass", false)
            a.set("tune", org.json.JSONObject())
            a.set("bgCustom", false)
            a.set("dynamic", true)
            Themes.select(ctx, "")
            UiNative.setActive(ctx, "")
            if (s.glassBg != 0L) a.call("clearGlassBg")
        })
    }
}


@Composable
fun LookPage(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, onBack: () -> Unit) {
    SubPage("Оформление", onBack) {
        val fluentOn = !s.glass && s.t("fluent", 0f) > 0.5f
        LookModules(a, s, fluentOn)
        Section("Стиль") {
            ChoiceRow("Оформление", listOf("Material You", "Liquid Glass", "Fluent"), if (s.glass) 1 else if (fluentOn) 2 else 0) {
                a.set("glass", it == 1)
                a.set("tune.fluent", if (it == 2) 1.0 else 0.0)
            }
            Text(
                when {
                    s.glass -> "Стекло как в iOS 26: размытие, преломление на кромках, блики. На Android 13+ настоящая линза на шейдере, на старых упрощённо."
                    fluentOn -> "Как в Windows 11: матовый фон Mica, плоские карточки с тонкой обводкой, малые скругления."
                    else -> "Material You: цвета из обоев или свои."
                },
                Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChoiceRow("Переключатели", listOf("Material", "iOS", "Fluent"), s.t("swStyle", if (fluentOn) 2f else 0f).toInt()) { a.set("tune.swStyle", it.toDouble()) }
            ChoiceRow("Запуск VPN", listOf("Кнопка", "Переключатель"), if (s.t("startStyle", 0f) > 0.5f) 1 else 0) { a.set("tune.startStyle", it.toDouble()) }
        }
        Section("Тема") {
            ChoiceRow("Режим", listOf("Система", "Светлая", "Тёмная"), s.theme, enabled = s.glass || !s.bgOn) { a.set("theme", it) }
            if (!s.glass && !fluentOn) {
                ChoiceRow("Цвета", listOf("Системные", "Свои"), if (s.dynamic) 0 else 1, enabled = !s.bgOn) { a.set("dynamic", it == 0) }
                SwitchRow("Чёрный фон", "Для OLED-экранов в тёмной теме", s.amoled, enabled = !s.bgOn) { a.set("amoled", it) }
            }
        }
        Section("Цвет акцента") {
            if (s.glass) SwitchRow("Свой цвет акцента", "Выключено: синий, как в iOS", s.t("gAcc", 0f) > 0.5f) { a.set("tune.gAcc", if (it) 1.0 else 0.0) }
            Dim(if (s.glass) true else (!s.dynamic || s.bgOn)) {
                Column {
                    SwatchGrid(accents, s.accentHex) {
                        a.set("accentHex", it)
                        if (s.glass) a.set("tune.gAcc", 1.0) else a.set("dynamic", false)
                    }
                    HexField(s.accentHex) {
                        a.set("accentHex", it)
                        if (s.glass) a.set("tune.gAcc", 1.0) else a.set("dynamic", false)
                    }
                }
            }
        }
        Fold("Фон") {
            BaseRow("Своя картинка", "Под всем приложением, в том числе на экране VPN. Сейчас: " + (if (s.glassBg != 0L) "своя картинка" else if (s.glass) "стандартный фон" else "нет"), onClick = {
                a.call("pickGlassBg") { r ->
                    val e = r.optString("error")
                    if (e.isNotEmpty()) a.say(e)
                }
            })
            if (s.glassBg != 0L) BaseRow("Убрать картинку", null, onClick = { a.call("clearGlassBg") })
            if (s.glassBg != 0L || s.glass) {
                Tune(a, s, "bgDim", "Затемнение фона", 0f, 100f, 0f, "%")
                Tune(a, s, "bgBlur", "Размытие фона", 0f, 100f, 0f, "%")
            }
            if (!s.glass) {
                SwitchRow("Свой цвет фона", "Пока включён, тема, системные цвета и чёрный фон неактивны, но их значения сохраняются", s.bgOn) { a.set("bgCustom", it) }
                if (s.bgOn) {
                    SwatchGrid(backgrounds, s.bgHex) { a.set("bgHex", it) }
                    HexField(s.bgHex) { a.set("bgHex", it) }
                }
            }
        }
        if (s.glass) {
            Fold("Стекло") {
                Tune(a, s, "gBlur", "Размытие под стеклом", 0f, 100f, 50f, "%")
                SliderRow("Степень стекла", s.glassLevel.toFloat(), 0f, 100f, if (s.glassLevel < 35) "матовое" else if (s.glassLevel < 70) "среднее" else "прозрачное") { a.set("glassLevel", it.toInt()) }
                Tune(a, s, "gTint", "Плотность подложки", 0f, 100f, 50f, "%")
                Tune(a, s, "gSat", "Насыщенность фона", 0f, 250f, 155f, "%")
            }
            Fold("Линза и свет") {
                Tune(a, s, "gRefr", "Преломление", 0f, 200f, 100f, "%")
                Tune(a, s, "gEdge", "Ширина кромки", 4f, 40f, 18f, " dp")
                Tune(a, s, "gDisp", "Цветовая дисперсия", 0f, 200f, 100f, "%")
                Tune(a, s, "gSpec", "Блики и свечение", 0f, 200f, 100f, "%")
            }
            Fold("Слияние и капли") {
                Tune(a, s, "gMerge", "Дистанция слияния панелей", 0f, 30f, 10f, " dp")
                Tune(a, s, "gDrop", "Сила линзы капли", 50f, 250f, 100f, "%")
            }
        } else {
            Fold("Material You") {
                ChoiceRow("Стиль карточек", listOf("Тональные", "Контурные", "Плоские"), s.t("cardStyle", 0f).toInt()) { a.set("tune.cardStyle", it.toDouble()) }
                Tune(a, s, "mSat", "Насыщенность цвета", 0f, 200f, 100f, "%", enabled = !s.dynamic && !s.bgOn)
                Tune(a, s, "mSurf", "Контраст поверхностей", 0f, 250f, 100f, "%", enabled = !s.dynamic && !s.bgOn)
            }
        }
        ThemePacks(a)
        CatalogPicker(a, "theme")
        Fold("Форма и размеры") {
            Tune(a, s, "uiRadius", "Скругление", 40f, 160f, 100f, "%")
            Tune(a, s, "uiSpace", "Отступы", 60f, 170f, 100f, "%")
            Tune(a, s, "uiFont", "Размер текста", 80f, 130f, 100f, "%")
            BaseRow("Сбросить тонкую настройку", "Вернуть все эти ползунки к значениям по умолчанию", onClick = { a.set("tune", org.json.JSONObject()) })
        }
        Fold("Кнопка питания") {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                PowerButton(s.status, 120.dp, s.btnShape, s.btnLobes, s.btnAmp, s.btnSpin) {}
            }
            ChoiceRow("Форма", listOf("Печенька", "Круг", "Квадрат", "Цветок"), s.btnShape) { a.set("btnShape", it) }
            SliderRow("Лепестки", s.btnLobes.toFloat(), 3f, 16f, "${s.btnLobes}", enabled = s.btnShape == 0 || s.btnShape == 3) { a.set("btnLobes", it.toInt()) }
            SliderRow("Выраженность", s.btnAmp, 0f, 0.14f, String.format("%.0f%%", s.btnAmp * 700f), enabled = s.btnShape == 0 || s.btnShape == 3) { a.set("btnAmp", it.toDouble()) }
            SliderRow("Размер", s.btnSize.toFloat(), 140f, 260f, "${s.btnSize} dp") { a.set("btnSize", it.toInt()) }
            SwitchRow("Вращение", "Кнопка медленно вращается", s.btnSpin) { a.set("btnSpin", it) }
        }
        Fold("Значок") {
            BaseRow("Иконка приложения", "Варианты и своя картинка", { open(Sheet.IconPick) })
        }
    }
}

@Composable
fun ConnectPage(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, onBack: () -> Unit) {
    SubPage("Подключение", onBack) {
        Section("Модули подключения") {
            Text(
                "Каждая функция включается отдельно. Выключенная не работает и не тратит ресурсы.",
                Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow("Подключаться при запуске", null, s.autoConnect) { a.set("autoConnect", it) }
            SwitchRow("Автопереподключение", "Поднять туннель заново, если он оборвался", s.autoReconnect) { a.set("autoReconnect", it) }
            SwitchRow("Запасной сервер", "Перейти на лучший по пингу, если текущий не отвечает", s.autoFailover) { a.set("autoFailover", it) }
            SwitchRow("Xray для неподдерживаемых серверов", "XHTTP и другое, что не умеет sing-box. Выключите, и такие серверы станут неактивными", s.xrayFallback) { a.set("xrayFallback", it); a.reapplyIfOn() }
            SwitchRow("Проверять пинг автоматически", null, s.autoPing) { a.set("autoPing", it) }
            SwitchRow("Проверять сервисы автоматически", "Discord, Telegram, YouTube", s.autoCheck) { a.set("autoCheck", it) }
            BaseRow("Отключить все модули", "Выключить автоматику и вернуть сетевые настройки по умолчанию", onClick = {
                a.set("autoConnect", false)
                a.set("autoReconnect", false)
                a.set("autoFailover", false)
                a.set("autoPing", false)
                a.set("autoCheck", false)
                a.set("tuning.ipv6", true)
                for (k in listOf("sniff", "lan", "quic", "unm", "mux")) a.set("tuning." + k, false)
                a.set("tuning.am", 0)
                a.reapplyIfOn()
            })
        }
        Section("Основное") {
            BaseRow(
                "Работа в фоне",
                "Если VPN сам отключается через время (Tecno, Xiaomi, Huawei, Samsung): разрешите Tunn работать без ограничений батареи и закрепите приложение в списке недавних",
                onClick = { a.call("batterySettings") }
            )
            ChoiceRow("DNS", DnsPresets.names, s.dns) { a.set("dns", it) }
            if (s.dns == 4) TextRow("Свои DNS через запятую", s.dnsCustom, "1.1.1.1, 8.8.8.8") { a.set("dnsCustom", it) }
            SliderRow("MTU", s.mtu.toFloat(), 1280f, 1500f, 21, { it.toInt().toString() }) { a.set("mtu", it.toInt()) }
        }
        CatalogPicker(a, "plugin")
        Fold("Низкоуровневые") {
            SwitchRow("IPv6", "Выключите, если сайты зависают", s.ipv6) { a.set("tuning.ipv6", it) }
            SwitchRow("Sniffing", "Определять домен по HTTP, TLS и QUIC", s.sniff) { a.set("tuning.sniff", it) }
            SwitchRow("Блокировать QUIC", "Браузеры перейдут на TCP", s.quic) { a.set("tuning.quic", it) }
            SwitchRow("Локальная сеть напрямую", "10.x, 172.16.x, 192.168.x мимо VPN", s.lan) { a.set("tuning.lan", it) }
            SwitchRow("Сеть без лимита", "Система не считает трафик VPN мобильным", s.unm) { a.set("tuning.unm", it) }
            SwitchRow("Mux", "Не работает с XTLS Vision", s.mux) { a.set("tuning.mux", it) }
        }
        Fold("Приложения", true) {
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
        Fold("Версии ядер") {
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
        Fold("Проверка пинга", true) {
            ChoiceRow("Способ", listOf("Авто", "TCP", "UDP", "Оба", "ICMP"), s.pingMode) { a.set("pingMode", it) }
            ChoiceRow("Таймаут", listOf("2 с", "4 с", "8 с"), s.pingTimeout) { a.set("pingTimeout", it) }
            SwitchRow("Проверять автоматически", null, s.autoPing) { a.set("autoPing", it) }
            SwitchRow("Сортировать по пингу", null, s.sortPing) { a.set("sortPing", it) }
        }
        Fold("Сервисы", true) {
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
            if (s.update.packName.isNotEmpty()) BaseRow(
                (if (s.update.packKind == "fix") "Установить исправление" else "Установить обновление"),
                s.update.packName + (if (s.update.busy.isNotEmpty()) " · " + s.update.busy else ""),
                { if (s.update.busy.isEmpty()) a.call("applyUpdate") { r -> r.optString("error").takeIf { it.isNotEmpty() }?.let { a.say(it) } } }
            )
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
            BaseRow("Tunn", "Версия 4.3 · создатель fasdeq13", {
                // 3 taps within 5 seconds
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


@androidx.compose.runtime.Composable
fun CatalogPicker(a: ComposeActivity, kind: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var stamp by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val themeRev = Themes.rev
    val st = stamp
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Store.items == null) {
            val e = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Store.load() }
            if (e != null) msg = e
            stamp++
        }
    }
    val theme = kind == "theme"
    val localAll = remember(st, themeRev) { Plugins.list(ctx) }
    val enabledMap = remember(localAll) { localAll.associate { it.key to Plugins.enabled(ctx, it.key) } }
    val themeIds = remember(themeRev) { Themes.list(ctx).map { it.id }.toSet() }
    val remote = Store.items?.filter { it.kind == kind } ?: emptyList()
    val localIds = if (theme) themeIds else localAll.map { it.id }.toSet()
    val localPlugins = localAll.filter { "ui-native" !in it.kinds && "ui" !in it.kinds }
    Section(if (theme) "Каталог тем" else "Плагины подключения") {
        if (!theme) {
            if (localPlugins.isEmpty()) Text("Установленных плагинов нет. Скачайте из каталога ниже.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
            localPlugins.forEach { p ->
                val on = enabledMap[p.key] ?: true
                SwitchRow(p.name + "  " + p.version, p.manifest.optString("description").ifEmpty { null }, on) {
                    Plugins.setEnabled(ctx, p.key, it)
                    stamp++
                }
            }
            Text("Правила применяются при следующем подключении.", Modifier.padding(horizontal = 20.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        remote.filter { it.id !in localIds }.forEach { item ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = Store.busy == null, onClick = {
                    scope.launch {
                        msg = ""
                        val e = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Store.install(ctx, item) }
                        if (e != null) msg = e else {
                            if (theme) Themes.select(ctx, item.id)
                            else Plugins.list(ctx).filter { it.id == item.id }.forEach { Plugins.setEnabled(ctx, it.key, true) }
                            a.say(item.name + if (theme) " применена" else " включён")
                        }
                        stamp++
                    }
                }) { Text(if (theme) "Скачать и применить" else "Скачать") }
            }
        }
        if (!theme && Store.items != null) Text("В каталоге плагинов: " + remote.size, Modifier.padding(horizontal = 20.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (remote.isEmpty() && Store.items == null && msg.isEmpty()) Text("Загружаю каталог…", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = {
            scope.launch {
                msg = ""
                val e = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Store.load() }
                if (e != null) msg = e
                stamp++
            }
        }, Modifier.padding(horizontal = 12.dp)) { Text("Обновить каталог") }
    }
}


@androidx.compose.runtime.Composable
fun ThemePacks(a: ComposeActivity) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rev = Themes.rev
    val packs = remember(rev) { Themes.list(ctx) }
    val active = remember(rev) { Themes.activeId(ctx) }
    var msg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Section("Темы-плагины") {
        Text(
            "JSON-пакет с палитрой и настройками меняет оформление всего приложения. Формат описан в plugins/THEMES.md.",
            Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        packs.forEach { p ->
            val on = p.id == active
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clickable { if (on) Themes.select(ctx, "") else Themes.select(ctx, p.id) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (on) "  ✓" else "", style = MaterialTheme.typography.bodyLarge)
                    if (p.author.isNotEmpty()) Text(p.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { Themes.remove(ctx, p.id) }) { Text("Удалить") }
            }
        }
        TextButton(onClick = {
            a.pickPlugin { uri ->
                if (uri != null) {
                    msg = try {
                        val txt = a.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                        Themes.install(ctx, txt) ?: ""
                    } catch (e: Exception) { e.message ?: "Ошибка чтения" }
                }
            }
        }, Modifier.padding(horizontal = 12.dp)) { Text("Установить тему из файла…") }
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}


@androidx.compose.runtime.Composable
fun JournalPage(a: ComposeActivity, s: Snap, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf(J.read(500)) }
    SubPage("Журнал и диагностика", onBack) {
        Section("Подключение") {
            SwitchRow("Автопереподключение", "Если туннель оборвался сам, поднять его заново (не чаще 4 раз за 5 минут)", s.autoReconnect) { a.set("autoReconnect", it) }
            SwitchRow("Запасной сервер", "Если сервер не отвечает после повторов, перейти на лучший по пингу", s.autoFailover) { a.set("autoFailover", it) }
        }
        Section("Журнал") {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ text = J.read(500) }) { Text("Обновить") }
                TextButton({
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Tunn journal", text))
                    a.say("Журнал скопирован")
                }) { Text("Копировать") }
                TextButton({ J.clear(); text = "" }) { Text("Очистить") }
            }
            Text(
                text.ifBlank { "Пока пусто. Записи появятся при подключении." },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@androidx.compose.runtime.Composable
fun StorePage(a: ComposeActivity, s: Snap, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var stamp by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val items = Store.items
    val themeRev = Themes.rev
    val soRev = UiNative.rev
    val st = stamp
    val haveMap = remember(st, themeRev, soRev, items) { items?.associate { it.id + ":" + it.kind to Store.installed(ctx, it) } ?: emptyMap() }
    val localUi = remember(st, soRev) { UiNative.list(ctx) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Store.items == null) {
            val e = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Store.load() }
            if (e != null) msg = e
            stamp++
        }
    }
    SubPage("Каталог плагинов и тем", onBack) {
        Text(
            "Репозиторий github.com/" + Store.REPO + ". Архивы проверяются по SHA-256 из каталога. Плагины выполняют код: ставьте только то, чему доверяете.",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton({
                scope.launch {
                    msg = ""
                    val e = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Store.load() }
                    if (e != null) msg = e
                    stamp++
                }
            }) { Text("Обновить каталог") }
        }
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (items == null && msg.isEmpty()) Text("Загрузка…", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        val titles = listOf("plugin" to "Плагины правил", "ui" to "Интерфейсы (.so на Rust)", "theme" to "Темы")
        for ((kind, title) in titles) {
            val group = items?.filter { it.kind == kind } ?: continue
            if (group.isEmpty()) continue
            Section(title) {
                for (item in group) {
                    val have = haveMap[item.id + ":" + item.kind]
                    val upd = have != null && have != item.version
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(item.name + "  " + item.version + if (have != null && !upd) "  ✓" else "", style = MaterialTheme.typography.bodyLarge)
                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    msg = ""
                                    val e = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Store.install(ctx, item) }
                                    if (e != null) msg = e else a.say(item.name + " установлен")
                                    stamp++
                                }
                            }, enabled = Store.busy == null) { Text(if (have == null) "Установить" else if (upd) "Обновить до " + item.version else "Переустановить") }
                            if (have != null) TextButton({ Store.remove(ctx, item); stamp++ }) { Text("Удалить") }
                            if (kind == "ui" && have != null) {
                                val key = localUi.firstOrNull { p -> p.id == item.id }?.key
                                if (key != null) TextButton({ UiNative.setActive(ctx, if (UiNative.activeKey(ctx) == key) "" else key) }) {
                                    Text(if (UiNative.activeKey(ctx) == key) "Выключить" else "Включить")
                                }
                            }
                        }
                    }
                }
            }
        }
        val local = localUi
        if (local.isNotEmpty()) Section("Установленные интерфейсы") {
            for (p in local) {
                val on = UiNative.activeKey(ctx) == p.key
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(p.name + "  " + p.version + if (on) "  ✓" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton({ UiNative.setActive(ctx, if (on) "" else p.key) }) { Text(if (on) "Выключить" else "Включить") }
                }
            }
            Text(
                "Если интерфейс не запускается или приложение закрывается, при следующем старте он выключается сам. Кнопка × в углу возвращает стандартный.",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
