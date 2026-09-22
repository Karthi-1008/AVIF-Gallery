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
    private var loader: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        val l = ImageLoader.Builder(this)
            .components {
                add(AvifCoilDecoder.Factory())          // AVIF on every Android version
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())        // thumbnails for videos
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)               // 18% of heap: preserves RAM on 1GB Android TVs
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_disk_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)   // 128 MB disk cache: prevents filling 2GB available storage
                    .build()
            }
            .crossfade(true)
            .allowRgb565(true)                          // Halves bitmap memory across the app
            .respectCacheHeaders(false)
            .build()
        loader = l
        return l
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Aggressively release in-memory bitmaps under OS RAM pressure to prevent TV OS reboots
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            loader?.memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        loader?.memoryCache?.clear()
    }
}
