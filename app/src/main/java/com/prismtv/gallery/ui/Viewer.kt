package com.prismtv.gallery.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import coil.size.Precision
import androidx.compose.material3.Icon
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.prismtv.gallery.data.GalleryViewModel
import com.prismtv.gallery.data.Media
import com.prismtv.gallery.data.MediaInfo
import com.prismtv.gallery.data.ViewerRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as ExoMediaItem

private const val VIDEO_FRAME_KEY = "coil#video_frame_micros"

/** Per-photo view transform (zoom / rotation / pan). */
data class Xf(val zoom: Float = 1f, val rot: Int = 0, val panX: Float = 0f, val panY: Float = 0f)

private data class Ctl(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val focusMe: Boolean = false,
    val onClick: () -> Unit,
)

private data class Toast(val text: String, val id: Int)

private fun photoRequest(ctx: Context, m: Media, isZoomed: Boolean = false): ImageRequest {
    val dm = ctx.resources.displayMetrics
    // Bound decode target to the physical display resolution (max 1080p, min 720p)
    // to prevent decoding massive 48MP photos into 100MB bitmaps on TV RAM.
    val maxW = if (isZoomed) 2560 else min(dm.widthPixels, 1920).coerceAtLeast(1280)
    val maxH = if (isZoomed) 1440 else min(dm.heightPixels, 1080).coerceAtLeast(720)

    return ImageRequest.Builder(ctx)
        .data(m.model)
        .size(maxW, maxH)
        .precision(Precision.INEXACT)
        .scale(Scale.FIT)
        .allowRgb565(true)
        .placeholderMemoryCacheKey("thumb_${m.id}")
        .crossfade(200)
        .build()
}

private fun slideTransition(dir: Int, ms: Int): ContentTransform =
    (slideInHorizontally(tween(ms)) { w -> w * dir } + fadeIn(tween(ms))) togetherWith
        (slideOutHorizontally(tween(ms)) { w -> -w * dir } + fadeOut(tween(ms)))

private fun zoomTransition(ms: Int): ContentTransform =
    (fadeIn(tween(ms)) + scaleIn(tween(ms), initialScale = 0.85f)) togetherWith
        (fadeOut(tween(ms)) + scaleOut(tween(ms), targetScale = 1.15f))

private fun fadeTransition(ms: Int): ContentTransform =
    fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))

@Composable
private fun rememberPlayer(m: Media, loop: Boolean, speed: Float): ExoPlayer {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val p = remember(m.id) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(ExoMediaItem.fromUri(m.uri))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setPlaybackSpeed(speed)
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(loop) {
        p.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(speed) {
        p.setPlaybackSpeed(speed)
    }
    DisposableEffect(p) {
        onDispose { p.release() }
    }
    DisposableEffect(owner, p) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) p.pause()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return p
}

@Composable
fun Viewer(
    request: ViewerRequest,
    vm: GalleryViewModel,
    onClose: (String?) -> Unit,
) {
    val ctx = LocalContext.current
    val settings = vm.settings

    var list by remember { mutableStateOf(request.items) }
    var index by remember {
        mutableIntStateOf(request.index.coerceIn(0, (request.items.size - 1).coerceAtLeast(0)))
    }
    var dir by remember { mutableIntStateOf(1) }
    var playing by remember { mutableStateOf(request.slideshow) }
    var controls by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var lastKeyAt by remember { mutableLongStateOf(0L) }
    var stage by remember { mutableStateOf(IntSize.Zero) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var infoRows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val xfs = remember { mutableStateMapOf<String, Xf>() }
    val rootFocus = remember { FocusRequester() }
    val ctlFocus = remember { FocusRequester() }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val cur: Media? = list.getOrNull(index)
    if (cur == null) {
        LaunchedEffect(Unit) { onClose(null) }
        return
    }

    val player: ExoPlayer? = if (cur.isVideo) rememberPlayer(cur, settings.loopVideo, settings.playbackSpeed) else null
    var vPlaying by remember(cur.id) { mutableStateOf(false) }
    var vPos by remember(cur.id) { mutableLongStateOf(0L) }
    var vDur by remember(cur.id) { mutableLongStateOf(0L) }
    var vError by remember(cur.id) { mutableStateOf<String?>(null) }

    if (player != null) {
        DisposableEffect(player) {
            val l = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    vPlaying = isPlaying
                }

                override fun onPlayerError(error: PlaybackException) {
                    vError = error.errorCodeName
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        vPlaying = false
                        controls = true
                    }
                }
            }
            player.addListener(l)
            onDispose { player.removeListener(l) }
        }
        LaunchedEffect(player) {
            while (true) {
                vPos = player.currentPosition
                vDur = player.duration.coerceAtLeast(0L)
                delay(250)
            }
        }
    }

    // ---------------------------------------------------------------- actions
    fun say(t: String) {
        toast = Toast(t, (toast?.id ?: 0) + 1)
    }

    fun xfOf(m: Media): Xf = xfs[m.id] ?: Xf()

    fun updateXf(m: Media, f: (Xf) -> Xf) {
        xfs[m.id] = f(xfOf(m))
    }

    fun go(delta: Int) {
        val n = list.size
        if (n <= 1) return
        val ni = ((index + delta) % n + n) % n
        dir = if (delta >= 0) 1 else -1
        index = ni
        if (list[ni].isVideo) playing = false
    }

    fun pan(dx: Float, dy: Float) {
        updateXf(cur) { x ->
            val mx = stage.width * (x.zoom - 1f) / 2f + 60f
            val my = stage.height * (x.zoom - 1f) / 2f + 60f
            x.copy(
                panX = (x.panX + dx).coerceIn(-mx, mx),
                panY = (x.panY + dy).coerceIn(-my, my),
            )
        }
    }

    fun zoomBy(f: Float) {
        updateXf(cur) { x ->
            val z = (x.zoom * f).coerceIn(1f, 6f)
            if (z <= 1.02f) x.copy(zoom = 1f, panX = 0f, panY = 0f) else x.copy(zoom = z)
        }
        say("${(xfOf(cur).zoom * 100).roundToInt()}%")
    }

    fun seekBy(ms: Long) {
        val pl = player ?: return
        var target = (pl.currentPosition + ms).coerceAtLeast(0L)
        val d = pl.duration
        if (d > 0) target = target.coerceAtMost(d)
        pl.seekTo(target)
        say(if (ms > 0) "+${ms / 1000}s" else "${ms / 1000}s")
    }

    fun togglePlay() {
        if (cur.isVideo) {
            val pl = player ?: return
            if (pl.isPlaying) pl.pause() else {
                if (pl.playbackState == Player.STATE_ENDED) pl.seekTo(0)
                pl.play()
            }
        } else {
            playing = !playing
            say(if (playing) "Slideshow on" else "Slideshow paused")
        }
    }

    fun handleKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (showInfo || confirmDelete) return false
        val k = e.key
        when (k) {
            Key.MediaPlayPause -> {
                togglePlay(); return true
            }
            Key.MediaPlay -> {
                if (cur.isVideo) player?.play() else playing = true; return true
            }
            Key.MediaPause -> {
                if (cur.isVideo) player?.pause() else playing = false; return true
            }
            Key.MediaNext, Key.PageDown, Key.ChannelDown -> {
                go(1); return true
            }
            Key.MediaPrevious, Key.PageUp, Key.ChannelUp -> {
                go(-1); return true
            }
            Key.MediaFastForward -> {
                seekBy(30_000); return true
            }
            Key.MediaRewind -> {
                seekBy(-30_000); return true
            }
        }
        if (controls) return false
        val zoomed = !cur.isVideo && xfOf(cur).zoom > 1.02f
        return when (k) {
            Key.DirectionLeft -> {
                if (cur.isVideo) seekBy(-10_000) else if (zoomed) pan(160f, 0f) else go(-1)
                true
            }
            Key.DirectionRight -> {
                if (cur.isVideo) seekBy(10_000) else if (zoomed) pan(-160f, 0f) else go(1)
                true
            }
            Key.DirectionUp -> {
                if (zoomed) pan(0f, 160f) else controls = true
                true
            }
            Key.DirectionDown -> {
                if (zoomed) pan(0f, -160f) else controls = true
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                controls = true
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------- effects
    LaunchedEffect(Unit) {
        if (request.slideshow) say("Slideshow – press OK for controls")
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1400)
            toast = null
        }
    }

    // slideshow timer
    LaunchedEffect(playing, index, list.size, settings.intervalSec) {
        if (!playing) return@LaunchedEffect
        val c = list.getOrNull(index) ?: return@LaunchedEffect
        if (c.isVideo) {
            playing = false
            return@LaunchedEffect
        }
        delay(settings.intervalSec * 1000L)
        val n = list.size
        var i = index
        var found = -1
        for (step in 1..n) {
            i += 1
            if (i >= n) {
                if (!settings.loop) break
                i = 0
            }
            if (!list[i].isVideo) {
                found = i
                break
            }
        }
        if (found < 0) {
            playing = false
        } else if (found != index) {
            dir = 1
            index = found
        }
    }

    // Warm the cache with the next photo sequentially (avoids choking USB bandwidth with parallel decodes)
    LaunchedEffect(index, list.size) {
        delay(350)
        val next = list.getOrNull(index + 1) ?: (if (settings.loop) list.firstOrNull() else null)
        if (next != null && !next.isVideo) {
            ctx.imageLoader.enqueue(photoRequest(ctx, next))
        }
    }

    // auto-hide control bar
    LaunchedEffect(controls, lastKeyAt, vPlaying, cur.id, showInfo, confirmDelete) {
        if (controls && !showInfo && !confirmDelete && !(cur.isVideo && !vPlaying)) {
            delay(6000)
            controls = false
        }
    }

    // focus management: root when hidden, first button when shown
    LaunchedEffect(controls, showInfo, confirmDelete) {
        if (showInfo || confirmDelete) return@LaunchedEffect
        delay(if (controls) 70 else 100)
        try {
            (if (controls) ctlFocus else rootFocus).requestFocus()
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(showInfo, cur.id) {
        if (showInfo) {
            infoRows = emptyList()
            infoRows = withContext(Dispatchers.IO) { MediaInfo.rows(ctx, cur) }
        }
    }

    BackHandler {
        when {
            showInfo -> showInfo = false
            confirmDelete -> confirmDelete = false
            controls -> controls = false
            !cur.isVideo && (xfOf(cur) != Xf()) -> updateXf(cur) { Xf() }
            else -> onClose(cur.id)
        }
    }

    // ---------------------------------------------------------------- UI
    val fav = cur.id in vm.favorites

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { stage = it }
            .focusable()
            .focusRequester(rootFocus)
            .onPreviewKeyEvent {
                lastKeyAt = System.nanoTime()
                false
            }
            .onKeyEvent { handleKey(it) }
    ) {
        if (cur.isVideo && player != null) {
            VideoSurface(player)
            val err = vError
            if (err != null) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.BrokenImage, null, tint = Ui.TextLo, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This TV can't play this video",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(err, color = Ui.TextLo, fontSize = 14.sp)
                }
            }
        } else {
            AnimatedContent(
                targetState = cur,
                contentKey = { it.id },
                transitionSpec = {
                    when {
                        !playing -> slideTransition(dir, 320)
                        settings.effect == 1 -> slideTransition(dir, 650)
                        settings.effect == 2 -> zoomTransition(750)
                        else -> fadeTransition(750)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "photoSwitch",
            ) { m ->
                PhotoPage(
                    m = m,
                    xf = xfs[m.id] ?: Xf(),
                    slideshowActive = playing,
                    kenBurns = settings.kenBurns,
                    intervalSec = settings.intervalSec,
                )
            }
        }

        // ---- top info bar
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                    .padding(start = 48.dp, end = 48.dp, top = 26.dp, bottom = 46.dp)
            ) {
                Text(
                    cur.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${index + 1} / ${list.size}   •   ${MediaInfo.formatSize(cur.size)}",
                    color = Color(0xFFCBD0F5),
                    fontSize = 15.sp,
                )
            }
        }

        // ---- bottom control panel
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val ctls = ArrayList<Ctl>()
            ctls += Ctl(Icons.Rounded.SkipPrevious, "Previous") { go(-1) }
            if (cur.isVideo) {
                ctls += Ctl(Icons.Rounded.Replay10, "Back 10 s") { seekBy(-10_000) }
                ctls += Ctl(
                    if (vPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (vPlaying) "Pause" else "Play",
                    focusMe = true,
                ) { togglePlay() }
                ctls += Ctl(Icons.Rounded.Forward10, "Forward 10 s") { seekBy(10_000) }
            } else {
                ctls += Ctl(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Pause slideshow" else "Start slideshow",
                    active = playing,
                    focusMe = true,
                ) { togglePlay() }
            }
            ctls += Ctl(Icons.Rounded.SkipNext, "Next") { go(1) }
            if (!cur.isVideo) {
                ctls += Ctl(Icons.Rounded.RotateRight, "Rotate") {
                    updateXf(cur) { it.copy(rot = (it.rot + 90) % 360) }
                }
                ctls += Ctl(Icons.Rounded.ZoomIn, "Zoom in") { zoomBy(1.5f) }
                ctls += Ctl(Icons.Rounded.ZoomOut, "Zoom out") { zoomBy(1f / 1.5f) }
                ctls += Ctl(Icons.Rounded.FitScreen, "Reset view") { updateXf(cur) { Xf() } }
            }
            ctls += Ctl(
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (fav) "Remove from favorites" else "Add to favorites",
                active = fav,
            ) {
                vm.toggleFavorite(cur)
                say(if (fav) "Removed from favorites" else "Added to favorites")
            }
            if (cur.isVideo) {
                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                ctls += Ctl(Icons.Rounded.Speed, "Speed ${settings.playbackSpeed}x") {
                    val nextIdx = (speeds.indexOf(settings.playbackSpeed).takeIf { it >= 0 } ?: 1) + 1
                    val newSpeed = speeds[nextIdx % speeds.size]
                    vm.update { it.copy(playbackSpeed = newSpeed) }
                    say("Speed ${newSpeed}x")
                }
                ctls += Ctl(Icons.Rounded.Repeat, if (settings.loopVideo) "Loop: On" else "Loop: Off", active = settings.loopVideo) {
                    val newLoop = !settings.loopVideo
                    vm.update { it.copy(loopVideo = newLoop) }
                    say(if (newLoop) "Loop On" else "Loop Off")
                }
            }
            ctls += Ctl(Icons.Rounded.Info, "Details") { showInfo = true }
            if (request.allowDelete) {
                ctls += Ctl(Icons.Rounded.Delete, "Delete") { confirmDelete = true }
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (list.size > 1) {
                    ThumbnailFilmstrip(
                        items = list,
                        selectedIndex = index,
                        onSelectIndex = { newIdx ->
                            index = newIdx
                            if (list[newIdx].isVideo) playing = false
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                ControlPanel(
                    ctls = ctls,
                    focusRequester = ctlFocus,
                    progress = if (cur.isVideo) (if (vDur > 0) vPos.toFloat() / vDur else 0f) else null,
                    posText = MediaInfo.formatDuration(vPos),
                    durText = MediaInfo.formatDuration(vDur),
                )
            }
        }

        // ---- transient toast
        val t = toast
        if (t != null) {
            Text(
                t.text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC15172E))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }

        // ---- info panel
        if (showInfo) {
            InfoPanel(rows = infoRows, onClose = { showInfo = false })
        }

        // ---- delete confirmation
        if (confirmDelete) {
            ConfirmDelete(
                name = cur.name,
                onCancel = { confirmDelete = false },
                onConfirm = {
                    val target = cur
                    confirmDelete = false
                    vm.delete(target) { ok ->
                        if (ok) {
                            val newList = list.filter { it.id != target.id }
                            if (newList.isEmpty()) {
                                onClose(null)
                            } else {
                                list = newList
                                index = index.coerceAtMost(newList.lastIndex)
                            }
                            say("Deleted")
                        } else {
                            say("Couldn't delete this file")
                        }
                    }
                },
            )
        }
    }
}

// ------------------------------------------------------------------ photo page

@Composable
private fun PhotoPage(
    m: Media,
    xf: Xf,
    slideshowActive: Boolean,
    kenBurns: Boolean,
    intervalSec: Int,
) {
    val ctx = LocalContext.current
    var intrinsic by remember(m.id) { mutableStateOf(Size.Zero) }
    var failed by remember(m.id) { mutableStateOf(false) }
    val kb = remember(m.id) { Animatable(1f) }

    LaunchedEffect(m.id, slideshowActive, kenBurns) {
        if (slideshowActive && kenBurns) {
            kb.snapTo(1f)
            kb.animateTo(1.12f, tween(intervalSec * 1000 + 900, easing = LinearEasing))
        } else if (kb.value != 1f) {
            kb.animateTo(1f, tween(300))
        }
    }

    val isZoomed = xf.zoom > 1.05f
    val request = remember(m.id, isZoomed) { photoRequest(ctx, m, isZoomed) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val rotated = xf.rot % 180 != 0
        val rotScale: Float =
            if (rotated && intrinsic.width > 0f && intrinsic.height > 0f) {
                val fit = min(w / intrinsic.width, h / intrinsic.height)
                val dw = intrinsic.width * fit
                val dh = intrinsic.height * fit
                min(w / dh, h / dw)
            } else 1f

        AsyncImage(
            model = request,
            contentDescription = m.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = xf.zoom * rotScale * kb.value
                    scaleX = s
                    scaleY = s
                    rotationZ = xf.rot.toFloat()
                    translationX = xf.panX
                    translationY = xf.panY
                },
            onSuccess = { st ->
                intrinsic = st.painter.intrinsicSize
                failed = false
            },
            onError = { failed = true },
        )

        if (failed) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.BrokenImage, null, tint = Ui.TextLo, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Can't display this file",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(m.name, color = Ui.TextLo, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun VideoSurface(exo: ExoPlayer) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
                this.player = exo
            }
        },
        update = { it.player = exo },
        modifier = Modifier.fillMaxSize(),
    )
}

// ------------------------------------------------------------------ controls

@Composable
private fun ControlPanel(
    ctls: List<Ctl>,
    focusRequester: FocusRequester,
    progress: Float?,
    posText: String,
    durText: String,
) {
    val p = LocalPalette.current
    var label by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000))))
            .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(posText, color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(p.horizontal)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(durText, color = Color.White, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x66151730))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ctls.forEach { c ->
                CtlButton(
                    c = c,
                    fr = if (c.focusMe) focusRequester else null,
                    onFocus = { f -> if (f) label = c.label },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CtlButton(c: Ctl, fr: FocusRequester?, onFocus: (Boolean) -> Unit) {
    val p = LocalPalette.current
    FocusCard(
        modifier = Modifier.size(54.dp),
        shape = CircleShape,
        focusRequester = fr,
        focusedScale = 1.18f,
        borderWidth = 0.dp,
        onFocusChange = onFocus,
        onClick = c.onClick,
    ) { focused ->
        val bg: Brush = when {
            focused -> p.diagonal
            c.active -> SolidColor(Color.White.copy(alpha = 0.28f))
            else -> SolidColor(Color.White.copy(alpha = 0.10f))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                c.icon,
                c.label,
                tint = if (focused) Ui.Bg else Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun InfoPanel(rows: List<Pair<String, String>>, onClose: () -> Unit) {
    val p = LocalPalette.current
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try {
            closeFocus.requestFocus()
        } catch (e: Exception) {
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(470.dp)
                .background(Ui.Surface)
                .padding(horizontal = 30.dp, vertical = 26.dp)
        ) {
            Text(
                "Details",
                style = androidx.compose.ui.text.TextStyle(
                    brush = p.horizontal,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                ),
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (rows.isEmpty()) {
                    Text("Reading…", color = Ui.TextLo, fontSize = 16.sp)
                }
                rows.forEach { (k, v) ->
                    Text(k.uppercase(), color = p.c3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(v, color = Ui.TextHi, fontSize = 17.sp)
                    Spacer(Modifier.height(12.dp))
                }
            }
            PillButton(
                text = "Close",
                icon = null,
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                focusRequester = closeFocus,
            )
        }
    }
}

@Composable
private fun ConfirmDelete(name: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try {
            cancelFocus.requestFocus()
        } catch (e: Exception) {
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Ui.Surface)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF5C7C), modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(12.dp))
            Text("Delete this file?", color = Ui.TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                name,
                color = Ui.TextLo,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text("This can't be undone.", color = Ui.TextLo, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PillButton("Cancel", null, onCancel, focusRequester = cancelFocus)
                PillButton("Delete", Icons.Rounded.Delete, onConfirm, danger = true)
            }
        }
    }
}

@Composable
private fun ThumbnailFilmstrip(
    items: List<Media>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val ctx = LocalContext.current
    val p = LocalPalette.current

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 48.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
    ) {
        itemsIndexed(items, key = { _, m -> "strip_${m.id}" }) { idx, m ->
            val isSelected = idx == selectedIndex
            val req = remember(m.id, m.dateMillis) {
                ImageRequest.Builder(ctx)
                    .data(m.model)
                    .size(160, 160)
                    .precision(Precision.INEXACT)
                    .allowRgb565(true)
                    .placeholderMemoryCacheKey("thumb_${m.id}")
                    .diskCacheKey("thumb_${m.id}_${m.dateMillis}")
                    .apply { if (m.isVideo) setParameter(VIDEO_FRAME_KEY, 1_500_000L) }
                    .crossfade(true)
                    .build()
            }

            FocusCard(
                modifier = Modifier
                    .size(58.dp)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.15f,
                borderWidth = 0.dp,
                onClick = { onSelectIndex(idx) },
            ) { _ ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ui.SurfaceHi)
                ) {
                    AsyncImage(
                        model = req,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isSelected) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .border(3.dp, p.horizontal, RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
        }
    }
}
