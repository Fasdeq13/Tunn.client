package tunn.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import android.system.Int64Ref
import android.system.Os
import android.system.OsConstants
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Mark(val kind: Int, val value: Int)

class Strategy(
    val id: String,
    val title: String,
    val marks: List<Mark>,
    val tlsrec: Boolean = false,
    val oob: Boolean = false,
    val fake: Boolean = false,
    val delay: Long = 0L
)

object TlsHello {
    private val rnd = SecureRandom()

    private fun ByteArrayOutputStream.u8(v: Int) = write(v and 0xff)
    private fun ByteArrayOutputStream.u16(v: Int) {
        write((v shr 8) and 0xff)
        write(v and 0xff)
    }
    private fun ByteArrayOutputStream.bytes(b: ByteArray) = write(b, 0, b.size)
    private fun random(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }

    private fun ext(type: Int, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.u16(type)
        o.u16(body.size)
        o.bytes(body)
        return o.toByteArray()
    }

    fun build(host: String): ByteArray {
        val name = host.toByteArray(Charsets.US_ASCII)
        val sn = ByteArrayOutputStream().also {
            it.u16(name.size + 3)
            it.u8(0)
            it.u16(name.size)
            it.bytes(name)
        }.toByteArray()
        val groups = byteArrayOf(0, 6, 0, 0x1d, 0, 0x17, 0, 0x18)
        val formats = byteArrayOf(1, 0)
        val alpn = byteArrayOf(0, 12, 2, 0x68, 0x32, 8, 0x68, 0x74, 0x74, 0x70, 0x2f, 0x31, 0x2e, 0x31)
        val sigs = byteArrayOf(0, 16, 4, 3, 8, 4, 4, 1, 5, 3, 8, 5, 5, 1, 8, 6, 6, 1)
        val versions = byteArrayOf(4, 3, 4, 3, 3)
        val psk = byteArrayOf(1, 1)
        val share = ByteArrayOutputStream().also {
            it.u16(36)
            it.u16(0x1d)
            it.u16(32)
            it.bytes(random(32))
        }.toByteArray()
        val exts = ByteArrayOutputStream()
        exts.bytes(ext(0, sn))
        exts.bytes(ext(23, ByteArray(0)))
        exts.bytes(ext(0xff01, byteArrayOf(0)))
        exts.bytes(ext(10, groups))
        exts.bytes(ext(11, formats))
        exts.bytes(ext(35, ByteArray(0)))
        exts.bytes(ext(16, alpn))
        exts.bytes(ext(5, byteArrayOf(1, 0, 0, 0, 0)))
        exts.bytes(ext(13, sigs))
        exts.bytes(ext(43, versions))
        exts.bytes(ext(45, psk))
        exts.bytes(ext(51, share))
        val suites = intArrayOf(0x1301, 0x1302, 0x1303, 0xc02b, 0xc02f, 0xc02c, 0xc030, 0xcca9, 0xcca8, 0xc013, 0xc014, 0x009c, 0x009d, 0x002f, 0x0035)
        val body = ByteArrayOutputStream()
        body.u16(0x0303)
        body.bytes(random(32))
        body.u8(32)
        body.bytes(random(32))
        body.u16(suites.size * 2)
        suites.forEach { body.u16(it) }
        body.u8(1)
        body.u8(0)
        val e = exts.toByteArray()
        body.u16(e.size)
        body.bytes(e)
        val hs = ByteArrayOutputStream()
        hs.u8(1)
        val b = body.toByteArray()
        hs.u8(b.size shr 16)
        hs.u16(b.size and 0xffff)
        hs.bytes(b)
        val h = hs.toByteArray()
        val rec = ByteArrayOutputStream()
        rec.u8(0x16)
        rec.u16(0x0301)
        rec.u16(h.size)
        rec.bytes(h)
        return rec.toByteArray()
    }

    fun isTls(d: ByteArray): Boolean = d.size > 11 && d[0] == 0x16.toByte() && d[1] == 3.toByte() && d[5] == 1.toByte()

    fun sni(d: ByteArray): IntRange? {
        try {
            if (!isTls(d)) return null
            var p = 43
            p += 1 + (d[p].toInt() and 0xff)
            p += 2 + (((d[p].toInt() and 0xff) shl 8) or (d[p + 1].toInt() and 0xff))
            p += 1 + (d[p].toInt() and 0xff)
            val end = p + 2 + (((d[p].toInt() and 0xff) shl 8) or (d[p + 1].toInt() and 0xff))
            p += 2
            while (p + 4 <= end && p + 4 <= d.size) {
                val type = ((d[p].toInt() and 0xff) shl 8) or (d[p + 1].toInt() and 0xff)
                val len = ((d[p + 2].toInt() and 0xff) shl 8) or (d[p + 3].toInt() and 0xff)
                if (type == 0) {
                    val start = p + 4 + 5
                    val nameLen = ((d[p + 4 + 3].toInt() and 0xff) shl 8) or (d[p + 4 + 4].toInt() and 0xff)
                    if (start + nameLen <= d.size) return start until (start + nameLen)
                    return null
                }
                p += 4 + len
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun splitRecord(d: ByteArray, at: Int): ByteArray {
        if (at <= 5 || at >= d.size) return d
        val o = ByteArrayOutputStream()
        val first = at - 5
        o.write(d, 0, 3)
        o.u16(first)
        o.write(d, 5, first)
        val rest = d.size - at
        o.write(d, 0, 3)
        o.u16(rest)
        o.write(d, at, rest)
        return o.toByteArray()
    }
}

object Doh {
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val literal = Regex("^[0-9.]+$|^[0-9a-fA-F]*:[0-9a-fA-F:]+$")

    private fun usable(a: InetAddress): Boolean =
        a.address.size == 4 && !a.isLoopbackAddress && !a.isAnyLocalAddress && !a.isSiteLocalAddress && !a.isLinkLocalAddress

    fun resolve(host: String): String? {
        if (literal.matches(host)) return host
        val hit = cache[host]
        if (hit != null && System.currentTimeMillis() - hit.second < 600_000L) return hit.first
        val sys = try {
            InetAddress.getAllByName(host).firstOrNull { usable(it) }?.hostAddress
        } catch (e: Exception) {
            null
        }
        if (sys != null) {
            cache[host] = sys to System.currentTimeMillis()
            return sys
        }
        for (base in listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")) {
            val ip = try {
                query(base, host)
            } catch (e: Exception) {
                null
            }
            if (ip != null) {
                cache[host] = ip to System.currentTimeMillis()
                return ip
            }
        }
        return null
    }

    private fun query(base: String, host: String): String? {
        val c = URL("$base?name=$host&type=A").openConnection() as HttpURLConnection
        c.connectTimeout = 3500
        c.readTimeout = 3500
        c.setRequestProperty("accept", "application/dns-json")
        try {
            if (c.responseCode != 200) return null
            val answers = JSONObject(c.inputStream.use { String(it.readBytes()) }).optJSONArray("Answer") ?: return null
            for (i in 0 until answers.length()) {
                val a = answers.optJSONObject(i) ?: continue
                if (a.optInt("type") == 1) return a.optString("data")
            }
            return null
        } finally {
            c.disconnect()
        }
    }
}

object Desync {
    private fun ttl(fd: FileDescriptor, v: Int) {
        try {
            Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, v)
        } catch (e: Throwable) {
        }
        try {
            Os.setsockoptInt(fd, OsConstants.IPPROTO_IPV6, OsConstants.IPV6_UNICAST_HOPS, v)
        } catch (e: Throwable) {
        }
    }

    private fun cuts(d: ByteArray, st: Strategy): List<Int> {
        val sni = TlsHello.sni(d)
        val list = ArrayList<Int>()
        for (m in st.marks) {
            val v = when (m.kind) {
                1 -> if (sni != null) sni.first + m.value else 1
                2 -> if (sni != null) (sni.first + sni.last) / 2 else 2
                else -> m.value
            }
            if (v in 1 until d.size) list.add(v)
        }
        return list.distinct().sorted()
    }

    private fun fakeFirst(s: Socket, real: ByteArray, len: Int, dir: File) {
        val file = File.createTempFile("dpi", ".bin", dir)
        try {
            val fake = TlsHello.build("www.google.com").copyOf(len)
            RandomAccessFile(file, "rw").use { raf ->
                raf.write(fake)
                val pfd = ParcelFileDescriptor.fromSocket(s)
                try {
                    ttl(pfd.fileDescriptor, 4)
                    FileInputStream(file).use { fin ->
                        Os.sendfile(pfd.fileDescriptor, fin.fd, Int64Ref(0), len.toLong())
                    }
                    raf.seek(0)
                    raf.write(real, 0, len)
                    ttl(pfd.fileDescriptor, 64)
                } finally {
                    pfd.close()
                }
            }
        } finally {
            file.delete()
        }
    }

    fun send(s: Socket, data: ByteArray, st: Strategy, dir: File) {
        s.tcpNoDelay = true
        val out = s.getOutputStream()
        var payload = data
        if (st.tlsrec) {
            val sni = TlsHello.sni(data)
            if (sni != null) payload = TlsHello.splitRecord(data, sni.first + 1)
        }
        val list = cuts(payload, st)
        if (list.isEmpty()) {
            out.write(payload)
            out.flush()
            return
        }
        var prev = 0
        if (st.fake) {
            try {
                fakeFirst(s, payload, list[0], dir)
                prev = list[0]
            } catch (e: Throwable) {
                prev = 0
            }
        }
        val points = list.filter { it > prev } + payload.size
        for (p in points) {
            out.write(payload, prev, p - prev)
            out.flush()
            if (st.oob && prev == 0) {
                try {
                    s.sendUrgentData(0)
                } catch (e: Exception) {
                }
            }
            if (st.delay > 0 && p < payload.size) Thread.sleep(st.delay)
            prev = p
        }
    }

    fun fakeUdp(sock: DatagramSocket, to: InetSocketAddress, repeats: Int) {
        val fake = ByteArray(74)
        fake[1] = 1
        fake[3] = 0x46
        val pfd = try {
            ParcelFileDescriptor.fromDatagramSocket(sock)
        } catch (e: Throwable) {
            null
        } ?: return
        try {
            ttl(pfd.fileDescriptor, 4)
            repeat(repeats) { sock.send(DatagramPacket(fake, fake.size, to)) }
            ttl(pfd.fileDescriptor, 64)
        } catch (e: Throwable) {
        } finally {
            pfd.close()
        }
    }
}

object Dpi {
    val strategies: List<Strategy> = listOf(
        Strategy("direct", "Без обхода", emptyList()),
        Strategy("general", "general", listOf(Mark(0, 1))),
        Strategy("ALT2", "general (ALT2)", listOf(Mark(0, 2))),
        Strategy("ALT3", "general (ALT3)", listOf(Mark(1, 1)), tlsrec = true),
        Strategy("ALT", "general (ALT)", listOf(Mark(0, 1), Mark(1, 0)), fake = true),
        Strategy("FAKE TLS AUTO", "general (FAKE TLS AUTO)", listOf(Mark(0, 1)), fake = true),
        Strategy("SIMPLE OOB", "general (SIMPLE OOB)", listOf(Mark(0, 1)), oob = true),
        Strategy("SNI-MID", "general (SNI-MID)", listOf(Mark(2, 0)), delay = 10),
        Strategy("SLOW SPLIT", "general (SLOW SPLIT)", listOf(Mark(0, 1), Mark(1, 0), Mark(2, 0)), delay = 80)
    )

    fun byId(id: String): Strategy? = strategies.firstOrNull { it.id == id }

    private fun stateFile(c: Context) = File(c.filesDir, "dpi_state.json")
    private fun statusFile(c: Context) = File(c.filesDir, "dpi_status.json")

    fun networkKey(c: Context): String {
        val cm = try {
            c.getSystemService(ConnectivityManager::class.java)
        } catch (e: Throwable) {
            null
        }
        val caps = try {
            cm?.getNetworkCapabilities(cm.activeNetwork)
        } catch (e: Throwable) {
            null
        }
        return when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                "cell:" + (try { c.getSystemService(TelephonyManager::class.java)?.networkOperatorName } catch (e: Throwable) { null } ?: "")
            else -> "other"
        }
    }

    private fun saved(c: Context): JSONObject = try {
        JSONObject(stateFile(c).readText())
    } catch (e: Exception) {
        JSONObject()
    }

    fun chosen(c: Context): Strategy? = byId(saved(c).optString(networkKey(c), ""))

    fun save(c: Context, key: String, id: String) {
        val o = saved(c)
        o.put(key, id)
        try {
            stateFile(c).writeText(o.toString())
        } catch (e: Exception) {
        }
    }

    fun forget(c: Context) {
        try {
            stateFile(c).delete()
        } catch (e: Exception) {
        }
    }

    fun setStatus(c: Context, state: String, done: Int, total: Int, best: String) {
        try {
            statusFile(c).writeText(JSONObject().put("state", state).put("done", done).put("total", total).put("best", best).put("at", System.currentTimeMillis()).toString())
        } catch (e: Exception) {
        }
    }

    fun status(c: Context): JSONObject {
        val o = try {
            JSONObject(statusFile(c).readText())
        } catch (e: Exception) {
            JSONObject().put("state", "idle").put("done", 0).put("total", strategies.size).put("best", "")
        }
        if (o.optString("state") == "searching" && System.currentTimeMillis() - o.optLong("at") > 120_000L) o.put("state", "idle")
        val cur = chosen(c)
        o.put("strategy", cur?.title ?: "")
        try {
            val live = JSONObject(File(c.filesDir, "dpi_live.json").readText())
            o.put("ok", live.optInt("ok")).put("fail", live.optInt("fail"))
        } catch (e: Exception) {
            o.put("ok", 0).put("fail", 0)
        }
        return o
    }
}

object DpiSearch {
    private val running = AtomicBoolean(false)
    private val hosts = listOf("www.youtube.com", "i.ytimg.com", "discord.com", "gateway.discord.gg", "cdn.discordapp.com", "www.google.com")

    fun isRunning(): Boolean = running.get()

    private fun probe(c: Context, st: Strategy, host: String): Boolean {
        repeat(2) {
            val ip = Doh.resolve(host) ?: return@repeat
            val s = Socket()
            try {
                s.connect(InetSocketAddress(ip, 443), 4000)
                s.soTimeout = 4500
                Desync.send(s, TlsHello.build(host), st, c.cacheDir)
                val b = ByteArray(5)
                val n = s.getInputStream().read(b)
                if (n >= 3 && (b[0] == 0x16.toByte() || b[0] == 0x15.toByte())) return true
            } catch (e: Exception) {
            } finally {
                try {
                    s.close()
                } catch (e: Exception) {
                }
            }
        }
        return false
    }

    private fun score(c: Context, st: Strategy): Int {
        val pool = Executors.newFixedThreadPool(hosts.size)
        try {
            val results = hosts.map { h -> pool.submit<Boolean> { probe(c, st, h) } }
            return results.count { try { it.get(25, TimeUnit.SECONDS) } catch (e: Exception) { false } }
        } finally {
            pool.shutdownNow()
        }
    }

    fun run(c: Context) {
        if (!running.compareAndSet(false, true)) return
        try {
            val key = Dpi.networkKey(c)
            val total = Dpi.strategies.size
            var best = Dpi.strategies[1]
            var bestScore = -1
            for ((i, st) in Dpi.strategies.withIndex()) {
                Dpi.setStatus(c, "searching", i, total, "")
                val sc = score(c, st)
                if (st.id == "direct" && sc == hosts.size) {
                    best = Dpi.strategies[1]
                    break
                }
                if (st.id != "direct" && sc > bestScore) {
                    best = st
                    bestScore = sc
                }
                if (sc == hosts.size) break
            }
            Dpi.save(c, key, best.id)
            Dpi.setStatus(c, "done", total, total, best.id)
        } catch (e: Throwable) {
            Dpi.setStatus(c, "idle", 0, Dpi.strategies.size, "")
        } finally {
            running.set(false)
        }
    }
}

object DpiProxy {
    private var server: ServerSocket? = null
    private var pool = Executors.newCachedThreadPool()
    @Volatile private var ctx: Context? = null
    @Volatile private var cached: Strategy = Dpi.strategies[1]
    @Volatile private var cachedAt = 0L

    private fun strategy(): Strategy {
        val c = ctx ?: return cached
        val now = System.currentTimeMillis()
        if (now - cachedAt > 3000L) {
            cached = Dpi.chosen(c) ?: Dpi.strategies[1]
            cachedAt = now
        }
        return cached
    }

    @Synchronized
    fun start(context: Context): Int {
        stop()
        ctx = context.applicationContext
        cachedAt = 0L
        pool = Executors.newCachedThreadPool()
        val s = ServerSocket(0, 256, InetAddress.getByName("127.0.0.1"))
        server = s
        pool.execute {
            while (!s.isClosed) {
                val client = try {
                    s.accept()
                } catch (e: Exception) {
                    break
                }
                pool.execute { handle(client) }
            }
        }
        return s.localPort
    }

    @Synchronized
    fun stop() {
        try {
            server?.close()
        } catch (e: Exception) {
        }
        server = null
        pool.shutdownNow()
    }

    private fun readN(i: InputStream, n: Int): ByteArray? {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = i.read(b, off, n - off)
            if (r < 0) return null
            off += r
        }
        return b
    }

    private fun reply(o: OutputStream, code: Int, port: Int = 0) {
        o.write(byteArrayOf(5, code.toByte(), 0, 1, 127, 0, 0, 1, (port shr 8).toByte(), port.toByte()))
        o.flush()
    }

    private fun pipe(a: InputStream, b: OutputStream) {
        val buf = ByteArray(16384)
        try {
            while (true) {
                val n = a.read(buf)
                if (n < 0) break
                b.write(buf, 0, n)
                b.flush()
            }
        } catch (e: Exception) {
        }
    }

    private fun half(s: Socket) {
        try {
            s.shutdownOutput()
        } catch (e: Exception) {
        }
    }

    fun relay(a: Socket, b: Socket) {
        val t = Thread {
            pipe(b.getInputStream(), a.getOutputStream())
            half(a)
        }
        t.start()
        pipe(a.getInputStream(), b.getOutputStream())
        half(b)
        try {
            t.join(60000)
        } catch (e: Exception) {
        }
        close(a)
        close(b)
    }

    private fun close(s: Socket) {
        try {
            s.close()
        } catch (e: Exception) {
        }
    }

    private fun handle(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.soTimeout = 20000
            val i = client.getInputStream()
            val o = client.getOutputStream()
            if (i.read() != 5) return close(client)
            val n = i.read()
            readN(i, n)
            o.write(byteArrayOf(5, 0))
            o.flush()
            val head = readN(i, 4) ?: return close(client)
            val cmd = head[1].toInt()
            val host = when (head[3].toInt()) {
                1 -> readN(i, 4)?.joinToString(".") { (it.toInt() and 0xff).toString() }
                4 -> readN(i, 16)?.let { InetAddress.getByAddress(it).hostAddress }
                3 -> {
                    val l = i.read()
                    readN(i, l)?.toString(Charsets.US_ASCII)
                }
                else -> null
            } ?: return close(client)
            val pb = readN(i, 2) ?: return close(client)
            val port = ((pb[0].toInt() and 0xff) shl 8) or (pb[1].toInt() and 0xff)
            when (cmd) {
                1 -> connect(client, host, port)
                3 -> udp(client)
                else -> {
                    reply(o, 7)
                    close(client)
                }
            }
        } catch (e: Exception) {
            close(client)
        }
    }

    private val winners = ConcurrentHashMap<String, String>()
    @Volatile private var okCount = 0
    @Volatile private var failCount = 0
    @Volatile private var noteAt = 0L

    private fun note(ok: Boolean) {
        if (ok) okCount++ else failCount++
        val now = System.currentTimeMillis()
        val c = ctx ?: return
        if (now - noteAt < 2000L) return
        noteAt = now
        try {
            File(c.filesDir, "dpi_live.json").writeText(JSONObject().put("ok", okCount).put("fail", failCount).put("at", now).toString())
        } catch (e: Exception) {
        }
    }

    private fun open(ip: String, port: Int): Socket? {
        val s = Socket()
        return try {
            s.connect(InetSocketAddress(ip, port), 6000)
            s.tcpNoDelay = true
            s
        } catch (e: Exception) {
            close(s)
            null
        }
    }

    private fun exchange(s: Socket, data: ByteArray, st: Strategy): ByteArray? {
        return try {
            if (st.id == "direct") {
                s.getOutputStream().write(data)
                s.getOutputStream().flush()
            } else {
                Desync.send(s, data, st, ctx?.cacheDir ?: File("/data/local/tmp"))
            }
            s.soTimeout = 4500
            val buf = ByteArray(16384)
            val n = s.getInputStream().read(buf)
            s.soTimeout = 0
            if (n > 0) buf.copyOf(n) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun connect(client: Socket, host: String, port: Int) {
        val o = client.getOutputStream()
        val ip = Doh.resolve(host)
        if (ip == null) {
            note(false)
            reply(o, 4)
            return close(client)
        }
        if (TgWs.isTelegram(ip) && (port == 443 || port == 80 || port == 5222)) {
            reply(o, 0)
            TgWs.bridge(client, ip, port)
            return
        }
        val first = open(ip, port)
        if (first == null) {
            note(false)
            reply(o, 5)
            return close(client)
        }
        reply(o, 0)
        val buf = ByteArray(8192)
        val n = try {
            client.soTimeout = 3000
            client.getInputStream().read(buf)
        } catch (e: Exception) {
            -1
        }
        client.soTimeout = 0
        if (n <= 0) {
            close(first)
            return close(client)
        }
        val data = buf.copyOf(n)
        val tls = port == 443 && TlsHello.isTls(data)
        val http = port == 80 && (data[0] == 'G'.code.toByte() || data[0] == 'H'.code.toByte())
        if (!tls) {
            try {
                if (http) {
                    Desync.send(first, data, strategy(), ctx?.cacheDir ?: File("/data/local/tmp"))
                } else {
                    first.getOutputStream().write(data)
                    first.getOutputStream().flush()
                }
            } catch (e: Exception) {
                close(first)
                note(false)
                return close(client)
            }
            first.soTimeout = 0
            note(true)
            relay(client, first)
            return
        }
        val key = "$host:$port"
        val order = ArrayList<Strategy>()
        winners[key]?.let { Dpi.byId(it) }?.let { order.add(it) }
        order.add(strategy())
        order.add(Dpi.strategies[0])
        order.addAll(Dpi.strategies.drop(1))
        var sock: Socket? = first
        for (st in order.distinctBy { it.id }.take(4)) {
            val s = sock ?: open(ip, port) ?: continue
            sock = null
            val resp = exchange(s, data, st)
            if (resp != null) {
                winners[key] = st.id
                note(true)
                try {
                    client.getOutputStream().write(resp)
                    client.getOutputStream().flush()
                } catch (e: Exception) {
                    close(s)
                    return close(client)
                }
                relay(client, s)
                return
            }
            close(s)
        }
        note(false)
        close(client)
    }

    private fun udp(client: Socket) {
        val local = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val up = DatagramSocket()
        val seen = ConcurrentHashMap.newKeySet<String>()
        val back = java.util.concurrent.atomic.AtomicReference<java.net.SocketAddress?>(null)
        reply(client.getOutputStream(), 0, local.localPort)
        client.soTimeout = 0
        pool.execute {
            val buf = ByteArray(65535)
            while (!local.isClosed) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    local.receive(p)
                    back.set(p.socketAddress)
                    val d = p.data
                    var off = 4
                    val addr: InetAddress? = when (d[3].toInt()) {
                        1 -> InetAddress.getByAddress(d.copyOfRange(4, 8)).also { off = 8 }
                        4 -> InetAddress.getByAddress(d.copyOfRange(4, 20)).also { off = 20 }
                        3 -> {
                            val l = d[4].toInt() and 0xff
                            off = 5 + l
                            Doh.resolve(String(d, 5, l, Charsets.US_ASCII))?.let { InetAddress.getByName(it) }
                        }
                        else -> null
                    }
                    if (addr == null) continue
                    val dport = ((d[off].toInt() and 0xff) shl 8) or (d[off + 1].toInt() and 0xff)
                    off += 2
                    if (dport == 443) continue
                    val payload = d.copyOfRange(off, p.length)
                    val dst = InetSocketAddress(addr, dport)
                    if ((dport in 50000..65535 || dport in 3478..3481) && seen.add(dst.toString())) Desync.fakeUdp(up, dst, 6)
                    up.send(DatagramPacket(payload, payload.size, dst))
                } catch (e: Exception) {
                    if (local.isClosed) break
                }
            }
        }
        pool.execute {
            val buf = ByteArray(65535)
            while (!up.isClosed) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    up.receive(p)
                    val target = back.get() ?: continue
                    val a = p.address.address
                    val o = ByteArrayOutputStream()
                    o.write(byteArrayOf(0, 0, 0, if (a.size == 4) 1 else 4))
                    o.write(a)
                    o.write(byteArrayOf((p.port shr 8).toByte(), p.port.toByte()))
                    o.write(p.data, 0, p.length)
                    val out = o.toByteArray()
                    local.send(DatagramPacket(out, out.size, target))
                } catch (e: Exception) {
                    if (up.isClosed) break
                }
            }
        }
        try {
            client.getInputStream().read()
        } catch (e: Exception) {
        }
        local.close()
        up.close()
        close(client)
    }
}
