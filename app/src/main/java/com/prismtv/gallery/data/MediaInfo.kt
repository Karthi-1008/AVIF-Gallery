package com.prismtv.gallery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.prismtv.gallery.avif.Avif
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Builds the rows shown in the "Info" panel. Runs on a background thread. */
object MediaInfo {

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun formatDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun rows(ctx: Context, m: Media): List<Pair<String, String>> {
        val rows = ArrayList<Pair<String, String>>()
        rows += "Name" to m.name
        m.path?.let { p -> File(p).parent?.let { rows += "Folder" to it } }
        rows += "Type" to m.mime
        rows += "Size" to formatSize(m.size)
        if (m.dateTakenMillis > 0) {
            rows += "Date Taken" to DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM)
                .format(Date(m.dateTakenMillis))
        }
        if (m.dateMillis > 0 && m.dateMillis != m.dateTakenMillis) {
            rows += "Modified" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(m.dateMillis))
        }
        try {
            if (m.isVideo) videoRows(ctx, m, rows) else imageRows(ctx, m, rows)
        } catch (e: Throwable) {
            // best effort only
        }
        return rows
    }

    private fun readBytes(ctx: Context, m: Media): ByteArray? = try {
        if (m.path != null) File(m.path).readBytes()
        else ctx.contentResolver.openInputStream(m.uri)?.use { it.readBytes() }
    } catch (e: Throwable) {
        null
    }

    private fun imageRows(ctx: Context, m: Media, rows: MutableList<Pair<String, String>>) {
        var dims: Pair<Int, Int>? = null
        val bytes = if (m.ext == "avif" || m.ext == "avifs") readBytes(ctx, m) else null
        if (bytes != null && Avif.sniff(bytes)) {
            dims = Avif.size(bytes)
        }
        if (dims == null) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (m.path != null) {
                BitmapFactory.decodeFile(m.path, o)
            } else {
                ctx.contentResolver.openInputStream(m.uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            }
            if (o.outWidth > 0 && o.outHeight > 0) dims = o.outWidth to o.outHeight
        }
        dims?.let {
            val mp = it.first.toLong() * it.second / 1_000_000.0
            rows += "Resolution" to "${it.first} × ${it.second}  (${String.format(Locale.US, "%.1f", mp)} MP)"
        }
        if (m.path != null && m.ext in setOf("jpg", "jpeg", "jpe", "jfif", "png", "webp", "heic", "heif")) {
            val ex = ExifInterface(m.path)
            val make = ex.getAttribute(ExifInterface.TAG_MAKE)
            val model = ex.getAttribute(ExifInterface.TAG_MODEL)
            val cam = listOfNotNull(make, model).joinToString(" ").trim()
            if (cam.isNotEmpty()) rows += "Camera" to cam
            ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { rows += "Taken" to it }
            ex.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { rows += "Aperture" to "f/$it" }
            ex.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { rows += "Exposure" to "$it s" }
            ex.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { rows += "Focal length" to it }
        }
    }

    private fun videoRows(ctx: Context, m: Media, rows: MutableList<Pair<String, String>>) {
        val r = MediaMetadataRetriever()
        try {
            if (m.path != null) r.setDataSource(m.path) else r.setDataSource(ctx, m.uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            if (w != null && h != null) rows += "Resolution" to "$w × $h"
            if (d != null) rows += "Duration" to formatDuration(d)
            if (br != null) rows += "Bitrate" to String.format(Locale.US, "%.1f Mbps", br / 1_000_000.0)
        } finally {
            try {
                r.release()
            } catch (e: Throwable) {
            }
        }
    }
}
