package tunn.client

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppItem(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun AppPickerSheet(a: ComposeActivity, s: Snap) {
    val ctx = LocalContext.current
    val items = remember { mutableStateOf<List<AppItem>?>(null) }
    val query = remember { mutableStateOf("") }
    val chosen = remember { mutableStateOf(s.apps.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toSet()) }
    LaunchedEffect(Unit) {
        items.value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) return@mapNotNull null
                val icon = try {
                    pm.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap()
                } catch (e: Throwable) {
                    null
                }
                AppItem(pkg, ri.loadLabel(pm).toString(), icon)
            }.sortedBy { it.label.lowercase() }
        }
    }
    SheetTitle(if (s.appMode == 1) "Исключить из VPN" else "Только через VPN", "Выбрано: ${chosen.value.size}")
    OutlinedTextField(query.value, { query.value = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    val list = items.value
    if (list == null) {
        CircularProgressIndicator(Modifier.padding(24.dp))
        return
    }
    val q = query.value.trim().lowercase()
    val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
        .sortedByDescending { it.pkg in chosen.value }
    for (it in shown) {
        val on = it.pkg in chosen.value
        Row(
            Modifier.fillMaxWidth().clickable {
                chosen.value = if (on) chosen.value - it.pkg else chosen.value + it.pkg
                a.set("tuning.apps", chosen.value.joinToString(","))
            }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (it.icon != null) Image(it.icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(it.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(it.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(on, null)
        }
    }
}
