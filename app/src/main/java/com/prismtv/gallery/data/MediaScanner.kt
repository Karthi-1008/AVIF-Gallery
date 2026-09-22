package com.prismtv.gallery.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns as Mc
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Ultra-fast Media Scanner:
 *  1. Detects all storage drives (Internal Storage & USB OTG / Flash drives) with storage capacities.
 *  2. Walks real filesystem recursively with progressive batch streaming.
 *  3. Extracts true camera capture date & time from EXIF (TAG_DATETIME_ORIGINAL) and MediaStore.
 *  4. Queries MediaStore (Images & Videos) safely with duration & date-taken metadata.
 *  5. Scans user-selected SAF DocumentTrees if available.
 */
object MediaScanner {

    private val exifDateFmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    private fun getDriveStats(file: File): Pair<Long, Long> {
        return try {
            val stat = StatFs(file.absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            total to free
        } catch (e: Throwable) {
            0L to 0L
        }
    }

    fun getDetectedDrives(ctx: Context): List<StorageDrive> {
        val drives = LinkedHashMap<String, StorageDrive>()

        // 1. Primary internal storage
        try {
            val internal = Environment.getExternalStorageDirectory()
            if (internal != null && internal.exists()) {
                val (total, free) = getDriveStats(internal)
                drives[internal.absolutePath] = StorageDrive(
                    id = internal.absolutePath,
                    path = internal,
                    name = "Internal Storage",
                    isUsb = false,
                    isPrimary = true,
                    totalBytes = total,
                    freeBytes = free,
                )
            }
        } catch (e: Throwable) {
        }

        // 2. StorageManager volumes (API 24+)
        try {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sm.storageVolumes.forEach { vol ->
                    val file: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory
                    } else {
                        try {
                            vol.javaClass.getMethod("getPathFile").invoke(vol) as? File
                                ?: (vol.javaClass.getMethod("getPath").invoke(vol) as? String)?.let { File(it) }
                        } catch (e: Throwable) {
                            null
                        }
                    }
                    if (file != null && file.exists()) {
                        val isRemovable = vol.isRemovable || !vol.isPrimary
                        val desc = try {
                            vol.getDescription(ctx)
                        } catch (e: Throwable) {
                            ""
                        }
                        val displayName = when {
                            desc.isNotEmpty() -> desc
                            isRemovable -> "USB Drive (${file.name})"
                            else -> "Internal Storage"
                        }
                        val (total, free) = getDriveStats(file)
                        drives[file.absolutePath] = StorageDrive(
                            id = file.absolutePath,
                            path = file,
                            name = displayName,
                            isUsb = isRemovable,
                            isPrimary = vol.isPrimary,
                            totalBytes = total,
                            freeBytes = free,
                        )
                    }
                }
            }
        } catch (e: Throwable) {
        }

        // 3. Direct scan of /storage (USB drives, OTG, external volumes)
        try {
            File("/storage").listFiles()?.forEach { f ->
                val name = f.name
                if (f.isDirectory && name != "self" && name != "emulated" && name != "knox-emulated") {
                    if (!drives.containsKey(f.absolutePath)) {
                        val (total, free) = getDriveStats(f)
                        drives[f.absolutePath] = StorageDrive(
                            id = f.absolutePath,
                            path = f,
                            name = "USB Drive ($name)",
                            isUsb = true,
                            isPrimary = false,
                            totalBytes = total,
                            freeBytes = free,
                        )
                    }
                }
            }
        } catch (e: Throwable) {
        }

        // 4. Direct scan of /mnt/media_rw (standard mount point on many Android TVs)
        try {
            File("/mnt/media_rw").listFiles()?.forEach { f ->
                if (f.isDirectory && !f.name.startsWith(".")) {
                    if (!drives.containsKey(f.absolutePath)) {
                        val (total, free) = getDriveStats(f)
                        drives[f.absolutePath] = StorageDrive(
                            id = f.absolutePath,
                            path = f,
                            name = "USB Drive (${f.name})",
                            isUsb = true,
                            isPrimary = false,
                            totalBytes = total,
                            freeBytes = free,
                        )
                    }
                }
            }
        } catch (e: Throwable) {
        }

        // 5. Common USB mount points
        val common = listOf(
            "/mnt/usbhost", "/mnt/usb", "/mnt/usb_storage", "/mnt/usbotg",
            "/storage/usbdisk", "/storage/usbotg"
        )
        for (m in common) {
            try {
                val f = File(m)
                if (f.exists() && f.isDirectory) {
                    val sub = f.listFiles()
                    if (sub != null && sub.isNotEmpty()) {
                        sub.forEach { s ->
                            if (s.isDirectory && !drives.containsKey(s.absolutePath)) {
                                val (total, free) = getDriveStats(s)
                                drives[s.absolutePath] = StorageDrive(
                                    id = s.absolutePath,
                                    path = s,
                                    name = "USB (${s.name})",
                                    isUsb = true,
                                    isPrimary = false,
                                    totalBytes = total,
                                    freeBytes = free,
                                )
                            }
                        }
                    } else if (!drives.containsKey(f.absolutePath)) {
                        val (total, free) = getDriveStats(f)
                        drives[f.absolutePath] = StorageDrive(
                            id = f.absolutePath,
                            path = f,
                            name = "USB (${f.name})",
                            isUsb = true,
                            isPrimary = false,
                            totalBytes = total,
                            freeBytes = free,
                        )
                    }
                }
            } catch (e: Throwable) {
            }
        }

        // 6. ContextCompat external files dirs
        try {
            ContextCompat.getExternalFilesDirs(ctx, null).forEach { d ->
                val p = d?.absolutePath ?: return@forEach
                val idx = p.indexOf("/Android/data")
                if (idx > 0) {
                    val root = File(p.substring(0, idx))
                    if (root.exists() && !drives.containsKey(root.absolutePath)) {
                        val isInternal = root.absolutePath.contains("emulated")
                        val (total, free) = getDriveStats(root)
                        drives[root.absolutePath] = StorageDrive(
                            id = root.absolutePath,
                            path = root,
                            name = if (isInternal) "Internal Storage" else "USB Drive (${root.name})",
                            isUsb = !isInternal,
                            isPrimary = isInternal,
                            totalBytes = total,
                            freeBytes = free,
                        )
                    }
                }
            }
        } catch (e: Throwable) {
        }

        return drives.values.toList()
    }

    fun scan(
        ctx: Context,
        specificRoot: File? = null,
        customTrees: Set<String> = emptySet(),
        onProgressBatch: ((List<Media>) -> Unit)? = null,
    ): List<Media> {
        val out = ArrayList<Media>(2048)
        val seen = HashSet<String>()
        val visited = HashSet<String>()

        val roots = if (specificRoot != null) {
            listOf(specificRoot)
        } else {
            storageRoots(ctx)
        }

        var lastReported = 0

        for (root in roots) {
            try {
                walk(root, true, 0, out, seen, visited) {
                    if (onProgressBatch != null && out.size - lastReported >= 50) {
                        lastReported = out.size
                        onProgressBatch(ArrayList(out))
                    }
                }
            } catch (e: Throwable) {
            }
        }

        // Also query MediaStore if not scanning a specific USB drive
        if (specificRoot == null) {
            try {
                queryMediaStore(ctx, out, seen)
            } catch (e: Throwable) {
            }
            try {
                scanCustomTrees(ctx, customTrees, out, seen)
            } catch (e: Throwable) {
            }
        }

        if (onProgressBatch != null && out.size != lastReported) {
            onProgressBatch(ArrayList(out))
        }

        return out
    }

    private fun storageRoots(ctx: Context): List<File> {
        val roots = LinkedHashSet<File>()

        // Add all detected drives
        for (drive in getDetectedDrives(ctx)) {
            roots += drive.path
        }

        // Add standard public media folders on primary storage in case directory walk is restricted
        try {
            roots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            roots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            roots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            roots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (e: Throwable) {
        }

        // /proc/mounts inspection
        try {
            File("/proc/mounts").forEachLine { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val mountPoint = parts[1]
                    if ((mountPoint.startsWith("/storage/") || mountPoint.startsWith("/mnt/media_rw/") || mountPoint.startsWith("/mnt/usb")) &&
                        !mountPoint.startsWith("/storage/emulated") && !mountPoint.startsWith("/storage/self")
                    ) {
                        val dir = File(mountPoint)
                        if (dir.exists() && dir.isDirectory) {
                            roots += dir
                        }
                    }
                }
            }
        } catch (e: Throwable) {
        }

        return roots.filter { it.exists() && it.isDirectory }
    }

    private fun parseExifDateTaken(path: String, fallback: Long): Long {
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("jpg", "jpeg", "jpe", "jfif", "heic", "heif", "webp", "avif")) {
            return fallback
        }
        return try {
            val ex = ExifInterface(path)
            val dateStr = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: ex.getAttribute(ExifInterface.TAG_DATETIME)
            if (!dateStr.isNullOrBlank()) {
                exifDateFmt.parse(dateStr)?.time ?: fallback
            } else fallback
        } catch (e: Throwable) {
            fallback
        }
    }

    private fun walk(
        dir: File,
        isRoot: Boolean,
        depth: Int,
        out: MutableList<Media>,
        seen: MutableSet<String>,
        visited: MutableSet<String>,
        onItemAdded: () -> Unit,
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
                walk(f, false, depth + 1, out, seen, visited, onItemAdded)
            } else {
                val ext = n.substringAfterLast('.', "").lowercase()
                val isImg = ext in Formats.imageExt
                val isVid = ext in Formats.videoExt
                if (!isImg && !isVid) continue
                val path = f.absolutePath
                if (!seen.add(path)) continue
                val size = f.length()
                if (isVid && size < 50_000) continue

                val mod = f.lastModified()
                // Use file modification time for instantaneous scanning without blocking USB I/O
                val taken = mod

                out += Media(
                    id = path,
                    path = path,
                    uri = Uri.fromFile(f),
                    name = n,
                    mime = Formats.mimeFor(ext),
                    isVideo = isVid,
                    size = size,
                    dateMillis = mod,
                    bucketId = dir.absolutePath,
                    bucketName = formatBucketName(dir),
                    dateTakenMillis = taken,
                    durationMs = 0L,
                )
                onItemAdded()
            }
        }
    }

    private fun formatBucketName(dir: File): String {
        val n = dir.name
        val p = dir.absolutePath
        val isUsb = (p.startsWith("/storage/") && !p.startsWith("/storage/emulated") && !p.startsWith("/storage/self")) ||
                p.startsWith("/mnt/media_rw") || p.startsWith("/mnt/usb")

        if (n.matches(Regex("^[0-9A-Za-z]{4}-[0-9A-Za-z]{4}$"))) {
            return "USB Drive ($n)"
        }
        if (isUsb) {
            return if (n.isNotEmpty()) "USB • $n" else "USB Drive"
        }
        return n.ifEmpty { "Storage" }
    }

    private fun queryMediaStore(ctx: Context, out: MutableList<Media>, seen: MutableSet<String>) {
        queryMediaStoreUri(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, out, seen)
        queryMediaStoreUri(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, out, seen)
    }

    private fun queryMediaStoreUri(
        ctx: Context,
        contentUri: Uri,
        isVideo: Boolean,
        out: MutableList<Media>,
        seen: MutableSet<String>,
    ) {
        val projection = mutableListOf(
            BaseColumns._ID,
            Mc.DISPLAY_NAME,
            Mc.MIME_TYPE,
            Mc.SIZE,
            Mc.DATE_MODIFIED,
            Mc.DATA,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.DATE_TAKEN)
            if (isVideo) {
                projection.add(MediaStore.Video.VideoColumns.DURATION)
            }
        }
        try {
            ctx.contentResolver.query(contentUri, projection.toTypedArray(), null, null, null)?.use { c ->
                val iId = c.getColumnIndex(BaseColumns._ID)
                val iName = c.getColumnIndex(Mc.DISPLAY_NAME)
                val iMime = c.getColumnIndex(Mc.MIME_TYPE)
                val iSize = c.getColumnIndex(Mc.SIZE)
                val iDate = c.getColumnIndex(Mc.DATE_MODIFIED)
                val iData = c.getColumnIndex(Mc.DATA)
                val iTaken = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val iDur = if (isVideo) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

                while (c.moveToNext()) {
                    val id = if (iId >= 0) c.getLong(iId) else continue
                    val itemUri = ContentUris.withAppendedId(contentUri, id)
                    val data = if (iData >= 0) c.getString(iData) else null
                    val name = (if (iName >= 0) c.getString(iName) else null)
                        ?: data?.let { File(it).name }
                        ?: "Media_$id"
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in Formats.imageExt && ext !in Formats.videoExt) continue

                    val mime = (if (iMime >= 0) c.getString(iMime) else null) ?: Formats.mimeFor(ext)
                    val size = if (iSize >= 0) c.getLong(iSize) else 0L
                    val date = (if (iDate >= 0) c.getLong(iDate) else 0L) * 1000L
                    val taken = if (iTaken >= 0) c.getLong(iTaken) else 0L
                    val duration = if (iDur >= 0) c.getLong(iDur) else 0L

                    val dedupeKey = data ?: itemUri.toString()
                    if (!seen.add(dedupeKey)) continue

                    val readable = data != null && try {
                        File(data).canRead()
                    } catch (e: Exception) {
                        false
                    }
                    val parent = data?.let { File(it).parentFile }

                    val finalDate = if (date > 0) date else System.currentTimeMillis()
                    val finalTaken = if (taken > 0) taken else finalDate

                    out += Media(
                        id = if (readable) data!! else itemUri.toString(),
                        path = if (readable) data else null,
                        uri = if (readable) Uri.fromFile(File(data!!)) else itemUri,
                        name = name,
                        mime = mime,
                        isVideo = isVideo,
                        size = size,
                        dateMillis = finalDate,
                        bucketId = parent?.absolutePath ?: "mediastore",
                        bucketName = parent?.name?.ifEmpty { "Storage" } ?: "Storage",
                        dateTakenMillis = finalTaken,
                        durationMs = duration,
                    )
                }
            }
        } catch (e: Throwable) {
        }
    }

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
                if (isVid && size < 50_000) continue
                val mime = f.type ?: Formats.mimeFor(ext)
                val folderName = if (doc == rootDoc) rootName else (doc.name ?: rootName)
                val mod = f.lastModified()
                out += Media(
                    id = uriStr,
                    path = null,
                    uri = f.uri,
                    name = n,
                    mime = mime,
                    isVideo = isVid,
                    size = size,
                    dateMillis = mod,
                    bucketId = doc.uri.toString(),
                    bucketName = folderName,
                    dateTakenMillis = mod,
                    durationMs = 0L,
                )
            }
        }
    }

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
