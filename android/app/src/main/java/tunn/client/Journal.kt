package tunn.client

import android.content.Context
import java.io.File

object J {
    @Volatile private var file: File? = null
    private var native = true

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "journal.log")
        file = f
        try {
            Native.journalInit(f.absolutePath)
        } catch (e: Throwable) {
            native = false
        }
    }

    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String) = add("E", tag, msg)

    private fun add(level: String, tag: String, msg: String) {
        if (native) {
            try {
                Native.journalAdd(level, tag, msg)
                return
            } catch (e: Throwable) {
                native = false
            }
        }
        try {
            val f = file ?: return
            if (f.length() > 192 * 1024) f.writeText(f.readLines().takeLast(800).joinToString("\n") + "\n")
            val t = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            f.appendText("$t $level [$tag] ${msg.replace('\n', ' ')}\n")
        } catch (e: Throwable) {
        }
    }

    fun read(lines: Int = 400): String {
        if (native) {
            try {
                return Native.journalRead(lines)
            } catch (e: Throwable) {
                native = false
            }
        }
        return try {
            file?.readLines()?.takeLast(lines)?.joinToString("\n") ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun clear() {
        try {
            if (native) Native.journalClear() else file?.writeText("")
        } catch (e: Throwable) {
        }
    }
}
