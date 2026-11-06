@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.fasdeq.vpn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Sheet {
    object Add : Sheet()
    data class Server(val id: String) : Sheet()
    data class Group(val name: String) : Sheet()
    object SbCores : Sheet()
    object XrayCores : Sheet()
    data class Plugin(val id: String) : Sheet()
    object Hwid : Sheet()
    object AddPlugin : Sheet()
    object PluginUrl : Sheet()
    object Catalog : Sheet()
    data class PluginSettings(val key: String) : Sheet()
    object IconPick : Sheet()
    object Apps : Sheet()
}

private fun parentOf(page: String): String = when (page) {
    "home" -> "home"
    "settings" -> "home"
    else -> "settings"
}

@Composable
fun AppRoot(a: ComposeActivity) {
    val s = a.snap.value
    TunnTheme(s) {
        val page = rememberSaveable { mutableStateOf("home") }
        val sheet = remember { mutableStateOf<Sheet?>(null) }
        val host = remember { SnackbarHostState() }
        val msg = a.toast.value
        LaunchedEffect(msg) {
            if (msg != null) {
                host.showSnackbar(msg)
                a.toast.value = null
            }
        }
        BackHandler(enabled = page.value != "home") { page.value = parentOf(page.value) }
        val wide = LocalConfiguration.current.screenWidthDp >= 720
        val go: (String) -> Unit = { page.value = it }
        val show: (Sheet) -> Unit = { sheet.value = it }
        if (!s.setupDone) SetupDialog(a, s)
        Scaffold(
            containerColor = if (LocalGlass.current.wall) Color.Transparent else MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            snackbarHost = { SnackbarHost(host, Modifier.navigationBarsPadding()) }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
              if (wide && page.value != "home") {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) { SettingsList(a, s, { go("home") }, go) }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        SettingsPage(a, s, if (page.value == "settings") "look" else page.value, show) { go("home") }
                    }
                }
              } else {
                AnimatedContent(
                    targetState = page.value,
                    transitionSpec = {
                        val deeper = depthOf(targetState) > depthOf(initialState)
                        if (deeper) {
                            (slideInHorizontally(tween(320)) { it / 5 } + fadeIn(tween(320))) togetherWith
                                (slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(320))) togetherWith
                                (slideOutHorizontally(tween(320)) { it / 5 } + fadeOut(tween(200)))
                        }
                    },
                    label = "page"
                ) { p ->
                    when (p) {
                        "home" -> HomeScreen(a, s, show) { go(if (wide) "look" else "settings") }
                        "settings" -> SettingsList(a, s, { go("home") }, go)
                        else -> SettingsPage(a, s, p, show) { go("settings") }
                    }
                }
              }
            }
        }
        SheetHost(a, s, sheet.value, { sheet.value = null }, { sheet.value = it })
    }
}

private fun depthOf(p: String): Int = when (p) {
    "home" -> 0
    "settings" -> 1
    else -> 2
}

private fun cookie(cx: Float, cy: Float, r: Float, amp: Float, lobes: Int, phase: Float, kind: Int = 0): Path {
    val path = Path()
    val steps = 260
    for (i in 0..steps) {
        val t = i.toFloat() / steps * 6.2831855f
        val rr = when (kind) {
            1 -> r
            2 -> {
                val a = t - phase * 0.35f
                val c = abs(cos(a))
                val sn = abs(sin(a))
                r * 1.06f / Math.pow(Math.pow(c.toDouble(), 4.0) + Math.pow(sn.toDouble(), 4.0), 0.25).toFloat()
            }
            3 -> r * (1f + amp * 2.4f * (abs(cos(lobes * (t - phase) / 2f)) - 0.5f))
            else -> r * (1f + amp * cos(lobes * (t - phase)))
        }
        val x = cx + rr * cos(t)
        val y = cy + rr * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
fun PowerButton(status: String, size: androidx.compose.ui.unit.Dp, shape: Int = 0, lobes: Int = 9, ampK: Float = 0.062f, spin: Boolean = true, onClick: () -> Unit) {
    val on = status == "On"
    val busy = status == "Connecting" || status == "Stopping"
    val src = remember { MutableInteractionSource() }
    val pressed = src.collectIsPressedAsState().value
    val press = animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 450f), label = "press").value
    val speed = animateFloatAsState(if (busy) 3.2f else if (on) 0.45f else 0.2f, tween(900), label = "speed")
    val ampBase = animateFloatAsState(if (busy) ampK * 1.4f else if (on) 0.05f else ampK, tween(700), label = "amp")
    val bg = animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        tween(600), label = "bg"
    ).value
    val fg = animateColorAsState(
        if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        tween(600), label = "fg"
    ).value
    val haloColor = MaterialTheme.colorScheme.primary
    val phase = remember { mutableStateOf(0f) }
    val spd = rememberUpdatedState(speed.value)
    val spinOn = rememberUpdatedState(spin)
    val busyOn = rememberUpdatedState(busy)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L && (spinOn.value || busyOn.value)) phase.value += (n - last) / 1_000_000_000f * spd.value
                last = n
            }
        }
    }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(on) {
        if (on) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1100))
        }
    }
    val halo = size + 44.dp
    Box(Modifier.size(halo).graphicsLayer { scaleX = press; scaleY = press }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(halo)) {
            val w = this.size.width
            val c = w / 2f
            val r0 = size.toPx() / 2f * 0.94f
            val ph = phase.value
            val amp = ampBase.value + (if (busy) 0.012f * sin(ph * 3f) else 0.006f * sin(ph * 2f))
            if (on) {
                drawPath(cookie(c, c, r0 * 1.17f, ampK * 1.2f, lobes, -ph * 0.8f, shape), haloColor.copy(alpha = 0.12f))
            }
            val b = burst.value
            if (b < 1f) {
                drawPath(
                    cookie(c, c, r0 * (1f + 0.3f * b), amp, lobes, ph, shape),
                    haloColor.copy(alpha = 0.5f * (1f - b)),
                    style = Stroke(3.dp.toPx())
                )
            }
            drawPath(cookie(c, c, r0, amp, lobes, ph, shape), bg)
            drawCircle(Color.White.copy(alpha = 0.07f), r0 * 0.62f, Offset(c - r0 * 0.12f, c - r0 * 0.16f))
            val ic = r0 * 0.5f
            val sw = ic * 0.17f
            drawArc(
                fg, -55f, 290f, false,
                topLeft = Offset(c - ic / 2f + sw / 2f, c - ic / 2f + sw * 1.4f),
                size = Size(ic - sw, ic - sw * 1.4f - sw / 2f),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawLine(fg, Offset(c, c - ic / 2f), Offset(c, c - ic * 0.04f), sw, StrokeCap.Round)
        }
        Box(Modifier.size(size).clip(CircleShape).clickable(interactionSource = src, indication = null, onClick = onClick))
    }
}

private sealed class Row2 {
    data class Head(val name: String, val count: Int, val group: GroupUi?) : Row2()
    data class Srv(val s: SrvUi) : Row2()
}

private fun fmtDate(sec: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(sec * 1000))

@Composable
fun HomeScreen(a: ComposeActivity, s: Snap, open: (Sheet) -> Unit, goSettings: () -> Unit) {
    val listState = rememberLazyListState()
    val compact = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 80
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val btn = animateDpAsState(if (compact && !wide) 84.dp else s.btnSize.dp, tween(480), label = "btn").value
    val label = when (s.status) {
        "On" -> "Подключено"
        "Connecting" -> "Подключаюсь…"
        "Stopping" -> "Отключаюсь…"
        else -> "Отключено"
    }
    val sel = s.selectedServer()
    val collapsed = remember { mutableStateOf(s.collapsed) }
    val delete = remember { mutableStateOf<SrvUi?>(null) }

    val rows = ArrayList<Row2>()
    val local = s.servers.filter { it.group.isEmpty() }
    val names = ArrayList<String>()
    for (g in s.groups) names.add(g.name)
    for (x in s.servers) if (x.group.isNotEmpty() && !names.contains(x.group)) names.add(x.group)
    fun order(l: List<SrvUi>): List<SrvUi> = if (s.sortPing) l.sortedBy { s.pings[it.id]?.ms ?: Int.MAX_VALUE } else l
    if (local.isNotEmpty()) {
        if (names.isNotEmpty()) rows.add(Row2.Head("", local.size, null))
        if (!collapsed.value.contains("")) for (x in order(local)) rows.add(Row2.Srv(x))
    }
    for (n in names) {
        val list = s.servers.filter { it.group == n }
        rows.add(Row2.Head(n, list.size, s.groups.firstOrNull { it.name == n }))
        if (!collapsed.value.contains(n)) for (x in order(list)) rows.add(Row2.Srv(x))
    }
    val busyPing = s.pings.values.any { it.busy }

    val hero: @Composable () -> Unit = {
    Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 18.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Tunn", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        FilledTonalIconButton(goSettings, Modifier.size(52.dp)) {
            Icon(painterResource(R.drawable.ic_settings), "Настройки")
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (s.t("startStyle", 0f) > 0.5f) {
            StartSwitchCard(s.status, label, sel?.name ?: "Сервер не выбран") {
                when (s.status) {
                    "Off" -> a.requestConnect()
                    "On", "Connecting" -> a.call("disconnect")
                    else -> Unit
                }
            }
        } else {
            PowerButton(s.status, btn, s.btnShape, s.btnLobes, s.btnAmp, s.btnSpin) {
                when (s.status) {
                    "Off" -> a.requestConnect()
                    "On" -> a.call("disconnect")
                    else -> Unit
                }
            }
            Spacer(Modifier.height(10.dp))
            Crossfade(label, animationSpec = tween(300), label = "status") {
                Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        AnimatedVisibility(!compact, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    sel?.name ?: "Сервер не выбран",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                val err = s.error
                if (err != null && s.status == "Off") {
                    Text(
                        err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                    )
                }
                if (s.update.available) {
                    TextButton({ a.call("open", s.update.url) }) { Text("Доступна версия ${s.update.tag}, скачать") }
                }
            }
        }
    }
    }
    val panelBody: @Composable (Modifier, androidx.compose.ui.graphics.Shape) -> Unit = { panelMod, panelShape ->
    Surface(
        modifier = panelMod.glass(panelShape),
        shape = panelShape,
        color = if (LocalGlass.current.on) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Серверы", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(end = 14.dp), maxLines = 1)
                FilledTonalIconButton({ a.call("checkServices"); a.say("Проверяю сервисы…") }, Modifier.size(42.dp), enabled = !s.checking) {
                    if (s.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_shield), "Сервисы", Modifier.size(22.dp))
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton({ a.call("ping") }, Modifier.size(42.dp), enabled = !busyPing) {
                    if (busyPing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_bolt), "Пинг", Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Button({ open(Sheet.Add) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(42.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить", maxLines = 1, softWrap = false)
                }
            }
            if (s.servers.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Серверов пока нет", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Добавьте ссылку vless://, trojan://, ss://, wireguard://, awg:// или адрес подписки",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp)) {
                    items(rows, key = {
                        when (it) {
                            is Row2.Head -> "h:" + it.name
                            is Row2.Srv -> "s:" + it.s.id
                        }
                    }) { r ->
                        when (r) {
                            is Row2.Head -> GroupHead(r, s.localName, collapsed.value.contains(r.name), Modifier.animateItem(), {
                                val next = HashSet(collapsed.value)
                                if (!next.add(r.name)) next.remove(r.name)
                                collapsed.value = next
                                a.call("setCollapsed", JSONArray(next.toList()))
                            }, { open(Sheet.Group(r.name)) })
                            is Row2.Srv -> ServerRow(a, s, r.s, Modifier.animateItem(), { open(Sheet.Server(r.s.id)) }, { delete.value = r.s })
                        }
                    }
                }
            }
        }
    }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.Top) { hero() }
            panelBody(Modifier.weight(0.58f).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp), RoundedCornerShape(34.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            hero()
            Spacer(Modifier.height(18.dp))
            panelBody(Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
        }
    }
    val d = delete.value
    if (d != null) {
        AlertDialog(
            onDismissRequest = { delete.value = null },
            title = { Text("Удалить сервер?") },
            text = { Text(d.name) },
            confirmButton = { TextButton({ a.call("remove", d.id); delete.value = null }) { Text("Удалить") } },
            dismissButton = { TextButton({ delete.value = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val text = remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text.value, { text.value = it }, singleLine = true) },
        confirmButton = { TextButton({ if (text.value.isNotBlank()) onSave(text.value.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun GroupHead(r: Row2.Head, local: String, folded: Boolean, modifier: Modifier, toggle: () -> Unit, more: () -> Unit) {
    val g = r.group
    val rot = animateFloatAsState(if (folded) -90f else 0f, tween(260), label = "rot").value
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = toggle, onLongClick = more).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.rotate(rot), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (r.name.isEmpty()) local.ifEmpty { "Мои серверы" } else r.name) + " · " + r.count,
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (g != null && (g.total > 0 || g.used > 0 || g.expire > 0)) {
                val parts = ArrayList<String>()
                if (g.total > 0) parts.add(fmtBytes(g.used) + " из " + fmtBytes(g.total)) else if (g.used > 0) parts.add(fmtBytes(g.used))
                if (g.expire > 0) parts.add("до " + fmtDate(g.expire))
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        IconButton(more, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_more), null, Modifier.size(20.dp)) }
    }
}

@Composable
private fun ServerRow(a: ComposeActivity, s: Snap, v: SrvUi, modifier: Modifier, onMore: () -> Unit, onDelete: () -> Unit) {
    val picked = s.selected == v.id
    val blocked = v.blocked != null
    val bg = animateColorAsState(
        if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(300), label = "rowbg"
    ).value
    Box(
        modifier.fillMaxWidth().padding(vertical = if (LocalGlass.current.on) sp(5) else sp(3)).glass(RoundedCornerShape(cr(24))).clip(RoundedCornerShape(cr(24)))
            .background(if (LocalGlass.current.on) (if (picked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) else bg).combinedClickable(
            onClick = {
                a.call("select", v.id) { r ->
                    val e = r.optString("error")
                    if (e.isNotEmpty()) a.say(e)
                }
            },
            onLongClick = onMore
        )
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp).alpha(if (blocked) 0.5f else 1f), verticalAlignment = Alignment.CenterVertically) {
            Avatar(if (picked) "✓" else v.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•", picked)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (blocked) v.blocked.orEmpty() else v.proto + " · " + v.endpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (blocked) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                PingChip(s.pings[v.id])
                val c = s.checks[v.id]
                val pg = s.pings[v.id]
                val dead = pg != null && !pg.busy && pg.ms == null
                if (c != null || dead) {
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until s.serviceNames.size) ServiceIcon(i, if (dead) checkColor(-4, false) else checkColor(c?.ms?.getOrNull(i), c?.busy == true), 14)
                    }
                }
            }
            IconButton(onDelete, Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, "Удалить", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp).widthIn(max = 720.dp)) {
            content()
        }
    }
}

@Composable
fun CategoryRow(icon: Int, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (LocalGlass.current.on) sp(7) else sp(4)).glass(RoundedCornerShape(cr(26))).clip(RoundedCornerShape(cr(24)))
            .background(if (LocalGlass.current.on) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SettingsList(a: ComposeActivity, s: Snap, onBack: () -> Unit, go: (String) -> Unit) {
    SubPage("Настройки", onBack) {
        Spacer(Modifier.height(8.dp))
        CategoryRow(R.drawable.ic_palette, "Оформление", "Тема, цвет, фон, иконка") { go("look") }
        CategoryRow(R.drawable.ic_dns, "Подключение", "DNS, MTU, маршрутизация, приложения") { go("connect") }
        CategoryRow(R.drawable.ic_apps, "Плагины и ядра", "sing-box, Xray, WireGuard, свои плагины") { go("plugins") }
        CategoryRow(R.drawable.ic_apps, "Компоненты", if (s.dist == "full") "sing-box, Xray, AmneziaWG: версии" else "Скачать sing-box, Xray, AmneziaWG") { go("components") }
        CategoryRow(R.drawable.ic_bolt, "Пинг и сервисы", "Способ проверки, автопроверка") { go("ping") }
        CategoryRow(R.drawable.ic_person, "Подписки", "Под кого маскироваться, HWID") { go("subs") }
        if (!BuildConfig.FDROID) CategoryRow(R.drawable.ic_refresh, "Обновления", "Проверка новых версий") { go("updates") }
        CategoryRow(R.drawable.ic_info, "О приложении", "Tunn 3.12, создатели, канал") { go("about") }
    }
}

@Composable
fun SettingsPage(a: ComposeActivity, s: Snap, page: String, open: (Sheet) -> Unit, onBack: () -> Unit) {
    when (page) {
        "look" -> LookPage(a, s, open, onBack)
        "connect" -> ConnectPage(a, s, open, onBack)
        "plugins" -> PluginsPage(a, s, open, onBack)
        "components" -> ComponentsPage(a, s, open, onBack)
        "ping" -> PingPage(a, s, onBack)
        "subs" -> SubsPage(a, s, open, onBack)
        "updates" -> UpdatesPage(a, s, onBack)
        else -> AboutPage(a, onBack)
    }
}
