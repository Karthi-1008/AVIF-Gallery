package com.prismtv.gallery.avif

import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import coil.size.Scale
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Teaches Coil to decode AVIF images (any Android version) using libavif + dav1d. */
class AvifCoilDecoder(
    private val source: SourceResult,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult? = gate.withPermit {
        val file = try {
            source.source.fileOrNull()?.toFile()
        } catch (e: Throwable) {
            null
        }

        val buf: ByteBuffer = if (file != null && file.exists() && file.length() > 0) {
            try {
                FileInputStream(file).channel.use { ch ->
                    ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
                }
            } catch (e: Throwable) {
                val bytes = source.source.source().readByteArray()
                val b = ByteBuffer.allocateDirect(bytes.size)
                b.put(bytes)
                b.rewind()
                b
            }
        } else {
            val bytes = source.source.source().readByteArray()
            val b = ByteBuffer.allocateDirect(bytes.size)
            b.put(bytes)
            b.rewind()
            b
        }

        val tw = (options.size.width as? Dimension.Pixels)?.px
        val th = (options.size.height as? Dimension.Pixels)?.px
        val res = Avif.decode(buf, tw, th, options.scale == Scale.FILL) ?: return@withPermit null
        DecodeResult(
            drawable = BitmapDrawable(options.context.resources, res.first),
            isSampled = res.second,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return try {
                val peek = result.source.source().peek()
                val head = ByteArray(64)
                val n = peek.read(head, 0, head.size)
                if (n >= 12 && Avif.sniff(head, n)) AvifCoilDecoder(result, options) else null
            } catch (e: Throwable) {
                null
            }
        }
    }

    companion object {
        // AVIF decoding is CPU and memory hungry; keep at most one running at once on Android TV.
        private val gate = Semaphore(1)
    }
}
