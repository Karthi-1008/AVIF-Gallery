package com.prismtv.gallery.data

import android.content.Context

data class Settings(
    val palette: Int = 0,
    /** 0 newest, 1 oldest, 2 name A-Z, 3 largest */
    val sort: Int = 0,
    /** 0 small, 1 medium, 2 large */
    val thumb: Int = 1,
    val intervalSec: Int = 5,
    /** 0 fade, 1 slide, 2 zoom */
    val effect: Int = 0,
    val kenBurns: Boolean = true,
    val shuffle: Boolean = false,
    val loop: Boolean = true,
    val hideSmall: Boolean = true,
    val showNames: Boolean = false,
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            palette = sp.getInt("palette", d.palette),
            sort = sp.getInt("sort", d.sort),
            thumb = sp.getInt("thumb", d.thumb),
            intervalSec = sp.getInt("interval", d.intervalSec),
            effect = sp.getInt("effect", d.effect),
            kenBurns = sp.getBoolean("kenBurns", d.kenBurns),
            shuffle = sp.getBoolean("shuffle", d.shuffle),
            loop = sp.getBoolean("loop", d.loop),
            hideSmall = sp.getBoolean("hideSmall", d.hideSmall),
            showNames = sp.getBoolean("showNames", d.showNames),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putInt("palette", s.palette)
            .putInt("sort", s.sort)
            .putInt("thumb", s.thumb)
            .putInt("interval", s.intervalSec)
            .putInt("effect", s.effect)
            .putBoolean("kenBurns", s.kenBurns)
            .putBoolean("shuffle", s.shuffle)
            .putBoolean("loop", s.loop)
            .putBoolean("hideSmall", s.hideSmall)
            .putBoolean("showNames", s.showNames)
            .apply()
    }

    fun loadFavorites(): Set<String> = HashSet(sp.getStringSet("favorites", emptySet()) ?: emptySet())

    fun saveFavorites(favs: Set<String>) {
        sp.edit().putStringSet("favorites", HashSet(favs)).apply()
    }

    fun loadCustomUris(): Set<String> = HashSet(sp.getStringSet("custom_tree_uris", emptySet()) ?: emptySet())

    fun saveCustomUris(uris: Set<String>) {
        sp.edit().putStringSet("custom_tree_uris", HashSet(uris)).apply()
    }
}
