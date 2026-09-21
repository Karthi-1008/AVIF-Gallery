package com.prismtv.gallery.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private var raw by mutableStateOf<List<Media>>(emptyList())

    var loading by mutableStateOf(false)
        private set

    var hasLoadedOnce by mutableStateOf(false)
        private set

    private val visible by derivedStateOf {
        val s = settings
        val filtered = raw.filter { it.isVideo || !s.hideSmall || it.size >= MIN_IMAGE_BYTES }
        when (s.sort) {
            1 -> filtered.sortedBy { it.dateMillis }
            2 -> filtered.sortedBy { it.name.lowercase() }
            3 -> filtered.sortedByDescending { it.size }
            else -> filtered.sortedByDescending { it.dateMillis }
        }
    }

    val photos: List<Media> by derivedStateOf { visible.filter { !it.isVideo } }
    val videos: List<Media> by derivedStateOf { visible.filter { it.isVideo } }
    val favoriteItems: List<Media> by derivedStateOf { visible.filter { it.id in favorites } }

    val albums: List<Album> by derivedStateOf {
        visible.groupBy { it.bucketId }
            .map { (id, list) -> Album(id, list.first().bucketName, list) }
            .sortedByDescending { a -> a.items.maxOf { it.dateMillis } }
    }

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val uris = customUris
            val result = withContext(Dispatchers.IO) { MediaScanner.scan(app, uris) }
            raw = result
            loading = false
            hasLoadedOnce = true
        }
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
            }
            onDone(ok)
        }
    }

    companion object {
        const val MIN_IMAGE_BYTES = 20_000L
    }
}
