package com.prismtv.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prismtv.gallery.data.Album
import com.prismtv.gallery.data.Media
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface GridEntry {
    val key: String
}

data class HeaderEntry(val title: String, val count: Int, val uid: Int) : GridEntry {
    override val key: String get() = "h:$uid:$title"
}

data class CellEntry(val media: Media, val index: Int) : GridEntry {
    override val key: String get() = media.id
}

private fun buildEntries(list: List<Media>, grouped: Boolean): List<GridEntry> {
    if (!grouped) return list.mapIndexed { i, m -> CellEntry(m, i) }
    val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val titles = list.map { fmt.format(Date(it.dateMillis)) }
    val counts = HashMap<String, Int>()
    titles.forEach { counts[it] = (counts[it] ?: 0) + 1 }
    val out = ArrayList<GridEntry>(list.size + 32)
    var last: String? = null
    var uid = 0
    list.forEachIndexed { i, m ->
        val t = titles[i]
        if (t != last) {
            out += HeaderEntry(t, counts[t] ?: 0, uid++)
            last = t
        }
        out += CellEntry(m, i)
    }
    return out
}

@Composable
fun MediaGrid(
    mediaList: List<Media>,
    state: LazyGridState,
    grouped: Boolean,
    cellMin: Dp,
    favorites: Set<String>,
    showNames: Boolean,
    memory: FocusMemory,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(mediaList, grouped) { buildEntries(mediaList, grouped) }
    val first = remember { FocusRequester() }

    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isEmpty()) return@LaunchedEffect
        val r = memory.restore
        if (r != null) {
            val idx = entries.indexOfFirst { it.key == r }
            if (idx >= 0) {
                state.scrollToItem(idx)
            } else {
                memory.restore = null
                delay(100)
                try {
                    first.requestFocus()
                } catch (e: Exception) {
                }
            }
        } else {
            delay(150)
            try {
                first.requestFocus()
            } catch (e: Exception) {
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellMin),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            entries,
            key = { it.key },
            span = { e -> if (e is HeaderEntry) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { e ->
            when (e) {
                is HeaderEntry -> MonthHeader(e)
                is CellEntry -> MediaCell(
                    m = e.media,
                    index = e.index,
                    fav = e.media.id in favorites,
                    showName = showNames,
                    memory = memory,
                    first = if (e.index == 0) first else null,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(h: HeaderEntry) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 5.dp, height = 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(p.vertical)
        )
        Spacer(Modifier.width(10.dp))
        Text(h.title, color = Ui.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text("${h.count}", color = Ui.TextLo, fontSize = 15.sp)
    }
}

private const val VIDEO_FRAME_KEY = "coil#video_frame_micros"

@Composable
private fun MediaCell(
    m: Media,
    index: Int,
    fav: Boolean,
    showName: Boolean,
    memory: FocusMemory,
    first: FocusRequester?,
    onOpen: (Int) -> Unit,
) {
    val fr = rememberCellFocus(m.id, memory, first)
    val ctx = LocalContext.current
    var failed by remember(m.id) { mutableStateOf(false) }
    val request = remember(m.id) {
        ImageRequest.Builder(ctx)
            .data(m.model)
            .apply { if (m.isVideo) setParameter(VIDEO_FRAME_KEY, 1_500_000L) }
            .crossfade(true)
            .build()
    }

    FocusCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(14.dp),
        focusRequester = fr,
        onFocusChange = { if (it) memory.last = m.id },
        onClick = { onOpen(index) },
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Ui.SurfaceHi)
        )
        AsyncImage(
            model = request,
            contentDescription = m.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { failed = false },
            onError = { failed = true },
        )
        if (failed) {
            Icon(
                Icons.Rounded.BrokenImage, null,
                tint = Ui.TextLo,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )
        }
        if (m.isVideo) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        val tag = when (m.ext) {
            "avif", "avifs" -> "AVIF"
            "gif" -> "GIF"
            "heic", "heif" -> "HEIC"
            else -> null
        }
        if (tag != null) {
            Text(
                tag,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (fav) {
            Icon(
                Icons.Rounded.Favorite, null,
                tint = Color(0xFFFF4D79),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp),
            )
        }
        if (focused || showName) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 8.dp)
            ) {
                Text(
                    m.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AlbumGrid(
    albums: List<Album>,
    state: LazyGridState,
    memory: FocusMemory,
    onOpen: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(albums.isNotEmpty()) {
        if (albums.isEmpty()) return@LaunchedEffect
        val r = memory.restore
        val idx = if (r != null) albums.indexOfFirst { "album:${it.id}" == r } else -1
        if (idx >= 0) {
            state.scrollToItem(idx)
        } else {
            memory.restore = null
            delay(150)
            try {
                first.requestFocus()
            } catch (e: Exception) {
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(albums, key = { it.id }) { a ->
            AlbumCell(a, memory, if (a.id == albums.first().id) first else null, onOpen)
        }
    }
}

@Composable
private fun AlbumCell(a: Album, memory: FocusMemory, first: FocusRequester?, onOpen: (Album) -> Unit) {
    val key = "album:${a.id}"
    val fr = rememberCellFocus(key, memory, first)
    val ctx = LocalContext.current
    val cover = a.cover
    val request = remember(cover.id) {
        ImageRequest.Builder(ctx)
            .data(cover.model)
            .apply { if (cover.isVideo) setParameter(VIDEO_FRAME_KEY, 1_500_000L) }
            .crossfade(true)
            .build()
    }
    FocusCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
        shape = RoundedCornerShape(18.dp),
        focusRequester = fr,
        onFocusChange = { if (it) memory.last = key },
        onClick = { onOpen(a) },
    ) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Ui.SurfaceHi)
        )
        AsyncImage(
            model = request,
            contentDescription = a.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val isUsb = a.name.startsWith("USB") || a.id.contains("tree") || a.id.startsWith("/mnt/media_rw")
        if (isUsb) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Usb, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("USB", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE05061A))))
                .padding(start = 14.dp, end = 14.dp, top = 34.dp, bottom = 12.dp)
        ) {
            Text(
                a.name,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parts = ArrayList<String>()
            if (a.photoCount > 0) parts += "${a.photoCount} photo" + if (a.photoCount == 1) "" else "s"
            if (a.videoCount > 0) parts += "${a.videoCount} video" + if (a.videoCount == 1) "" else "s"
            Text(parts.joinToString("  •  "), color = Color(0xFFCBD0F5), fontSize = 13.sp, maxLines = 1)
        }
    }
}
