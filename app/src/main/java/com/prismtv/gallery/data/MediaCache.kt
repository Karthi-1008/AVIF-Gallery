package com.prismtv.gallery.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Ultra-fast persistent disk cache for scanned media metadata.
 * Enables zero-delay instant display of library and USB files upon opening.
 */
object MediaCache {
    private const val TAG = "MediaCache"
    private const val CACHE_FILE_NAME = "media_cache_v2.json"

    private fun getCacheFile(ctx: Context): File {
        return File(ctx.filesDir, CACHE_FILE_NAME)
    }

    suspend fun loadCache(ctx: Context): List<Media> = withContext(Dispatchers.IO) {
        val file = getCacheFile(ctx)
        if (!file.exists() || file.length() == 0L) return@withContext emptyList()
        val list = ArrayList<Media>(1024)
        try {
            val text = file.readText()
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val path = if (obj.has("path") && !obj.isNull("path")) obj.getString("path") else null
                val uriStr = obj.getString("uri")
                val name = obj.getString("name")
                val mime = obj.getString("mime")
                val isVideo = obj.optBoolean("isVideo", false)
                val size = obj.optLong("size", 0L)
                val dateMillis = obj.optLong("dateMillis", 0L)
                val bucketId = obj.optString("bucketId", "")
                val bucketName = obj.optString("bucketName", "Storage")
                val dateTakenMillis = obj.optLong("dateTakenMillis", dateMillis)
                val durationMs = obj.optLong("durationMs", 0L)

                list += Media(
                    id = id,
                    path = path,
                    uri = Uri.parse(uriStr),
                    name = name,
                    mime = mime,
                    isVideo = isVideo,
                    size = size,
                    dateMillis = dateMillis,
                    bucketId = bucketId,
                    bucketName = bucketName,
                    dateTakenMillis = dateTakenMillis,
                    durationMs = durationMs,
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed reading media cache", e)
        }
        list
    }

    suspend fun saveCache(ctx: Context, items: List<Media>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        try {
            val target = getCacheFile(ctx)
            val temp = File(ctx.filesDir, "$CACHE_FILE_NAME.tmp")
            val array = JSONArray()
            for (m in items) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("path", m.path)
                obj.put("uri", m.uri.toString())
                obj.put("name", m.name)
                obj.put("mime", m.mime)
                obj.put("isVideo", m.isVideo)
                obj.put("size", m.size)
                obj.put("dateMillis", m.dateMillis)
                obj.put("bucketId", m.bucketId)
                obj.put("bucketName", m.bucketName)
                obj.put("dateTakenMillis", m.dateTakenMillis)
                obj.put("durationMs", m.durationMs)
                array.put(obj)
            }
            FileOutputStream(temp).use { fos ->
                fos.write(array.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            if (temp.exists()) {
                if (target.exists()) target.delete()
                temp.renameTo(target)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed saving media cache", e)
        }
    }

    fun clearCache(ctx: Context): Boolean {
        return try {
            getCacheFile(ctx).delete()
        } catch (e: Exception) {
            false
        }
    }

    fun getCacheSizeBytes(ctx: Context): Long {
        val f = getCacheFile(ctx)
        return if (f.exists()) f.length() else 0L
    }
}
