package com.prismtv.gallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import android.os.Build
import com.prismtv.gallery.avif.AvifCoilDecoder

/** Application: configures the global Coil image loader with persistent disk cache & AVIF/GIF/Video decoding. */
class GalleryApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AvifCoilDecoder.Factory())          // AVIF on every Android version
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())        // thumbnails for videos
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_disk_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)   // 512 MB disk cache for instant USB thumbnail loads
                    .build()
            }
            .crossfade(true)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .build()
    }
}
