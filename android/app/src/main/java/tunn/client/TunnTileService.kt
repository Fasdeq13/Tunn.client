package tunn.client

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TunnTileService : TileService() {

    override fun onStartListening() {
        TunnApp.state.syncExternal()
        refresh()
    }

    override fun onClick() {
        val st = TunnApp.state
        when (st.status) {
            Status.On -> st.disconnect()
            Status.Off -> {
                if (st.selected == null || VpnService.prepare(this) != null) openApp() else st.connect()
            }
            else -> Unit
        }
        refresh()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val st = TunnApp.state
        tile.state = when (st.status) {
            Status.On -> Tile.STATE_ACTIVE
            Status.Off -> Tile.STATE_INACTIVE
            else -> Tile.STATE_UNAVAILABLE
        }
        tile.label = "Tunn"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_power)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (st.status) {
                Status.On -> "Подключено"
                Status.Connecting -> "Подключение…"
                Status.Stopping -> "Отключение…"
                Status.Off -> "Отключено"
            }
        }
        tile.updateTile()
    }
}
