package tunn.client

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class PackAsset(val name: String, val url: String, val kind: String, val nums: List<Int>)

object UpdatePack {
    private val NAME = Regex("^Tunn-([0-9]+(?:\\.[0-9]+)+)\\.(Update|fix)$")

    fun pick(assets: org.json.JSONArray?, current: String, applied: String): PackAsset? {
        if (assets == null) return null
        val cur = Regex("[0-9]+").findAll(current).map { it.value.toInt() }.toList()
        var best: PackAsset? = null
        for (i in 0 until assets.length()) {
            val o = assets.optJSONObject(i) ?: continue
            val name = o.optString("name")
            val url = o.optString("browser_download_url")
            val m = NAME.matchEntire(name) ?: continue
            val kind = if (m.groupValues[2] == "Update") "update" else "fix"
            val nums = m.groupValues[1].split('.').map { it.toInt() }
            val ok = if (kind == "update") Updates.newer(m.groupValues[1], current)
            else nums.size >= 3 && cur.size >= 2 && nums[0] == cur[0] && nums[1] == cur[1] &&
                Updates.newer(nums.drop(2).joinToString("."), applied)
            if (!ok || url.isEmpty()) continue
            val b = best
            if (b == null || (kind == "update" && b.kind == "fix") || (kind == b.kind && Updates.newer(m.groupValues[1], b.nums.joinToString(".")))) {
                best = PackAsset(name, url, kind, nums)
            }
        }
        return best
    }

    fun appliedFix(c: Context, current: String): String = Layer.serial()

    private fun routes(c: Context): List<Network?> {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val out = ArrayList<Network?>()
        out.add(null)
        try {
            val all = cm.allNetworks.toList()
            val plain = all.filter { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false }
            val vpn = all.filter { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            out.addAll(plain)
            out.addAll(vpn)
        } catch (e: Throwable) {
        }
        return out
    }

    private fun fetch(url: String, net: Network?): ByteArray {
        var u = url
        for (hop in 0..6) {
            val conn = (if (net != null) net.openConnection(URL(u)) else URL(u).openConnection()) as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Tunn")
            try {
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    u = URL(URL(u), conn.getHeaderField("Location") ?: throw IllegalStateException("Пустое перенаправление")).toString()
                    continue
                }
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                return conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("Слишком много перенаправлений")
    }

    fun download(c: Context, url: String): ByteArray {
        var last: Exception? = null
        for (net in routes(c)) {
            try {
                return fetch(url, net)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Нет сети")
    }

    private fun unzip(raw: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(raw)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val b = z.readBytes()
                total += b.size
                require(total < 300_000_000L) { "Пакет слишком большой" }
                out[e.name] = b
            }
        }
        return out
    }

    private fun verify(c: Context, data: ByteArray, sig: ByteArray): Boolean = try {
        val pub = c.assets.open("tunn_pack.pub").use { it.readBytes() }
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pub))
        val v = Signature.getInstance("SHA256withECDSA")
        v.initVerify(key)
        v.update(data)
        v.verify(sig)
    } catch (e: Throwable) {
        false
    }

    fun apply(c: Context, pack: PackAsset, onStep: (String) -> Unit): String? {
        return try {
            onStep("Скачиваю " + pack.name + "…")
            val files = unzip(download(c, pack.url))
            val manifestBytes = files["manifest.json"] ?: return "В пакете нет manifest.json"
            val sig = files["manifest.sig"] ?: return "Пакет не подписан"
            if (!verify(c, manifestBytes, sig)) return "Подпись пакета неверна, пакет отклонён"
            val current = Updates.state.current
            val plan = JSONObject(Native.updatePlan(pack.name, String(manifestBytes, Charsets.UTF_8), current, Layer.SHELL, appliedFix(c, current)))
            if (!plan.optBoolean("ok")) return plan.optString("error", "Пакет отклонён")
            onStep("Проверяю…")
            val list = plan.getJSONArray("files")
            val staged = ArrayList<Pair<String, ByteArray>>()
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val p = o.getString("path")
                val data = files[p] ?: return "Файл $p отсутствует в пакете"
                if (Store.sha256(data) != o.getString("sha256")) return "Контрольная сумма $p не совпала"
                staged.add(p to data)
            }
            onStep("Применяю…")
            val root = Layer.root(c)
            root.mkdirs()
            val kind = plan.getString("kind")
            val version = plan.getString("version")
            val serial = plan.optString("serial")
            val dirName = if (kind == "update") "update-$version" else "fix-$version-$serial"
            val dir = File(root, dirName)
            dir.deleteRecursively()
            val tmp = File(root, "tmp-" + System.nanoTime())
            val code = ArrayList<Pair<String, ByteArray>>()
            for ((p, data) in staged) {
                if (p.startsWith("plugins/") || p.startsWith("themes/")) place(c, p, data) else code.add(p to data)
            }
            for ((p, data) in code) {
                val f = File(tmp, p)
                require(f.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) { "Путь вне каталога слоя" }
                f.parentFile?.mkdirs()
                f.writeBytes(data)
                if (p.startsWith("dex/")) f.setReadOnly()
            }
            if (code.isNotEmpty()) require(tmp.renameTo(dir)) { "Не удалось сохранить слой" }
            writeState(root, kind, dirName, version, serial, code.isNotEmpty())
            if (code.isNotEmpty()) "restart" else null
        } catch (e: Throwable) {
            e.message ?: "Ошибка обновления"
        }
    }

    private fun writeState(root: File, kind: String, dirName: String, version: String, serial: String, hasCode: Boolean) {
        val f = File(root, "state.json")
        val cur = if (f.isFile) try { JSONObject(f.readText()) } catch (e: Throwable) { JSONObject() } else JSONObject()
        val keep = HashSet<String>()
        if (kind == "update") {
            cur.put("update", if (hasCode) dirName else "")
            cur.put("fixes", org.json.JSONArray())
            cur.put("version", version)
            cur.put("serial", "")
        } else {
            val fx = cur.optJSONArray("fixes") ?: org.json.JSONArray()
            if (hasCode) fx.put(dirName)
            cur.put("fixes", fx)
            cur.put("serial", serial)
            if (cur.optString("version").isEmpty()) cur.put("version", version)
        }
        cur.optString("update").takeIf { it.isNotEmpty() }?.let { keep.add(it) }
        val fx = cur.optJSONArray("fixes")
        if (fx != null) for (i in 0 until fx.length()) keep.add(fx.getString(i))
        val tmp = File(root, "state.tmp")
        tmp.writeText(cur.toString())
        require(tmp.renameTo(f) || (f.delete() && tmp.renameTo(f))) { "Не удалось записать состояние" }
        File(root, "boots").writeText("0")
        root.listFiles()?.filter { it.isDirectory && (it.name.startsWith("update-") || it.name.startsWith("fix-")) && it.name !in keep }?.forEach { it.deleteRecursively() }
    }

    private fun place(c: Context, path: String, data: ByteArray) {
        if (path.startsWith("themes/")) {
            val e = Themes.install(c, String(data, Charsets.UTF_8))
            if (!e.isNullOrEmpty()) throw IllegalStateException(e)
            return
        }
        val rel = path.removePrefix("plugins/")
        val key = rel.substringBefore('/')
        require(Plugins.safeName(key)) { "Недопустимое имя плагина" }
        val root = Plugins.rootDir(c)
        val f = File(root, rel)
        require(f.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Путь вне каталога плагинов" }
        f.parentFile?.mkdirs()
        f.writeBytes(data)
        Plugins.stamp++
    }

    fun restart(c: Context) {
        try {
            c.startService(Intent(c, TunnService::class.java).setAction("tunn.client.STOP"))
        } catch (e: Throwable) {
        }
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pi = android.app.PendingIntent.getActivity(c, 7, launch, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_CANCEL_CURRENT)
        c.getSystemService(android.app.AlarmManager::class.java).set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 800L, pi)
        Handler(Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 400L)
    }
}
