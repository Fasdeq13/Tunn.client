package app.fasdeq.vpn

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

object Components {
    const val SINGBOX = "singbox"
    const val XRAY = "xray"
    const val AWG = "awg"

    private const val AWG_AAR = "https://repo1.maven.org/maven2/com/zaneschepke/amneziawg-android/2.3.7/amneziawg-android-2.3.7.aar"

    val dist: String get() = BuildConfig.DIST
    val full: Boolean get() = dist == "full"

    private fun prefs(c: Context) = c.getSharedPreferences("components", Context.MODE_PRIVATE)

    fun setupDone(c: Context): Boolean = dist != "netinstall" || prefs(c).getBoolean("setup_done", false)

    fun markSetupDone(c: Context) {
        prefs(c).edit().putBoolean("setup_done", true).apply()
    }

    private fun awgDir(c: Context) = File(c.filesDir, "awg/" + Cores.abi())

    fun awgFile(c: Context): File = File(awgDir(c), "libam-go.so")

    fun has(c: Context, id: String): Boolean = when (id) {
        SINGBOX -> File(c.applicationInfo.nativeLibraryDir, "libsingbox.so").exists() || SbCores.list(c).isNotEmpty()
        XRAY -> full || Cores.list(c).isNotEmpty()
        AWG -> awgBundled() || awgFile(c).exists()
        else -> true
    }

    private fun awgBundled(): Boolean = try {
        Class.forName("org.amnezia.awg.GoBackend")
        BuildConfig.DIST == "full"
    } catch (e: Throwable) {
        false
    }

    fun anyCore(c: Context): Boolean = has(c, SINGBOX) || has(c, XRAY)

    fun status(c: Context): JSONObject = JSONObject()
        .put("dist", dist)
        .put("setupDone", setupDone(c))
        .put(SINGBOX, has(c, SINGBOX))
        .put(XRAY, has(c, XRAY))
        .put(AWG, has(c, AWG))

    fun installAwg(c: Context) {
        val abi = Cores.abi()
        val aar = Net.get(AWG_AAR, "application/octet-stream", 120_000)
        var found: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(aar)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (n.startsWith("jni/$abi/") && n.endsWith(".so") && (n.contains("am-go") || n.contains("wg-go"))) {
                    found = z.readBytes()
                    break
                }
            }
        }
        val bytes = found ?: throw IllegalStateException("В библиотеке AmneziaWG нет сборки для $abi")
        val dir = awgDir(c)
        dir.mkdirs()
        val tmp = File(dir, "libam-go.so.part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(awgFile(c))) throw IllegalStateException("Не удалось сохранить библиотеку AmneziaWG")
    }

    fun removeAwg(c: Context) {
        awgFile(c).delete()
    }
}
