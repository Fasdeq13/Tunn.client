@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.fasdeq.vpn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private fun mb(n: Long): String = String.format("%.1f МБ", n.toDouble() / 1048576)

@Composable
fun SheetHost(a: ComposeActivity, s: Snap, sheet: Sheet?, onClose: () -> Unit, onSwitch: (Sheet) -> Unit) {
    if (sheet == null) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding()
        ) {
            when (sheet) {
                is Sheet.Add -> AddSheet(a, onClose)
                is Sheet.Server -> ServerSheet(a, s, sheet.id, onClose)
                is Sheet.Group -> GroupSheet(a, sheet.name, s.localName, onClose)
                is Sheet.SbCores -> SbSheet(a, s)
                is Sheet.XrayCores -> XraySheet(a, s)
                is Sheet.Plugin -> PluginSheet(a, s, sheet.id, onSwitch)
                is Sheet.Hwid -> HwidSheet(a, onClose)
                is Sheet.AddPlugin -> AddPluginSheet(a, onClose, onSwitch)
                is Sheet.PluginUrl -> PluginUrlSheet(a, onClose)
                is Sheet.Catalog -> CatalogSheet(a, s)
                is Sheet.PluginSettings -> PluginSettingsSheet(a, s, sheet.key, onClose)
                is Sheet.IconPick -> IconSheet(a, s)
                is Sheet.Apps -> AppPickerSheet(a, s)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SheetTitle(title: String, sub: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (!sub.isNullOrEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
fun AddSheet(a: ComposeActivity, onClose: () -> Unit) {
    val text = remember { mutableStateOf("") }
    SheetTitle("Добавить сервер", "Одна или несколько ссылок, либо адрес подписки")
    OutlinedTextField(
        value = text.value,
        onValueChange = { text.value = it },
        placeholder = { Text("vless://…  trojan://…  ss://…  wireguard://…  awg://…  https://подписка") },
        minLines = 3,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton({
            a.call("clipboard") { r ->
                val t = r.optString("text")
                if (t.isNotEmpty()) text.value = if (text.value.isEmpty()) t else text.value + "\n" + t
            }
        }) { Text("Вставить") }
        TextButton(onClose) { Text("Отмена") }
        Button({
            val t = text.value.trim()
            if (t.isNotEmpty()) {
                a.say("Добавляю…")
                a.call("add", t) { r ->
                    val e = r.optString("error")
                    val n = r.optInt("added")
                    a.say(if (e.isNotEmpty()) e else if (n > 0) "Добавлено: $n" else "Уже есть в списке")
                }
                onClose()
            }
        }, enabled = text.value.isNotBlank()) { Text("Добавить") }
    }
}

@Composable
fun VerRow(selected: Boolean, title: String, sub: String?, onPick: () -> Unit, onDelete: (() -> Unit)? = null) {
    if (onDelete == null) {
        BaseRow(title, sub, onClick = onPick, leading = { RadioButton(selected = selected, onClick = null) })
    } else {
        BaseRow(
            title, sub, onClick = onPick,
            leading = { RadioButton(selected = selected, onClick = null) },
            trailing = { TextButton(onDelete) { Text("Удалить") } }
        )
    }
}

@Composable
fun ServerSheet(a: ComposeActivity, s: Snap, sid: String, onClose: () -> Unit) {
    val v = s.servers.firstOrNull { it.id == sid }
    if (v == null) {
        SheetTitle("Сервер удалён")
        return
    }
    val isWg = Regex("wireguard|amnezia", RegexOption.IGNORE_CASE).containsMatchIn(v.proto)
    val eng = v.core
    val rename = remember { mutableStateOf(false) }
    if (rename.value) {
        RenameDialog("Название сервера", v.name, { rename.value = false }) { n ->
            a.call("renameServer", JSONObject().put("id", sid).put("name", n))
            rename.value = false
        }
    }
    fun set(core: String, ver: String) {
        a.call(
            "corePlugin",
            JSONObject().put("id", sid).put("op", "server").put("core", core).put("ver", ver)
        ) { if (s.selected == sid) a.reapplyIfOn() }
    }
    SheetTitle(v.name, v.proto + " · " + v.endpoint)
    if (v.blocked != null) {
        Text(v.blocked, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
    }
    Text(if (isWg) "Движок WireGuard" else "Ядро", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    if (isWg) {
        Text(
            "Авто: обычный WireGuard идёт через отдельный движок WireGuard, конфиги с параметрами Amnezia через AmneziaWG. Xray нужен только если выбрать его вручную.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    ChoiceRow(
        "", if (isWg) listOf("Авто", "WireGuard", "AmneziaWG", "Xray") else listOf("Авто", "sing-box", "Xray"),
        if (isWg) when (eng) { "" -> 0; "wg" -> 1; "awg" -> 2; else -> 3 } else when (eng) { "" -> 0; "xray" -> 2; else -> 1 }
    ) { i -> set(if (isWg) (when (i) { 0 -> ""; 1 -> "wg"; 2 -> "awg"; else -> "xray" }) else (when (i) { 0 -> ""; 1 -> "singbox"; else -> "xray" }), "") }
    if (eng == "xray") {
        Text("Версия Xray", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        VerRow(v.ver.isEmpty(), "Активная версия", if (s.xrayActive.isNotEmpty()) "Выбрана в настройках" else "Встроенное", onPick = { set(eng, "") })
        VerRow(v.ver == "builtin", "Встроенное", if (s.xrayBuiltin.isNotEmpty()) "Xray " + s.xrayBuiltin else null, onPick = { set(eng, "builtin") })
        for (c in s.xrayList) VerRow(v.ver == c.id, c.label, if (c.version.isNotEmpty()) "Xray " + c.version else null, { set(eng, c.id) })
    } else if (!isWg) {
        Text("Версия sing-box", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        VerRow(
            v.ver.isEmpty(), "Активная версия",
            if (s.sbActive.isNotEmpty()) "Выбрана в настройках" else if (s.sbBuiltin) "Встроенное" else "Нет ядра",
            onPick = { set(eng, "") }
        )
        if (s.sbBuiltin) VerRow(v.ver == "builtin", "Встроенное", "Идёт в комплекте", onPick = { set(eng, "builtin") })
        for (c in s.sbList) VerRow(v.ver == c.id, c.label, if (c.version.isNotEmpty()) "sing-box " + c.version else null, { set(eng, c.id) })
        Text("Скачать версии: Настройки, Ядро, sing-box", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton({ rename.value = true }) { Text("Имя") }
        FilledTonalButton({ a.call("pingOne", sid); onClose() }) { Text("Пинг") }
        FilledTonalButton({ a.call("remove", sid); onClose() }) { Text("Удалить") }
    }
}

@Composable
fun GroupSheet(a: ComposeActivity, name: String, local: String, onClose: () -> Unit) {
    val rename = remember { mutableStateOf(false) }
    SheetTitle(if (name.isEmpty()) local.ifEmpty { "Мои серверы" } else name)
    BaseRow("Проверить пинг", null, { if (name.isEmpty()) a.call("ping") else a.call("pingGroup", name); onClose() })
    if (name.isEmpty()) BaseRow("Переименовать", "Только название группы", { rename.value = true })
    if (name.isNotEmpty()) {
        BaseRow("Обновить подписку", null, {
            a.say("Обновляю…")
            a.call("refreshGroup", name) { r -> val e = r.optString("error"); a.say(if (e.isNotEmpty()) e else "Обновлено") }
            onClose()
        })
        BaseRow("Переименовать", null, { rename.value = true })
        BaseRow("Очистить группу", "Удалить серверы, оставить подписку", { a.call("clearGroup", name); onClose() })
        BaseRow("Удалить группу", "Вместе с серверами", { a.call("removeGroup", name); onClose() })
    }
    if (rename.value) {
        RenameDialog("Название группы", name.ifEmpty { local.ifEmpty { "Мои серверы" } }, { rename.value = false }) { n ->
            if (name.isEmpty()) {
                a.set("localName", n.trim())
                rename.value = false
                onClose()
                return@RenameDialog
            }
            a.call("renameGroup", JSONObject().put("old", name).put("name", n)) { r ->
                val e = r.optString("error")
                if (e.isNotEmpty()) a.say(e)
            }
            rename.value = false
            onClose()
        }
    }
}

@Composable
fun SbSheet(a: ComposeActivity, s: Snap) {
    val open = remember { mutableStateOf(false) }
    SheetTitle("Ядро sing-box", "Активная версия используется для всех серверов, у которых не выбрана своя (долгое нажатие на сервер).")
    BusyLine(s.xrayBusy)
    if (s.sbBuiltin) VerRow(s.sbActive.isEmpty(), "Встроенное", "Идёт в комплекте с приложением", onPick = { a.call("selectSb", "") })
    else if (s.sbList.isEmpty()) Text("Встроенного ядра в этой сборке нет, скачайте версию ниже.", style = MaterialTheme.typography.bodyMedium)
    for (c in s.sbList) VerRow(s.sbActive == c.id, c.label, if (c.version.isNotEmpty()) "sing-box " + c.version else null, { a.call("selectSb", c.id) }, { a.call("deleteSb", c.id) })
    Spacer(Modifier.height(8.dp))
    FilledTonalButton({
        open.value = !open.value
        if (open.value && s.sbReleases == null) a.call("loadSbReleases") { r -> val e = r.optString("error"); if (e.isNotEmpty()) a.say(e) }
    }, enabled = s.xrayBusy == null) { Text("Скачать версию") }
    if (open.value) {
        val rel = s.sbReleases
        if (rel == null) Text("Загружаю…", style = MaterialTheme.typography.bodyMedium)
        else for (r in rel) BaseRow(r.tag, mb(r.size), trailing = {
            TextButton({
                a.call("downloadSb", r.tag) { x -> val e = x.optString("error"); a.say(if (e.isNotEmpty()) e else "Ядро ${r.tag} установлено") }
            }, enabled = s.xrayBusy == null) { Text("Скачать") }
        })
    }
}

@Composable
fun XraySheet(a: ComposeActivity, s: Snap) {
    val open = remember { mutableStateOf(false) }
    SheetTitle("Ядро Xray", "Своё ядро запускается отдельным процессом. Нужен исполняемый файл Xray для Android (arm64) или zip с ним. Применяется при следующем подключении.")
    BusyLine(s.xrayBusy)
    VerRow(s.xrayActive.isEmpty(), "Встроенное", if (s.xrayBuiltin.isNotEmpty()) "Xray " + s.xrayBuiltin else "Идёт в комплекте с приложением", onPick = { a.call("selectCore", "") })
    for (c in s.xrayList) VerRow(s.xrayActive == c.id, c.label, if (c.version.isNotEmpty()) "Xray " + c.version else null, { a.call("selectCore", c.id) }, { a.call("deleteCore", c.id) })
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton({
            a.call("pickCore") { r ->
                if (!r.optBoolean("cancel")) {
                    val e = r.optString("error")
                    a.say(if (e.isNotEmpty()) e else "Ядро добавлено")
                }
            }
        }, enabled = s.xrayBusy == null) { Text("Из файла") }
        FilledTonalButton({
            open.value = !open.value
            if (open.value && s.xrayReleases == null) a.call("loadReleases") { r -> val e = r.optString("error"); if (e.isNotEmpty()) a.say(e) }
        }, enabled = s.xrayBusy == null) { Text("Скачать версию") }
    }
    if (open.value) {
        val rel = s.xrayReleases
        if (rel == null) Text("Список версий загружается…", style = MaterialTheme.typography.bodyMedium)
        else for (r in rel) BaseRow(r.tag, mb(r.size), trailing = {
            TextButton({
                a.call("downloadCore", r.tag) { x -> val e = x.optString("error"); a.say(if (e.isNotEmpty()) e else "Ядро ${r.tag} установлено") }
            }, enabled = s.xrayBusy == null) { Text("Скачать") }
        })
    }
}

@Composable
fun PluginSheet(a: ComposeActivity, s: Snap, id: String, onSwitch: (Sheet) -> Unit) {
    val p = s.plugins.firstOrNull { it.id == id }
    if (p == null) {
        SheetTitle("Плагин не найден")
        return
    }
    SheetTitle(p.name, p.description)
    if (!p.present) {
        Text("Нет в этой сборке", color = MaterialTheme.colorScheme.error)
        if (p.id == "xray") {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton({
                a.say("Скачиваю Xray…")
                a.call("corePlugin", JSONObject().put("id", "xray").put("op", "restore")) { r ->
                    val e = r.optString("error")
                    if (e.isNotEmpty()) a.say(e)
                }
            }) { Text("Скачать") }
        }
        return
    }
    val cores = s.plugins.filter { it.core }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton({
            if (p.core && p.enabled && cores.none { it.id != p.id && it.present && it.enabled }) {
                a.say("Нужно хотя бы одно ядро")
            } else {
                a.call("corePlugin", JSONObject().put("id", p.id).put("op", "enable").put("value", !p.enabled)) { a.reapplyIfOn() }
            }
        }) { Text(if (p.enabled) "Выключить" else "Включить") }
        if (p.core && p.enabled && !p.primary) {
            FilledTonalButton({
                a.call("corePlugin", JSONObject().put("id", p.id).put("op", "switch")) { a.reapplyIfOn() }
            }) { Text("Сделать основным") }
        }
    }
    if (p.id == "xray") {
        Spacer(Modifier.height(8.dp))
        TextButton({ onSwitch(Sheet.XrayCores) }) { Text("Версии ядра") }
    }
    if (p.id == "wireguard" || p.id == "awg") {
        Spacer(Modifier.height(8.dp))
        ChoiceRow("Движок WireGuard", listOf("Авто", "AmneziaWG", "WireGuard"), s.wgMode) { a.call("awgMode", it) }
    }
    if (p.id == "dpi") {
        Spacer(Modifier.height(8.dp))
        Text(s.dpiLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton({ a.call("dpiSearch"); a.say("Подбор начался, это может занять до 5 минут и идёт в фоне") }) { Text("Подобрать заново") }
    }
    if (p.id == "singbox" && p.present) {
        Spacer(Modifier.height(8.dp))
        TextButton({ onSwitch(Sheet.SbCores) }) { Text("Версии ядра") }
    }
}

@Composable
fun HwidSheet(a: ComposeActivity, onClose: () -> Unit) {
    val hwid = remember { mutableStateOf("…") }
    val os = remember { mutableStateOf("") }
    val ver = remember { mutableStateOf("") }
    val model = remember { mutableStateOf("") }
    remember {
        a.call("hwidInfo") { r ->
            hwid.value = r.optString("hwid").ifEmpty { "—" }
            os.value = r.optString("os")
            ver.value = r.optString("ver")
            model.value = r.optString("model")
        }
        0
    }
    SheetTitle("HWID и устройство", "Отправляется провайдеру при загрузке подписки. После сброса HWID у подписки может понадобиться заново привязать устройство.")
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Text(hwid.value, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(os.value, { os.value = it }, label = { Text("Операционная система") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(ver.value, { ver.value = it }, label = { Text("Версия ОС") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(model.value, { model.value = it }, label = { Text("Имя устройства") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton({
            a.call("hwidDefaults")
            a.call("hwidInfo") { r -> os.value = r.optString("os"); ver.value = r.optString("ver"); model.value = r.optString("model") }
            a.say("Значения устройства сброшены")
        }) { Text("По умолчанию") }
        TextButton({
            a.call("hwidReset") { r -> hwid.value = r.optString("hwid").ifEmpty { "—" }; a.say("HWID сброшен") }
        }) { Text("Сбросить HWID") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton({ a.call("copy", hwid.value); a.say("HWID скопирован") }) { Text("Копировать") }
        Button({
            a.call("hwidSet", JSONObject().put("os", os.value.trim()).put("ver", ver.value.trim()).put("model", model.value.trim()))
            onClose()
        }) { Text("Сохранить") }
    }
}

@Composable
fun AddPluginSheet(a: ComposeActivity, onClose: () -> Unit, onSwitch: (Sheet) -> Unit) {
    SheetTitle("Добавить плагин", "Плагины выполняют код, ставьте только из доверенных источников.")
    BaseRow("Из файла", "Выбрать .zip с plugin.json", {
        onClose()
        a.call("pickPlugin") { r ->
            if (!r.optBoolean("cancel")) {
                val e = r.optString("error")
                a.say(if (e.isNotEmpty()) e else "Плагин установлен")
            }
        }
    })
    BaseRow("По ссылке", "Прямая ссылка https на .zip", { onSwitch(Sheet.PluginUrl) })
    BaseRow("Каталог", "Готовые плагины из каталога", {
        a.call("loadCatalog") { r -> val e = r.optString("error"); if (e.isNotEmpty()) a.say(e) }
        onSwitch(Sheet.Catalog)
    })
}

@Composable
fun PluginUrlSheet(a: ComposeActivity, onClose: () -> Unit) {
    val url = remember { mutableStateOf("") }
    SheetTitle("Плагин по ссылке", "Прямая ссылка https на .zip с plugin.json")
    OutlinedTextField(
        url.value, { url.value = it }, singleLine = true,
        placeholder = { Text("https://…/plugin.zip") }, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClose) { Text("Отмена") }
        Button({
            val u = url.value.trim()
            if (u.isNotEmpty()) {
                a.say("Скачиваю…")
                a.call("installPluginUrl", JSONObject().put("url", u).put("select", false)) { r ->
                    val e = r.optString("error")
                    a.say(if (e.isNotEmpty()) e else "Плагин установлен")
                }
                onClose()
            }
        }, enabled = url.value.isNotBlank()) { Text("Установить") }
    }
}

@Composable
fun CatalogSheet(a: ComposeActivity, s: Snap) {
    SheetTitle("Каталог плагинов")
    BusyLine(s.plugBusy)
    val cat = s.catalog
    if (cat == null) {
        Text("Загружаю…", style = MaterialTheme.typography.bodyMedium)
    } else if (cat.isEmpty()) {
        Text("Каталог пуст", style = MaterialTheme.typography.bodyMedium)
    } else {
        for (f in cat) {
            Text(f.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
            for (v in f.versions.take(4)) {
                BaseRow(
                    f.name + " " + v.version,
                    (if (v.size > 0) mb(v.size) else "") + (if (v.notes.isNotEmpty()) " · " + v.notes else ""),
                    trailing = {
                        TextButton({
                            a.say("Скачиваю…")
                            a.call("installPluginUrl", JSONObject().put("url", v.url).put("select", false)) { r ->
                                val e = r.optString("error")
                                a.say(if (e.isNotEmpty()) e else "Плагин установлен")
                            }
                        }, enabled = s.plugBusy == null) { Text("Поставить") }
                    }
                )
            }
        }
    }
}

@Composable
fun PluginSettingsSheet(a: ComposeActivity, s: Snap, key: String, onClose: () -> Unit) {
    val p = s.installed.firstOrNull { it.key == key }
    if (p == null) {
        SheetTitle("Плагин не найден")
        return
    }
    SheetTitle(p.name, p.description + " v" + p.version)
    for (f in p.schema) {
        val cur = p.values[f.key]
        fun put(v: Any?) = a.call("pluginSet", JSONObject().put("key", p.key).put("name", f.key).put("value", v ?: JSONObject.NULL))
        when (f.type) {
            "switch" -> SwitchRow(f.title, f.sub.ifEmpty { null }, cur == true) { put(it) }
            "select" -> ChoiceRow(f.title, f.options, (cur as? Number)?.toInt() ?: 0) { put(it) }
            "text" -> TextRow(f.title, cur?.toString() ?: "", f.placeholder, false) { put(it) }
            else -> SliderRow(
                f.title, ((cur as? Number)?.toFloat() ?: f.min), f.min, f.max,
                if (f.step > 0f) (((f.max - f.min) / f.step).toInt() - 1).coerceAtLeast(0) else 0,
                { if (f.step >= 1f) it.toInt().toString() else String.format("%.2f", it) }
            ) { put(it.toDouble()) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClose) { Text("Готово") } }
}

@Composable
fun IconSheet(a: ComposeActivity, s: Snap) {
    SheetTitle("Иконка приложения", "На Android 13+ включите «Тематические значки» в настройках обоев. Смена значка может занять несколько секунд.")
    for (v in s.icons) VerRow(s.iconCurrent == v.id, v.name, v.note, onPick = { a.call("setIcon", v.id) })
    Spacer(Modifier.height(8.dp))
    FilledTonalButton({
        a.call("pickIcon") { r ->
            if (!r.optBoolean("cancel")) {
                val e = r.optString("error")
                a.say(if (e.isNotEmpty()) e else "Подтвердите добавление значка на главный экран")
            }
        }
    }) { Text("Своя картинка") }
}
