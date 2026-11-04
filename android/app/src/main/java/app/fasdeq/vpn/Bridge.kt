package app.fasdeq.vpn

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object VpnBridge {
    const val ACTION_STATE = "app.fasdeq.vpn.STATE"

    private fun file(context: Context) = File(context.filesDir, "vpn_state")

    fun write(context: Context, up: Boolean, id: String?) {
        try {
            file(context).writeText(if (up) "1\n" + (id ?: "") else "0\n")
        } catch (e: Throwable) {
        }
    }

    fun read(context: Context): Pair<Boolean, String?> {
        return try {
            val lines = file(context).readText().lines()
            Pair(lines.firstOrNull() == "1", lines.getOrNull(1)?.ifEmpty { null })
        } catch (e: Throwable) {
            Pair(false, null)
        }
    }

    fun vpnNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            cm.allNetworks.any {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (e: Throwable) {
            false
        }
    }

    fun serviceRunning(context: Context): Boolean {
        return try {
            val am = context.getSystemService(ActivityManager::class.java)
            @Suppress("DEPRECATION")
            am.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == FasdeqVpnService::class.java.name
            }
        } catch (e: Throwable) {
            false
        }
    }

    fun live(context: Context): Boolean = vpnNetwork(context)

    fun send(context: Context, up: Boolean, message: String?) {
        try {
            context.sendBroadcast(
                Intent(ACTION_STATE)
                    .setPackage(context.packageName)
                    .putExtra("up", up)
                    .putExtra("msg", message)
            )
        } catch (e: Throwable) {
        }
    }
}

object SharedStore {
    private fun file(context: Context) = File(context.filesDir, "shared.json")

    fun read(context: Context): JSONObject? {
        return try {
            val f = file(context)
            if (f.exists()) JSONObject(f.readText()) else null
        } catch (e: Throwable) {
            null
        }
    }

    fun write(context: Context, servers: JSONArray, groups: JSONArray, selected: String?) {
        try {
            val f = file(context)
            val tmp = File(context.filesDir, "shared.json.tmp")
            tmp.writeText(
                JSONObject()
                    .put("servers", servers)
                    .put("groups", groups)
                    .put("selected", selected ?: JSONObject.NULL)
                    .toString()
            )
            tmp.renameTo(f)
        } catch (e: Throwable) {
        }
    }
}
