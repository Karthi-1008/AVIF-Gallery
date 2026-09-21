package com.prismtv.gallery.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.File

/** One photo or video shown in the gallery. */
@Immutable
data class Media(
    val id: String,
    val path: String?,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val size: Long,
    val dateMillis: Long,
    val bucketId: String,
    val bucketName: String,
) {
    /** What Coil should load: a File when we have a path, else the content Uri. */
    val model: Any get() = if (path != null) File(path) else uri
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

@Immutable
data class Album(
    val id: String,
    val name: String,
    val items: List<Media>,
) {
    val cover: Media get() = items.first()
    val photoCount: Int get() = items.count { !it.isVideo }
    val videoCount: Int get() = items.count { it.isVideo }
}

@Immutable
data class StorageDrive(
    val id: String,
    val path: File,
    val name: String,
    val isUsb: Boolean,
    val isPrimary: Boolean,
)

object Formats {
    val imageExt = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "webp", "gif", "bmp",
        "heic", "heif", "avif", "avifs"
    )
    val videoExt = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "3gp", "3g2", "ts", "m2ts", "mts",
        "mpg", "mpeg", "flv", "ogv", "avi", "wmv"
    )

    fun mimeFor(ext: String): String = when (ext) {
        "jpg", "jpeg", "jpe", "jfif" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "avif", "avifs" -> "image/avif"
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp", "3g2" -> "video/3gpp"
        "ts", "m2ts", "mts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "ogv" -> "video/ogg"
        "avi" -> "video/x-msvideo"
        "wmv" -> "video/x-ms-wmv"
        else -> "application/octet-stream"
    }
}

/** Small ephemeral request to open the full-screen viewer. */
class ViewerRequest(
    val items: List<Media>,
    val index: Int,
    val slideshow: Boolean = false,
    val allowDelete: Boolean = true,
    val external: Boolean = false,
)
