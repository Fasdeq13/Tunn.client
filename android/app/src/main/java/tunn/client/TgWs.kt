package tunn.client

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private class FragOutput(private val real: OutputStream) : FilterOutputStream(real) {
    @Volatile private var armed = true

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (!armed || !TlsHello.isTls(b.copyOfRange(off, off + len))) {
            armed = false
            real.write(b, off, len)
            real.flush()
            return
        }
        armed = false
        val data = b.copyOfRange(off, off + len)
        val sni = TlsHello.sni(data)
        val record = if (sni != null) TlsHello.splitRecord(data, sni.first + 1) else data
        val cuts = (if (sni != null) listOf(sni.first + 1, (sni.first + sni.last) / 2) else listOf(record.size / 2))
            .filter { it in 1 until record.size }.distinct().sorted()
        var prev = 0
        for (cut in cuts + record.size) {
            real.write(record, prev, cut - prev)
            real.flush()
            if (prev == 0) {
                try {
                    Thread.sleep(6)
                } catch (e: InterruptedException) {
                }
            }
            prev = cut
        }
    }

    override fun write(b: Int) {
        armed = false
        real.write(b)
    }
}

private class FragSocket(private val raw: Socket) : Socket() {
    private val out by lazy { FragOutput(raw.getOutputStream()) }
    override fun getInputStream(): InputStream = raw.getInputStream()
    override fun getOutputStream(): OutputStream = out
    override fun isConnected(): Boolean = raw.isConnected
    override fun isClosed(): Boolean = raw.isClosed
    override fun isBound(): Boolean = raw.isBound
    override fun close() = raw.close()
    override fun getInetAddress() = raw.inetAddress
    override fun getLocalAddress() = raw.localAddress
    override fun getPort() = raw.port
    override fun getLocalPort() = raw.localPort
    override fun getRemoteSocketAddress(): SocketAddress = raw.remoteSocketAddress
    override fun getLocalSocketAddress(): SocketAddress = raw.localSocketAddress
    override fun setSoTimeout(timeout: Int) = raw.setSoTimeout(timeout)
    override fun getSoTimeout(): Int = raw.soTimeout
    override fun setTcpNoDelay(on: Boolean) = raw.setTcpNoDelay(on)
    override fun getTcpNoDelay(): Boolean = raw.tcpNoDelay
    override fun shutdownInput() = raw.shutdownInput()
    override fun shutdownOutput() = raw.shutdownOutput()
    override fun isInputShutdown(): Boolean = raw.isInputShutdown
    override fun isOutputShutdown(): Boolean = raw.isOutputShutdown
}

class WsLink(private val ssl: SSLSocket) {
    private val inp = BufferedInputStream(ssl.getInputStream())
    private val out = ssl.getOutputStream()
    private val rnd = SecureRandom()

    fun send(data: ByteArray, off: Int, len: Int, op: Int = 2) {
        val mask = ByteArray(4)
        rnd.nextBytes(mask)
        val h = ByteArrayOutputStream()
        h.write(0x80 or op)
        when {
            len < 126 -> h.write(0x80 or len)
            len < 65536 -> {
                h.write(0x80 or 126)
                h.write(len shr 8)
                h.write(len and 0xff)
            }
            else -> {
                h.write(0x80 or 127)
                for (s in 56 downTo 0 step 8) h.write(((len.toLong() shr s) and 0xff).toInt())
            }
        }
        h.write(mask)
        val body = ByteArray(len) { (data[off + it].toInt() xor mask[it % 4].toInt()).toByte() }
        synchronized(out) {
            out.write(h.toByteArray())
            out.write(body)
            out.flush()
        }
    }

    fun read(): ByteArray? {
        while (true) {
            val b0 = inp.read()
            val b1 = inp.read()
            if (b0 < 0 || b1 < 0) return null
            var len = (b1 and 0x7f).toLong()
            if (len == 126L) len = ((inp.read() shl 8) or inp.read()).toLong()
            else if (len == 127L) {
                len = 0
                repeat(8) { len = (len shl 8) or inp.read().toLong() }
            }
            val mask = if (b1 and 0x80 != 0) ByteArray(4).also { fill(inp, it) } else null
            val payload = ByteArray(len.toInt())
            fill(inp, payload)
            if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (b0 and 0x0f) {
                8 -> return null
                9 -> send(payload, 0, payload.size, 10)
                10 -> {}
                else -> return payload
            }
        }
    }

    private fun fill(i: InputStream, b: ByteArray) {
        var off = 0
        while (off < b.size) {
            val r = i.read(b, off, b.size - off)
            if (r < 0) throw java.io.EOFException()
            off += r
        }
    }

    fun close() {
        try {
            ssl.close()
        } catch (e: Exception) {
        }
    }
}

object TgWs {
    private val nets = listOf(
        "149.154.160.0" to 20, "91.108.4.0" to 22, "91.108.8.0" to 22, "91.108.12.0" to 22,
        "91.108.16.0" to 22, "91.108.20.0" to 22, "91.108.56.0" to 22, "95.161.64.0" to 20, "185.76.151.0" to 24
    ).map { ip(it.first) to (-1 shl (32 - it.second)) }
    private val fallback = mapOf(1 to "149.154.175.205", 2 to "149.154.167.220", 4 to "149.154.167.220")

    private fun ip(s: String): Int {
        val p = s.split('.')
        if (p.size != 4) return 0
        return (p[0].toInt() shl 24) or (p[1].toInt() shl 16) or (p[2].toInt() shl 8) or p[3].toInt()
    }

    fun isTelegram(addr: String): Boolean {
        if (addr.contains(':')) return false
        val v = try {
            ip(addr)
        } catch (e: Exception) {
            return false
        }
        return nets.any { (v and it.second) == (it.first and it.second) }
    }

    private fun dcOf(init: ByteArray): Int? {
        return try {
            val c = Cipher.getInstance("AES/CTR/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(init.copyOfRange(8, 40), "AES"), IvParameterSpec(init.copyOfRange(40, 56)))
            val plain = c.update(init)
            val tag = plain.copyOfRange(56, 60).joinToString("") { "%02x".format(it) }
            if (tag != "efefefef" && tag != "eeeeeeee" && tag != "dddddddd") return null
            var dc = Math.abs(((plain[61].toInt() shl 8) or (plain[60].toInt() and 0xff)).toShort().toInt())
            if (dc > 1000) dc -= 10000
            if (dc in 1..5) dc else null
        } catch (e: Exception) {
            null
        }
    }

    private fun domains(dc: Int): List<String> {
        val mine = listOf("kws$dc.web.telegram.org", "kws$dc-1.web.telegram.org")
        val others = (1..5).filter { it != dc }.flatMap { listOf("kws$it.web.telegram.org", "kws$it-1.web.telegram.org") }
        return mine + others.shuffled()
    }

    private fun handshake(raw: Socket, domain: String, ip: String, fragment: Boolean): SSLSocket {
        val carrier: Socket = if (fragment) FragSocket(raw) else raw
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(carrier, domain, 443, true) as SSLSocket
        ssl.soTimeout = 8000
        ssl.startHandshake()
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(domain, ssl.session)) throw java.io.IOException("ssl host mismatch")
        return ssl
    }

    private fun open(dc: Int): WsLink? {
        for (domain in domains(dc)) {
            val ip = Doh.resolve(domain) ?: fallback[dc] ?: continue
            var ssl: SSLSocket? = null
            try {
                val raw1 = Socket()
                raw1.connect(InetSocketAddress(ip, 443), 5000)
                ssl = try {
                    handshake(raw1, domain, ip, fragment = false)
                } catch (e: Exception) {
                    try {
                        raw1.close()
                    } catch (x: Exception) {
                    }
                    val raw2 = Socket()
                    raw2.connect(InetSocketAddress(ip, 443), 5000)
                    handshake(raw2, domain, ip, fragment = true)
                }
                val key = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val req = "GET /apiws HTTP/1.1\r\nHost: $domain\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: ${Base64.getEncoder().encodeToString(key)}\r\nSec-WebSocket-Version: 13\r\n" +
                    "Sec-WebSocket-Protocol: binary\r\nOrigin: https://web.telegram.org\r\nUser-Agent: Mozilla/5.0\r\n\r\n"
                ssl.getOutputStream().write(req.toByteArray())
                ssl.getOutputStream().flush()
                val head = StringBuilder()
                val i = ssl.getInputStream()
                while (!head.endsWith("\r\n\r\n") && head.length < 4096) {
                    val b = i.read()
                    if (b < 0) break
                    head.append(b.toChar())
                }
                if (!head.startsWith("HTTP/1.1 101")) {
                    ssl.close()
                    continue
                }
                ssl.soTimeout = 0
                return WsLink(ssl)
            } catch (e: Exception) {
                try {
                    ssl?.close()
                } catch (x: Exception) {
                }
            }
        }
        return null
    }

    fun bridge(client: Socket, ip: String, port: Int) {
        val ci = client.getInputStream()
        val co = client.getOutputStream()
        client.soTimeout = 4000
        val init = ByteArray(64)
        var off = 0
        try {
            while (off < 64) {
                val r = ci.read(init, off, 64 - off)
                if (r < 0) break
                off += r
            }
        } catch (e: Exception) {
        }
        client.soTimeout = 0
        if (off < 64) {
            try {
                client.close()
            } catch (e: Exception) {
            }
            return
        }
        val dc = dcOf(init)
        val ws = if (dc != null) open(dc) else null
        if (ws == null) {
            val remote = Socket()
            try {
                remote.connect(InetSocketAddress(ip, port), 7000)
                remote.getOutputStream().write(init)
                remote.getOutputStream().flush()
                DpiProxy.relay(client, remote)
            } catch (e: Exception) {
                try {
                    client.close()
                } catch (x: Exception) {
                }
            }
            return
        }
        try {
            ws.send(init, 0, 64)
            val t = Thread {
                try {
                    while (true) {
                        val f = ws.read() ?: break
                        co.write(f)
                        co.flush()
                    }
                } catch (e: Exception) {
                }
                try {
                    client.close()
                } catch (e: Exception) {
                }
                ws.close()
            }
            t.start()
            val buf = ByteArray(16384)
            while (true) {
                val n = ci.read(buf)
                if (n < 0) break
                ws.send(buf, 0, n)
            }
        } catch (e: Exception) {
        }
        ws.close()
        try {
            client.close()
        } catch (e: Exception) {
        }
    }
}
