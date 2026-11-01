package app.fasdeq.vpn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONObject

abstract class BaseUiActivity : ComponentActivity() {
    open fun applyBars(dark: Boolean) {}

    open fun afterVpnPermission() {}

    open fun onPushed(json: String) {}

    open fun onReply(id: Int, value: JSONObject) {}

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            askBattery()
            FasdeqApp.state.connect()
        }
        afterVpnPermission()
    }

    private var corePicked: ((Uri?) -> Unit)? = null

    private val corePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val f = corePicked
        corePicked = null
        f?.invoke(uri)
    }

    private var pluginPicked: ((Uri?) -> Unit)? = null

    private val pluginPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val f = pluginPicked
        pluginPicked = null
        f?.invoke(uri)
    }

    fun pickPlugin(done: (Uri?) -> Unit) {
        pluginPicked = done
        pluginPicker.launch(arrayOf("*/*"))
    }

    private var imagePicked: ((Uri?) -> Unit)? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val f = imagePicked
        imagePicked = null
        f?.invoke(uri)
    }

    fun pickImage(done: (Uri?) -> Unit) {
        imagePicked = done
        imagePicker.launch(arrayOf("image/*"))
    }

    fun pickCore(done: (Uri?) -> Unit) {
        corePicked = done
        corePicker.launch(arrayOf("*/*"))
    }

    private val batteryAsk = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun requestConnect() {
        val st = FasdeqApp.state
        when (st.status) {
            Status.Off -> {
                if (st.selected == null) {
                    st.error = "Сначала добавьте сервер"
                    return
                }
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermission.launch(intent)
                } else {
                    askBattery()
                    st.connect()
                }
            }
            else -> Unit
        }
    }

    protected fun askBattery() {
        val prefs = getSharedPreferences("fasdeq", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_battery", false)) return
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        prefs.edit().putBoolean("asked_battery", true).apply()
        try {
            batteryAsk.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
        }
    }

    protected fun askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("fasdeq", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
