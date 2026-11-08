package tunn.client

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LiquidChoice(title: String, options: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val n = options.size
    val density = LocalDensity.current
    var rowW by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    val cell = if (rowW > 0) rowW / n.toFloat() else 1f
    val x = remember { Animatable(0f) }
    LaunchedEffect(selected, rowW) {
        val target = selected * cell
        if (rowW == 0) return@LaunchedEffect
        if (x.value == 0f && selected != 0 && !x.isRunning) x.snapTo(target)
        else x.animateTo(target, spring(dampingRatio = 0.55f, stiffness = 260f))
    }
    val lift by animateDpAsState(if (pressed) 7.dp else 0.dp, spring(0.6f, 500f), label = "lift")
    val stretchPx = (abs(x.targetValue - x.value) / cell).coerceAtMost(1.2f) * cell * 0.30f
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp).alpha(if (enabled) 1f else 0.45f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        fun indexAt(px: Float) = (px / cell).toInt().coerceIn(0, n - 1)
        Box(
            Modifier.fillMaxWidth().height(46.dp).glass(RoundedCornerShape(23.dp)).onSizeChanged { rowW = it.width }
                .pointerInput(n, enabled) {
                    detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { if (enabled) onSelect(indexAt(it.x)) })
                }
                .pointerInput(n, enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { pressed = true },
                        onDragEnd = { pressed = false },
                        onDragCancel = { pressed = false },
                        onHorizontalDrag = { change, _ -> if (enabled) onSelect(indexAt(change.position.x)) }
                    )
                }
        ) {
            val dropW = with(density) { (cell + stretchPx).toDp() } - 6.dp
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((x.value - stretchPx / 2f + 3.dp.toPx()).roundToInt(), 0) }
                    .width(dropW.coerceAtLeast(8.dp)).height(38.dp + lift)
                    .glassDrop(RoundedCornerShape(50))
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, o ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            o, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val prog = remember { Animatable(if (checked) 1f else 0f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(checked) { prog.animateTo(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f)) }
    val grow by animateDpAsState(if (pressed) 10.dp else 0.dp, spring(0.6f, 500f), label = "grow")
    val travel = abs(prog.targetValue - prog.value).coerceAtMost(1f)
    val trackW = 64.dp
    val trackH = 28.dp
    val thumbW = 38.dp + grow + 8.dp * travel
    val thumbH = 24.dp + grow * 0.4f
    val dark = LocalGlass.current.dark
    val off = if (dark) Color(0xFF3A3A3F) else Color(0xFFE4E4E8)
    val on = Color(0xFF34C759)
    val trackColor = lerp(off, on, prog.value.coerceIn(0f, 1f))
    Box(
        Modifier.size(trackW, trackH)
            .drawBehind {
                drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent), endY = size.height * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
            }
            .pointerInput(checked, enabled) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { if (enabled) onChange(!checked) })
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val free = trackW - thumbW - 4.dp
        val a = if (pressed) 0.86f else 0.98f
        Box(
            Modifier.offset(x = 2.dp + free * prog.value).width(thumbW).height(thumbH)
                .shadow(5.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x66000000))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color(0xFFF0F0F4).copy(alpha = a))),
                    CircleShape
                )
                .border(0.6.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
        )
    }
}


@Composable
fun FluentSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val prog = remember { Animatable(if (checked) 1f else 0f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(checked) { prog.animateTo(if (checked) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 700f)) }
    val knob by animateDpAsState(if (pressed) 16.dp else 12.dp, spring(0.8f, 700f), label = "knob")
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val onKnob = MaterialTheme.colorScheme.onPrimary
    val p = prog.value.coerceIn(0f, 1f)
    Box(
        Modifier.size(44.dp, 22.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(lerp(Color.Transparent, accent, p), CircleShape)
            .border(1.dp, lerp(outline, accent, p), CircleShape)
            .pointerInput(checked, enabled) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { if (enabled) onChange(!checked) })
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val free = 44.dp - knob - 8.dp
        Box(
            Modifier.offset(x = 4.dp + free * p).size(knob)
                .background(lerp(outline, onKnob, p), CircleShape)
        )
    }
}

@Composable
fun StartSwitchCard(status: String, title: String, server: String, onToggle: () -> Unit) {
    val busy = status == "Connecting" || status == "Stopping"
    val on = status == "On" || status == "Connecting"
    val sh = RoundedCornerShape(cr(28))
    val glassOn = LocalGlass.current.on
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).glass(sh)
            .background(if (glassOn) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer, sh)
            .padding(horizontal = 22.dp, vertical = sp(22)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(server, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Box(Modifier.padding(start = 12.dp).graphicsLayer(scaleX = 1.45f, scaleY = 1.45f).padding(end = 6.dp)) {
            when (LocalUi.current.sw) {
                1 -> LiquidSwitch(on, !busy) { onToggle() }
                2 -> FluentSwitch(on, !busy) { onToggle() }
                else -> androidx.compose.material3.Switch(checked = on, enabled = !busy, onCheckedChange = { onToggle() })
            }
        }
    }
}
