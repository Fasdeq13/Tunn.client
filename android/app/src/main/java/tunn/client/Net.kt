package tunn.client

import java.net.HttpURLConnection
import java.net.URL

object Net {
    private fun explain(code: Int, host: String): String = when (code) {
        403, 429 -> "$host ограничил запросы (HTTP $code). Подождите несколько минут или установите из файла"
        404 -> "Файл не найден на $host (HTTP 404)"
        in 500..599 -> "$host временно недоступен (HTTP $code)"
        else -> "$host ответил HTTP $code"
    }

    private fun once(start: String, accept: String, timeoutMs: Int): ByteArray {
        var url = start
        for (hop in 0..6) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", "Tunn/1.9")
            c.setRequestProperty("Accept", accept)
            try {
                val code = c.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val loc = c.getHeaderField("Location") ?: throw IllegalStateException("Пустое перенаправление")
                    url = URL(URL(url), loc).toString()
                    continue
                }
                if (code !in 200..299) throw IllegalStateException(explain(code, URL(url).host))
                return c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        throw IllegalStateException("Слишком много перенаправлений")
    }

    fun get(url: String, accept: String = "*/*", timeoutMs: Int = 60_000, tries: Int = 3): ByteArray {
        var last: Exception? = null
        for (attempt in 0 until tries) {
            try {
                return once(url, accept, timeoutMs)
            } catch (e: Exception) {
                last = e
                val m = e.message ?: ""
                if (m.contains("HTTP 404") || m.contains("HTTP 403") || m.contains("HTTP 429")) break
                try {
                    Thread.sleep(700L * (attempt + 1))
                } catch (x: InterruptedException) {
                    break
                }
            }
        }
        throw last ?: IllegalStateException("Не удалось загрузить")
    }

    fun first(urls: List<String>, accept: String = "*/*", timeoutMs: Int = 30_000): ByteArray {
        var last: Exception? = null
        for (u in urls) {
            try {
                return get(u, accept, timeoutMs, 2)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Не удалось загрузить")
    }
}
