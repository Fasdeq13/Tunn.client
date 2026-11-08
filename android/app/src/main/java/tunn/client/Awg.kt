package tunn.client

import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object Awg {
    @Volatile
    private var loaded = false

    private fun cls(): Class<*> = try {
        Class.forName("org.amnezia.awg.GoBackend")
    } catch (e: Throwable) {
        throw IllegalStateException("Библиотека AmneziaWG не найдена в приложении")
    }

    fun load() {
        if (loaded) return
        var failure = ""
        val downloaded = Components.awgFile(TunnApp.appContext)
        if (downloaded.exists()) {
            try {
                System.load(downloaded.absolutePath)
                loaded = true
                return
            } catch (e: UnsatisfiedLinkError) {
                failure = e.message ?: ""
            }
        }
        for (name in listOf("am-go", "wg-go")) {
            try {
                System.loadLibrary(name)
                loaded = true
                return
            } catch (e: UnsatisfiedLinkError) {
                failure = e.message ?: ""
            }
        }
        throw IllegalStateException("Не удалось загрузить библиотеку AmneziaWG: $failure")
    }

    private fun method(name: String, args: Int = -1): Method =
        cls().methods.firstOrNull { it.name == name && (args < 0 || it.parameterTypes.size == args) }
            ?: throw IllegalStateException("Версия библиотеки AmneziaWG не поддерживается: нет $name")

    private fun call(m: Method, vararg args: Any?): Any? = try {
        m.invoke(null, *args)
    } catch (e: InvocationTargetException) {
        throw IllegalStateException(e.targetException?.message ?: "Ошибка AmneziaWG")
    }

    fun version(): String = try {
        load()
        (call(method("awgVersion")) as? String) ?: ""
    } catch (e: Throwable) {
        ""
    }

    fun turnOn(name: String, fd: Int, settings: String, dir: String): Int {
        load()
        return (call(method("awgTurnOn", 4), name, fd, settings, dir) as? Int) ?: -1
    }

    fun turnOff(handle: Int) {
        call(method("awgTurnOff"), handle)
    }

    fun socketV4(handle: Int): Int = (call(method("awgGetSocketV4"), handle) as? Int) ?: -1

    fun socketV6(handle: Int): Int = (call(method("awgGetSocketV6"), handle) as? Int) ?: -1

    fun config(handle: Int): String? = try {
        call(method("awgGetConfig"), handle) as? String
    } catch (e: Throwable) {
        null
    }

    fun stats(handle: Int): JSONObject? {
        val text = config(handle) ?: return null
        var rx = 0L
        var tx = 0L
        var hs = 0L
        for (line in text.lines()) {
            val i = line.indexOf('=')
            if (i <= 0) continue
            val n = line.substring(i + 1).trim().toLongOrNull() ?: continue
            when (line.substring(0, i).trim()) {
                "rx_bytes" -> rx += n
                "tx_bytes" -> tx += n
                "last_handshake_time_sec" -> if (n > hs) hs = n
            }
        }
        return JSONObject().put("rx", rx).put("tx", tx).put("hs", hs)
    }
}
