package com.prismtv.gallery.data

import android.content.Context

data class Settings(
    /** 0: OLED Pitch Black, 1: Obsidian Charcoal, 2: Midnight Sky, 3: Deep Slate, 4: Carbon Neutral */
    val darkThemeStyle: Int = 0,
    /** 0: Cyber Cyan, 1: Titanium Monochrome, 2: Electric Emerald, 3: Solar Gold, 4: Hyper Crimson, 5: Sunset Glow, 6: Royal Azure, 7: Neon Violet */
    val palette: Int = 0,
    /** 0: Date Taken (Newest), 1: Date Taken (Oldest), 2: Name A–Z, 3: Name Z–A, 4: Largest, 5: Smallest */
    val sort: Int = 0,
    /** 0: By Day, 1: By Month, 2: By Year, 3: None */
    val dateGrouping: Int = 0,
    /** 0: small, 1: medium, 2: large */
    val thumb: Int = 1,
    val intervalSec: Int = 5,
    /** 0 fade, 1 slide, 2 zoom */
    val effect: Int = 0,
    val kenBurns: Boolean = true,
    val shuffle: Boolean = false,
    val loop: Boolean = true,
    val hideSmall: Boolean = false,
    val showNames: Boolean = false,
    val useDateTaken: Boolean = true,
    val loopVideo: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val enableFastCache: Boolean = true,
    /** 0: Square (1:1), 1: Widescreen (16:10), 2: Standard Photo (4:3) */
    val gridAspect: Int = 0,
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            darkThemeStyle = sp.getInt("darkThemeStyle", d.darkThemeStyle),
            palette = sp.getInt("palette", d.palette),
            sort = sp.getInt("sort", d.sort),
            dateGrouping = sp.getInt("dateGrouping", d.dateGrouping),
            thumb = sp.getInt("thumb", d.thumb),
            intervalSec = sp.getInt("interval", d.intervalSec),
            effect = sp.getInt("effect", d.effect),
            kenBurns = sp.getBoolean("kenBurns", d.kenBurns),
            shuffle = sp.getBoolean("shuffle", d.shuffle),
            loop = sp.getBoolean("loop", d.loop),
            hideSmall = sp.getBoolean("hideSmall", d.hideSmall),
            showNames = sp.getBoolean("showNames", d.showNames),
            useDateTaken = sp.getBoolean("useDateTaken", d.useDateTaken),
            loopVideo = sp.getBoolean("loopVideo", d.loopVideo),
            playbackSpeed = sp.getFloat("playbackSpeed", d.playbackSpeed),
            enableFastCache = sp.getBoolean("enableFastCache", d.enableFastCache),
            gridAspect = sp.getInt("gridAspect", d.gridAspect),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putInt("darkThemeStyle", s.darkThemeStyle)
            .putInt("palette", s.palette)
            .putInt("sort", s.sort)
            .putInt("dateGrouping", s.dateGrouping)
            .putInt("thumb", s.thumb)
            .putInt("interval", s.intervalSec)
            .putInt("effect", s.effect)
            .putBoolean("kenBurns", s.kenBurns)
            .putBoolean("shuffle", s.shuffle)
            .putBoolean("loop", s.loop)
            .putBoolean("hideSmall", s.hideSmall)
            .putBoolean("showNames", s.showNames)
            .putBoolean("useDateTaken", s.useDateTaken)
            .putBoolean("loopVideo", s.loopVideo)
            .putFloat("playbackSpeed", s.playbackSpeed)
            .putBoolean("enableFastCache", s.enableFastCache)
            .putInt("gridAspect", s.gridAspect)
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
