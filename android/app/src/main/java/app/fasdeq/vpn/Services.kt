package app.fasdeq.vpn

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class ServiceCheck(
    val ms: List<Int?> = listOf(null, null, null),
    val busy: Boolean = false
) {
    fun with(index: Int, value: Int?): ServiceCheck {
        val list = ms.toMutableList()
        list[index] = value
        return copy(ms = list)
    }
}

object Services {
    val names = listOf("Discord", "Telegram", "YouTube")
    val mirrors: List<List<String>> = listOf(
        listOf(
            "https://discord.com/api/v9/gateway",
            "https://discordapp.com/api/v9/gateway",
            "https://cdn.discordapp.com/embed/avatars/0.png",
            "https://status.discord.com/api/v2/summary.json"
        ),
        listOf(
            "https://telegram.org/",
            "https://web.telegram.org/a/",
            "https://core.telegram.org/api",
            "https://t.me/telegram"
        ),
        listOf(
            "https://www.youtube.com/generate_204",
            "https://i.ytimg.com/vi/jNQXAC9IVRw/default.jpg",
            "https://www.youtube.com/robots.txt",
            "https://www.youtube.com/favicon.ico"
        )
    )
    const val UNSUPPORTED = -3
    const val SKIPPED = -4
    const val PARTIAL = -2

    private val pool = Executors.newCachedThreadPool()

    fun probeConfig(outbound: JSONObject): String {
        outbound.put("tag", "proxy")
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "none"))
            .put(
                "outbounds",
                JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            )
            .toString()
    }

    private val slots = java.util.concurrent.Semaphore(8)

    fun delay(config: String, url: String, timeoutSeconds: Long = 8): Int {
        val method = try {
            Class.forName("libv2ray.Libv2ray")
                .getMethod("measureOutboundDelay", String::class.java, String::class.java)
        } catch (e: Throwable) {
            return UNSUPPORTED
        }
        try {
            slots.acquire()
        } catch (e: InterruptedException) {
            return -1
        }
        try {
            val future = pool.submit(
                Callable {
                    try {
                        (method.invoke(null, config, url) as Long).toInt()
                    } catch (e: Throwable) {
                        -1
                    }
                }
            )
            return try {
                val value = future.get(timeoutSeconds, TimeUnit.SECONDS)
                if (value >= 0) value.coerceAtLeast(1) else -1
            } catch (e: TimeoutException) {
                future.cancel(true)
                -1
            } catch (e: Exception) {
                -1
            }
        } finally {
            slots.release()
        }
    }

    fun check(config: String, index: Int, tries: Int = 3, timeoutSeconds: Long = 8): Int {
        for (u in mirrors[index].take(tries)) {
            val v = delay(config, u, timeoutSeconds)
            if (v == UNSUPPORTED) return UNSUPPORTED
            if (v > 0) return v
        }
        return -1
    }
}
