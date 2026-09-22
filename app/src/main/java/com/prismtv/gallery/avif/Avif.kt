package com.prismtv.gallery.avif

import android.graphics.Bitmap
import org.aomedia.avif.android.AvifDecoder
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Thin wrapper around the bundled libavif/dav1d decoder.
 * Works on every Android version (Android 12+ has native AVIF, Android 11 and below do not).
 */
object Avif {

    /** True if the first bytes look like an AVIF (ISO-BMFF "ftyp" box with avif/avis brand). */
    fun sniff(head: ByteArray, len: Int = head.size): Boolean {
        if (len < 12) return false
        val ftyp = head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()
        if (!ftyp) return false
        val limit = min(len, 64)
        var i = 8
        while (i + 4 <= limit) {
            val a = head[i].toInt().toChar()
            val b = head[i + 1].toInt().toChar()
            val c = head[i + 2].toInt().toChar()
            val d = head[i + 3].toInt().toChar()
            if (a == 'a' && b == 'v' && c == 'i' && (d == 'f' || d == 's')) return true
            i += 4
        }
        return false
    }

    private fun direct(bytes: ByteArray): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(bytes.size)
        buf.put(bytes)
        buf.rewind()
        return buf
    }

    /** Returns width to height, or null when the data is not a decodable AVIF. */
    fun size(bytes: ByteArray): Pair<Int, Int>? = try {
        val buf = direct(bytes)
        val info = AvifDecoder.Info()
        if (AvifDecoder.getInfo(buf, buf.remaining(), info) && info.width > 0 && info.height > 0) {
            info.width to info.height
        } else null
    } catch (e: Throwable) {
        null
    }

    /**
     * Decodes an AVIF. When [targetW]/[targetH] are given the image is decoded straight into
     * a smaller bitmap (the native decoder scales it), which keeps TV memory usage low.
     * [cover] = true fills the target (crop), false fits inside it.
     */
    fun decode(bytes: ByteArray, targetW: Int?, targetH: Int?, cover: Boolean): Pair<Bitmap, Boolean>? {
        return try {
            val buf = direct(bytes)
            val info = AvifDecoder.Info()
            if (!AvifDecoder.getInfo(buf, buf.remaining(), info)) return null
            var w = info.width
            var h = info.height
            if (w <= 0 || h <= 0) return null
            var sampled = false
            if (targetW != null && targetH != null && targetW > 0 && targetH > 0) {
                val sx = targetW.toFloat() / w
                val sy = targetH.toFloat() / h
                val s = if (cover) max(sx, sy) else min(sx, sy)
                if (s < 1f) {
                    w = max(1, (w * s).roundToInt())
                    h = max(1, (h * s).roundToInt())
                    sampled = true
                }
            }
            // Guard against excessive memory usage on Android TVs (max ~2.5 MP / 1080p, ~8 MB RAM)
            while (w.toLong() * h > 2_500_000L) {
                w /= 2; h /= 2; sampled = true
            }
            var bmp: Bitmap? = null
            while (bmp == null && w >= 64 && h >= 64) {
                try {
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    w /= 2
                    h /= 2
                    sampled = true
                }
            }
            if (bmp == null || !AvifDecoder.decode(buf, buf.remaining(), bmp)) {
                bmp?.recycle()
                return null
            }
            bmp to sampled
        } catch (e: Throwable) {
            null
        }
    }
}
