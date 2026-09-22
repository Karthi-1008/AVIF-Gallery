package com.prismtv.gallery.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    var settings by mutableStateOf(prefs.load())
        private set

    var favorites by mutableStateOf(prefs.loadFavorites())
        private set

    var customUris by mutableStateOf(prefs.loadCustomUris())
        private set

    var detectedDrives by mutableStateOf<List<StorageDrive>>(emptyList())
        private set

    private var raw by mutableStateOf<List<Media>>(emptyList())

    var loading by mutableStateOf(false)
        private set

    var hasLoadedOnce by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")

    var activeFilter by mutableStateOf(MediaFilter.ALL)

    var selectedDriveId by mutableStateOf<String?>(null)

    // Multi-selection state
    var selectionMode by mutableStateOf(false)
    var selectedIds by mutableStateOf<Set<String>>(emptySet())

    init {
        // Instant startup: load cached media instantly from disk cache (< 30ms)
        if (settings.enableFastCache) {
            viewModelScope.launch {
                val cached = MediaCache.loadCache(app)
                if (cached.isNotEmpty()) {
                    raw = cached
                    hasLoadedOnce = true
                }
                // Also load detected drives quickly
                val drives = withContext(Dispatchers.IO) { MediaScanner.getDetectedDrives(app) }
                detectedDrives = drives
                // Background scan for any new/deleted items
                refreshInternal(silent = cached.isNotEmpty())
            }
        } else {
            refresh()
        }
    }

    private val visible by derivedStateOf {
        val s = settings
        val query = searchQuery.trim().lowercase()
        val filter = activeFilter
        val driveId = selectedDriveId

        var list = raw.filter { item ->
            // Filter out tiny images if requested
            if (!item.isVideo && s.hideSmall && item.size < MIN_IMAGE_BYTES) return@filter false
            // Drive filter
            if (driveId != null) {
                val path = item.path ?: ""
                val bucket = item.bucketId
                if (!path.startsWith(driveId) && !bucket.startsWith(driveId)) return@filter false
            }
            // Media type filter
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !item.isVideo
                MediaFilter.VIDEOS -> item.isVideo
                MediaFilter.AVIF -> item.ext == "avif" || item.ext == "avifs"
                MediaFilter.FAVORITES -> item.id in favorites
            }
        }

        // Search query filter
        if (query.isNotEmpty()) {
            list = list.filter {
                it.name.lowercase().contains(query) ||
                it.bucketName.lowercase().contains(query) ||
                it.ext.contains(query)
            }
        }

        // Sort order
        when (s.sort) {
            1 -> list.sortedBy { if (s.useDateTaken) it.dateTakenMillis else it.dateMillis }
            2 -> list.sortedBy { it.name.lowercase() }
            3 -> list.sortedByDescending { it.name.lowercase() }
            4 -> list.sortedByDescending { it.size }
            5 -> list.sortedBy { it.size }
            else -> list.sortedByDescending { if (s.useDateTaken) it.dateTakenMillis else it.dateMillis }
        }
    }

    val photos: List<Media> by derivedStateOf { visible.filter { !it.isVideo } }
    val videos: List<Media> by derivedStateOf { visible.filter { it.isVideo } }
    val favoriteItems: List<Media> by derivedStateOf { visible.filter { it.id in favorites } }

    val albums: List<Album> by derivedStateOf {
        visible.groupBy { it.bucketId }
            .map { (id, list) -> Album(id, list.first().bucketName, list) }
            .sortedByDescending { a -> a.items.maxOfOrNull { it.dateTakenMillis } ?: 0L }
    }

    fun refresh() {
        if (loading) return
        refreshInternal(silent = false)
    }

    private fun refreshInternal(silent: Boolean = false) {
        if (!silent) loading = true
        viewModelScope.launch {
            val drives = withContext(Dispatchers.IO) { MediaScanner.getDetectedDrives(app) }
            detectedDrives = drives
            val uris = customUris

            val result = withContext(Dispatchers.IO) {
                MediaScanner.scan(app, null, uris) { batch ->
                    // Progressive loading: update raw list in chunks as they are discovered
                    if (raw.isEmpty()) {
                        raw = batch
                    }
                }
            }
            raw = result
            loading = false
            hasLoadedOnce = true

            // Persist to disk cache for instantaneous subsequent launches
            if (settings.enableFastCache) {
                MediaCache.saveCache(app, result)
            }
        }
    }

    fun scanSpecificDrive(drive: StorageDrive) {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaScanner.scan(app, drive.path, emptySet()) { batch ->
                    if (raw.isEmpty()) raw = batch
                }
            }
            raw = result
            loading = false
            hasLoadedOnce = true
            if (settings.enableFastCache) {
                MediaCache.saveCache(app, result)
            }
        }
    }

    fun toggleSelection(m: Media) {
        selectedIds = if (m.id in selectedIds) selectedIds - m.id else selectedIds + m.id
        if (selectedIds.isEmpty()) {
            selectionMode = false
        }
    }

    fun selectAll(list: List<Media>) {
        selectedIds = list.map { it.id }.toSet()
        selectionMode = true
    }

    fun clearSelection() {
        selectedIds = emptySet()
        selectionMode = false
    }

    fun batchFavorite() {
        val allFav = selectedIds.all { it in favorites }
        favorites = if (allFav) favorites - selectedIds else favorites + selectedIds
        prefs.saveFavorites(favorites)
        clearSelection()
    }

    fun batchDelete(onDone: (Int) -> Unit) {
        val targets = raw.filter { it.id in selectedIds }
        if (targets.isEmpty()) {
            clearSelection()
            onDone(0)
            return
        }
        viewModelScope.launch {
            var deletedCount = 0
            withContext(Dispatchers.IO) {
                for (m in targets) {
                    if (MediaScanner.delete(app, m)) {
                        deletedCount++
                    }
                }
            }
            raw = raw.filter { it.id !in selectedIds }
            favorites = favorites - selectedIds
            prefs.saveFavorites(favorites)
            clearSelection()
            if (settings.enableFastCache) {
                MediaCache.saveCache(app, raw)
            }
            onDone(deletedCount)
        }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Coil.imageLoader(app).diskCache?.clear()
                Coil.imageLoader(app).memoryCache?.clear()
            } catch (e: Throwable) {
            }
        }
    }

    fun clearMediaCache() {
        MediaCache.clearCache(app)
        refresh()
    }

    fun addCustomTreeUri(uri: Uri) {
        val set = customUris + uri.toString()
        customUris = set
        prefs.saveCustomUris(set)
        refresh()
    }

    fun removeCustomTreeUri(uriStr: String) {
        val set = customUris - uriStr
        customUris = set
        prefs.saveCustomUris(set)
        refresh()
    }

    fun clearCustomTreeUris() {
        customUris = emptySet()
        prefs.saveCustomUris(emptySet())
        refresh()
    }

    fun update(block: (Settings) -> Settings) {
        settings = block(settings)
        prefs.save(settings)
    }

    fun toggleFavorite(m: Media) {
        favorites = if (m.id in favorites) favorites - m.id else favorites + m.id
        prefs.saveFavorites(favorites)
    }

    fun delete(m: Media, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { MediaScanner.delete(app, m) }
            if (ok) {
                raw = raw.filter { it.id != m.id }
                if (m.id in favorites) {
                    favorites = favorites - m.id
                    prefs.saveFavorites(favorites)
                }
                if (settings.enableFastCache) {
                    MediaCache.saveCache(app, raw)
                }
            }
            onDone(ok)
        }
    }

    companion object {
        const val MIN_IMAGE_BYTES = 5_000L
    }
}
