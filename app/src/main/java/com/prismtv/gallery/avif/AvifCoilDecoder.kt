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

/** Teaches Coil to decode AVIF images (any Android version) using libavif + dav1d. */
class AvifCoilDecoder(
    private val source: SourceResult,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult? = gate.withPermit {
        val bytes = source.source.source().readByteArray()
        val tw = (options.size.width as? Dimension.Pixels)?.px
        val th = (options.size.height as? Dimension.Pixels)?.px
        val res = Avif.decode(bytes, tw, th, options.scale == Scale.FILL) ?: return@withPermit null
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
        // AVIF decoding is CPU and memory hungry; keep at most two running at once.
        private val gate = Semaphore(2)
    }
}
