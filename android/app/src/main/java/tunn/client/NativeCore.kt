package tunn.client

import org.json.JSONObject

object NativeCore {
    private val loaded: Boolean = try {
        Class.forName("tunn.client.Native")
        true
    } catch (e: Throwable) {
        false
    }

    fun call(block: () -> String): JSONObject? {
        if (!loaded) return null
        return try {
            val out = JSONObject(block())
            if (out.optBoolean("ok", true)) out else null
        } catch (e: Throwable) {
            null
        }
    }

    fun ping(conf: String, endpoint: String, mode: Int, timeout: Int): PingResult? {
        if (!loaded) return null
        return try {
            val o = JSONObject(Native.ping(conf, endpoint, mode, timeout.toLong()))
            PingResult(
                tcp = if (o.isNull("tcp")) null else o.getInt("tcp"),
                udp = if (o.isNull("udp")) null else o.getInt("udp"),
                icmp = if (o.isNull("icmp")) null else o.getInt("icmp")
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun retryDelay(n: Int): Long? {
        if (loaded) try { return Native.retryDelay(n).takeIf { it >= 0 } } catch (e: Throwable) {}
        return longArrayOf(0, 300, 900, 1800, 3500).getOrNull(n)
    }

    fun startTimeout(kind: String, n: Int): Long {
        if (loaded) try { return Native.startTimeout(kind, n) } catch (e: Throwable) {}
        return 11000L + n.coerceAtMost(3) * 1500L
    }

    fun settle(prev: String): Long {
        if (loaded) try { return Native.settleMs(prev) } catch (e: Throwable) {}
        return if (prev == "awg" || prev == "wg") 900L else 400L
    }

    fun pickNext(candidates: Map<String, Int>, failed: Collection<String>, current: String): String? {
        val arr = org.json.JSONArray()
        for ((id, p) in candidates) arr.put(JSONObject().put("id", id).put("ping", p))
        if (loaded) try {
            return Native.pickNext(arr.toString(), failed.joinToString(","), current).ifEmpty { null }
        } catch (e: Throwable) {}
        return candidates.entries.filter { it.key != current && it.key !in failed && it.value > 0 }.minByOrNull { it.value }?.key
            ?: candidates.keys.firstOrNull { it != current && it !in failed }
    }

    fun subHeaders(headers: Map<String, String>): JSONObject {
        if (loaded) try { return JSONObject(Native.subHeaders(JSONObject(headers).toString())) } catch (e: Throwable) {}
        return JSONObject()
    }
}
