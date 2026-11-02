@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.fasdeq.vpn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
fun BaseRow(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null || onLong != null) {
        m = m.combinedClickable(enabled = enabled, onClick = { onClick?.invoke() }, onLongClick = onLong)
    }
    Row(
        m.padding(horizontal = 16.dp, vertical = 12.dp).alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    BaseRow(
        title, sub, enabled = enabled,
        onClick = { onChange(!checked) },
        trailing = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = {
                onChange(it)
            })
        }
    )
}

@Composable
fun ChoiceRow(title: String, options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).alpha(if (enabled) 1f else 0.45f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o ->
                FilterChip(selected = i == selected, onClick = { if (enabled) onSelect(i) }, label = { Text(o) })
            }
        }
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Int,
    fmt: (Float) -> String,
    enabled: Boolean = true,
    onDone: (Float) -> Unit
) {
    val local = remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(fmt(local.value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local.value,
            onValueChange = { local.value = it },
            onValueChangeFinished = { onDone(local.value) },
            valueRange = min..max,
            steps = steps,
            enabled = enabled
        )
    }
}

@Composable
fun TextRow(title: String, value: String, hint: String, singleLine: Boolean = true, onDone: (String) -> Unit) {
    val text = remember(value) { mutableStateOf(value) }
    val changed = remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text.value,
            onValueChange = {
                text.value = it
                changed.value = true
            },
            placeholder = { Text(hint) },
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                changed.value = false
                onDone(text.value)
            }),
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (!it.isFocused && changed.value) {
                    changed.value = false
                    onDone(text.value)
                }
            }
        )
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Spacer(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun Avatar(letter: String, selected: Boolean = false) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = CircleShape, color = bg, modifier = Modifier.size(42.dp)) {
        Row(Modifier.size(42.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(letter, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

fun pingColor(ms: Int?): Color = when {
    ms == null -> Color(0xFF8A9390)
    ms < 150 -> Color(0xFF4CAF7A)
    ms < 300 -> Color(0xFFE0A52E)
    else -> Color(0xFFD9645A)
}

@Composable
fun PingChip(p: PingUi?) {
    val ms = p?.ms
    val c = pingColor(ms)
    val label = when {
        p == null -> "—"
        p.busy -> "…"
        ms == null -> "—"
        else -> "$ms мс"
    }
    Surface(shape = RoundedCornerShape(10.dp), color = c.copy(alpha = 0.16f)) {
        Text(
            label,
            color = c,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

fun checkColor(ms: Int?, busy: Boolean): Color = when {
    busy -> Color(0xFFE0A52E)
    ms == null || ms == -3 || ms == -4 -> Color(0xFF8A9390)
    ms == -2 -> Color(0xFFE0A52E)
    ms > 0 -> Color(0xFF4CAF7A)
    else -> Color(0xFFD9645A)
}

@Composable
fun BusyLine(text: String?) {
    if (text == null) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ClickableText(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp)
    )
}

private const val TG_PATH = "M21.6 3.4 2.7 10.7c-.9.4-.9 1 0 1.3l4.8 1.5 1.8 5.6c.2.6.4.8.9.8s.6-.2.9-.5l2.3-2.2 4.7 3.5c.9.5 1.5.2 1.7-.8l3-14.3c.3-1.2-.4-1.8-1.2-1.2zM8.5 13.2l9.1-5.7c.4-.3.8-.1.5.2l-7.5 6.8-.3 3.1z"

@Composable
fun ServiceIcon(index: Int, color: Color, size: Int) {
    val hole = MaterialTheme.colorScheme.surfaceContainer
    androidx.compose.foundation.Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        when (index) {
            0 -> {
                val body = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.16f, h * 0.22f)
                    quadraticBezierTo(w * 0.5f, h * 0.06f, w * 0.84f, h * 0.22f)
                    quadraticBezierTo(w * 0.98f, h * 0.55f, w * 0.92f, h * 0.84f)
                    quadraticBezierTo(w * 0.5f, h * 1.0f, w * 0.08f, h * 0.84f)
                    quadraticBezierTo(w * 0.02f, h * 0.55f, w * 0.16f, h * 0.22f)
                    close()
                }
                drawPath(body, color)
                drawCircle(hole, w * 0.1f, androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.55f))
                drawCircle(hole, w * 0.1f, androidx.compose.ui.geometry.Offset(w * 0.66f, h * 0.55f))
            }
            1 -> {
                val path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(TG_PATH).toPath()
                path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                scale(w / 24f, h / 24f, androidx.compose.ui.geometry.Offset.Zero) {
                    drawPath(path, color)
                }
            }
            else -> {
                drawRoundRect(
                    color,
                    androidx.compose.ui.geometry.Offset(0f, h * 0.17f),
                    androidx.compose.ui.geometry.Size(w, h * 0.66f),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.22f, w * 0.22f)
                )
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.40f, h * 0.35f)
                    lineTo(w * 0.40f, h * 0.65f)
                    lineTo(w * 0.66f, h * 0.5f)
                    close()
                }
                drawPath(tri, hole)
            }
        }
    }
}

@Composable
fun Dim(active: Boolean, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.alpha(if (active) 1f else 0.4f)) {
        content()
        if (!active) {
            androidx.compose.foundation.layout.Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                }
            )
        }
    }
}
