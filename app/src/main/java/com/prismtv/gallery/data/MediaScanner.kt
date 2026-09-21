package com.prismtv.gallery.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns as Fc
import android.provider.MediaStore.MediaColumns as Mc
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Finds photos and videos three ways:
 *  1. Walks the real file system (internal storage + all mounted USB drives / SD cards). This is what
 *     makes .avif files show up on Android 11 TVs, where MediaStore does not index them.
 *  2. Queries MediaStore for anything the filesystem walk could not reach.
 *  3. Recursively scans user-selected USB drives / DocumentTrees added via Storage Access Framework (SAF).
 */
object MediaScanner {

    fun scan(ctx: Context, customTrees: Set<String> = emptySet()): List<Media> {
        val out = ArrayList<Media>(2048)
        val seen = HashSet<String>()
        val visited = HashSet<String>()

        try {
            for (root in storageRoots(ctx)) walk(root, true, 0, out, seen, visited)
        } catch (e: Throwable) {
        }
        try {
            queryMediaStore(ctx, out, seen)
        } catch (e: Throwable) {
        }
        try {
            scanCustomTrees(ctx, customTrees, out, seen)
        } catch (e: Throwable) {
        }
        return out
    }

    private fun storageRoots(ctx: Context): List<File> {
        val roots = LinkedHashSet<File>()

        // 0. Primary external storage (internal user flash: /storage/emulated/0)
        try {
            roots += Environment.getExternalStorageDirectory()
        } catch (e: Throwable) {
        }

        // 1. Direct scan of /storage (USB drives, OTG, SD cards)
        try {
            File("/storage").listFiles()?.forEach { f ->
                val name = f.name
                if (f.isDirectory && name != "self" && name != "emulated" && name != "knox-emulated") {
                    roots += f
                }
            }
        } catch (e: Throwable) {
        }

        // 2. Direct scan of /mnt/media_rw (common on Android TV 9/10/11 for USB drives)
        try {
            File("/mnt/media_rw").listFiles()?.forEach { f ->
                if (f.isDirectory && !f.name.startsWith(".")) roots += f
            }
        } catch (e: Throwable) {
        }

        // 3. Other typical Android TV USB mount directories
        val commonMounts = listOf(
            "/mnt/usbhost", "/mnt/usb", "/mnt/usb_storage", "/mnt/usbotg",
            "/mnt/sdcard", "/mnt/extsd", "/storage/usbdisk", "/storage/usbotg"
        )
        for (m in commonMounts) {
            try {
                val dir = File(m)
                if (dir.exists() && dir.isDirectory) {
                    val sub = dir.listFiles()
                    if (sub != null && sub.isNotEmpty()) {
                        sub.forEach { if (it.isDirectory) roots += it }
                    } else {
                        roots += dir
                    }
                }
            } catch (e: Throwable) {
            }
        }

        // 4. Android StorageManager API (API 24+)
        try {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sm.storageVolumes.forEach { vol ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory?.let { roots += it }
                    } else {
                        try {
                            val getPath = vol.javaClass.getMethod("getPath")
                            val path = getPath.invoke(vol) as? String
                            if (!path.isNullOrEmpty()) roots += File(path)
                        } catch (e: Throwable) {
                            try {
                                val getPathFile = vol.javaClass.getMethod("getPathFile")
                                val file = getPathFile.invoke(vol) as? File
                                if (file != null) roots += file
                            } catch (e2: Throwable) {
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
        }

        // 5. ContextCompat app external directories (derives parent root on mounted USB drives)
        try {
            ContextCompat.getExternalFilesDirs(ctx, null).forEach { d ->
                val p = d?.absolutePath ?: return@forEach
                val idx = p.indexOf("/Android/data")
                if (idx > 0) roots += File(p.substring(0, idx))
            }
            ContextCompat.getExternalCacheDirs(ctx).forEach { d ->
                val p = d?.absolutePath ?: return@forEach
                val idx = p.indexOf("/Android/data")
                if (idx > 0) roots += File(p.substring(0, idx))
            }
        } catch (e: Throwable) {
        }

        // 6. Linux /proc/mounts inspection (catches exotic vendor mount points)
        try {
            File("/proc/mounts").forEachLine { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val mountPoint = parts[1]
                    if ((mountPoint.startsWith("/storage/") || mountPoint.startsWith("/mnt/media_rw/") || mountPoint.startsWith("/mnt/usb")) &&
                        !mountPoint.startsWith("/storage/emulated") && !mountPoint.startsWith("/storage/self")) {
                        val dir = File(mountPoint)
                        if (dir.exists() && dir.isDirectory) {
                            roots += dir
                        }
                    }
                }
            }
        } catch (e: Throwable) {
        }

        return roots.toList()
    }

    private fun walk(
        dir: File,
        isRoot: Boolean,
        depth: Int,
        out: MutableList<Media>,
        seen: MutableSet<String>,
        visited: MutableSet<String>,
    ) {
        if (depth > 12) return
        val canon = try {
            dir.canonicalPath
        } catch (e: Exception) {
            dir.absolutePath
        }
        if (!visited.add(canon)) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return
        if (!isRoot && children.any { it.name == ".nomedia" }) return

        for (f in children) {
            val n = f.name
            if (n.startsWith(".")) continue
            if (f.isDirectory) {
                if (isRoot && (n == "Android" || n == "LOST.DIR")) continue
                walk(f, false, depth + 1, out, seen, visited)
            } else {
                val ext = n.substringAfterLast('.', "").lowercase()
                val isImg = ext in Formats.imageExt
                val isVid = ext in Formats.videoExt
                if (!isImg && !isVid) continue
                val path = f.absolutePath
                if (!seen.add(path)) continue
                val size = f.length()
                if (isVid && size < 100_000) continue
                out += Media(
                    id = path,
                    path = path,
                    uri = Uri.fromFile(f),
                    name = n,
                    mime = Formats.mimeFor(ext),
                    isVideo = isVid,
                    size = size,
                    dateMillis = f.lastModified(),
                    bucketId = dir.absolutePath,
                    bucketName = formatBucketName(dir),
                )
            }
        }
    }

    private fun formatBucketName(dir: File): String {
        val n = dir.name
        if (n.matches(Regex("^[0-9A-Za-z]{4}-[0-9A-Za-z]{4}$"))) {
            return "USB Drive ($n)"
        }
        if (dir.absolutePath.startsWith("/mnt/media_rw") || dir.absolutePath.startsWith("/mnt/usb")) {
            return if (n.isNotEmpty()) "USB - $n" else "USB Drive"
        }
        return n.ifEmpty { "Storage" }
    }

    private fun queryMediaStore(ctx: Context, out: MutableList<Media>, seen: MutableSet<String>) {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            BaseColumns._ID, Mc.DATA, Mc.DISPLAY_NAME, Mc.MIME_TYPE, Mc.SIZE, Mc.DATE_MODIFIED, Fc.MEDIA_TYPE
        )
        val selection = "${Fc.MEDIA_TYPE} IN (${Fc.MEDIA_TYPE_IMAGE},${Fc.MEDIA_TYPE_VIDEO})"
        ctx.contentResolver.query(uri, projection, selection, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(BaseColumns._ID)
            val iData = c.getColumnIndexOrThrow(Mc.DATA)
            val iName = c.getColumnIndexOrThrow(Mc.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(Mc.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(Mc.SIZE)
            val iDate = c.getColumnIndexOrThrow(Mc.DATE_MODIFIED)
            val iType = c.getColumnIndexOrThrow(Fc.MEDIA_TYPE)
            while (c.moveToNext()) {
                val data = c.getString(iData)
                if (data != null && seen.contains(data)) continue
                val isVideo = c.getInt(iType) == Fc.MEDIA_TYPE_VIDEO
                val name = c.getString(iName) ?: data?.let { File(it).name } ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in Formats.imageExt && ext !in Formats.videoExt) continue
                val mime = c.getString(iMime) ?: Formats.mimeFor(ext)
                val size = c.getLong(iSize)
                val date = c.getLong(iDate) * 1000L
                val readable = data != null && File(data).canRead()
                if (data != null) seen.add(data)
                val contentUri = ContentUris.withAppendedId(
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    c.getLong(iId)
                )
                val parent = data?.let { File(it).parentFile }
                out += Media(
                    id = if (readable) data!! else contentUri.toString(),
                    path = if (readable) data else null,
                    uri = if (readable) Uri.fromFile(File(data!!)) else contentUri,
                    name = name,
                    mime = mime,
                    isVideo = isVideo,
                    size = size,
                    dateMillis = date,
                    bucketId = parent?.absolutePath ?: "mediastore",
                    bucketName = parent?.name?.ifEmpty { "Storage" } ?: "Other",
                )
            }
        }
    }

    /**
     * Recursively scans user-selected SAF DocumentTrees (USB drives or folders picked via file picker).
     */
    private fun scanCustomTrees(
        ctx: Context,
        trees: Set<String>,
        out: MutableList<Media>,
        seen: MutableSet<String>,
    ) {
        if (trees.isEmpty()) return
        for (treeStr in trees) {
            val treeUri = try {
                Uri.parse(treeStr)
            } catch (e: Exception) {
                continue
            }
            try {
                val rootDoc = DocumentFile.fromTreeUri(ctx, treeUri) ?: continue
                val rootName = rootDoc.name?.takeIf { it.isNotEmpty() } ?: "USB Drive"
                walkDocumentTree(ctx, rootDoc, rootDoc, rootName, 0, out, seen)
            } catch (e: Throwable) {
            }
        }
    }

    private fun walkDocumentTree(
        ctx: Context,
        doc: DocumentFile,
        rootDoc: DocumentFile,
        rootName: String,
        depth: Int,
        out: MutableList<Media>,
        seen: MutableSet<String>,
    ) {
        if (depth > 12) return
        val children = try {
            doc.listFiles()
        } catch (e: Exception) {
            null
        } ?: return

        for (f in children) {
            val n = f.name ?: continue
            if (n.startsWith(".")) continue
            if (f.isDirectory) {
                if (depth == 0 && (n == "Android" || n == "LOST.DIR")) continue
                walkDocumentTree(ctx, f, rootDoc, rootName, depth + 1, out, seen)
            } else if (f.isFile) {
                val ext = n.substringAfterLast('.', "").lowercase()
                val isImg = ext in Formats.imageExt
                val isVid = ext in Formats.videoExt
                if (!isImg && !isVid) continue
                val uriStr = f.uri.toString()
                if (!seen.add(uriStr)) continue
                val size = f.length()
                if (isVid && size < 100_000) continue
                val mime = f.type ?: Formats.mimeFor(ext)
                val folderName = if (doc == rootDoc) rootName else (doc.name ?: rootName)
                out += Media(
                    id = uriStr,
                    path = null,
                    uri = f.uri,
                    name = n,
                    mime = mime,
                    isVideo = isVid,
                    size = size,
                    dateMillis = f.lastModified(),
                    bucketId = doc.uri.toString(),
                    bucketName = folderName,
                )
            }
        }
    }

    /** Deletes a file (legacy storage), SAF document, or via MediaStore, then tells MediaStore about it. */
    fun delete(ctx: Context, m: Media): Boolean {
        var ok = false
        m.path?.let { p ->
            ok = try {
                File(p).delete()
            } catch (e: Exception) {
                false
            }
            if (ok) {
                try {
                    MediaScannerConnection.scanFile(ctx, arrayOf(p), null, null)
                } catch (e: Throwable) {
                }
            }
        }
        if (!ok) {
            ok = try {
                DocumentsContract.deleteDocument(ctx.contentResolver, m.uri)
            } catch (e: Exception) {
                false
            }
        }
        if (!ok) {
            ok = try {
                ctx.contentResolver.delete(m.uri, null, null) > 0
            } catch (e: Exception) {
                false
            }
        }
        return ok
    }
}
