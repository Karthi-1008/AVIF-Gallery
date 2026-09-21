package com.prismtv.gallery.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.gallery.data.Album
import com.prismtv.gallery.data.GalleryViewModel
import com.prismtv.gallery.data.Media
import com.prismtv.gallery.data.ViewerRequest
import kotlinx.coroutines.delay

enum class Section { PHOTOS, ALBUMS, VIDEOS, FAVORITES, SETTINGS }

private val NAV = listOf(
    NavItem("PHOTOS", "Photos", Icons.Rounded.PhotoLibrary),
    NavItem("ALBUMS", "Albums", Icons.Rounded.Folder),
    NavItem("VIDEOS", "Videos", Icons.Rounded.VideoLibrary),
    NavItem("FAVORITES", "Favorites", Icons.Rounded.Favorite),
    NavItem("USB", "Select USB", Icons.Rounded.Usb),
    NavItem("SLIDESHOW", "Slideshow", Icons.Rounded.Slideshow),
    NavItem("REFRESH", "Rescan", Icons.Rounded.Refresh),
    NavItem("SETTINGS", "Settings", Icons.Rounded.Settings),
)

@Composable
fun PrismRoot(
    vm: GalleryViewModel,
    permissionGranted: Boolean,
    hasAllFilesAccess: Boolean,
    externalMedia: Media?,
    onRequestPermission: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSelectUsbFolder: () -> Unit,
    onExternalClosed: () -> Unit,
    onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings = vm.settings

    var section by remember { mutableStateOf(Section.PHOTOS) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var lastBackAt by remember { mutableStateOf(0L) }
    val memory = remember { FocusMemory() }

    val photosState = rememberLazyGridState()
    val videosState = rememberLazyGridState()
    val favState = rememberLazyGridState()
    val albumsState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
        AppBackground()

        val ext = externalMedia
        val v = viewer
        when {
            ext != null -> {
                Viewer(
                    request = ViewerRequest(listOf(ext), 0, allowDelete = false, external = true),
                    vm = vm,
                    onClose = { onExternalClosed() },
                )
            }
            !permissionGranted -> {
                PermissionScreen(
                    onRequestPermission = onRequestPermission,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onOpenAppSettings = onOpenAppSettings,
                    hasAllFilesAccess = hasAllFilesAccess,
                )
            }
            v != null -> {
                Viewer(
                    request = v,
                    vm = vm,
                    onClose = { lastId ->
                        memory.restore = lastId
                        viewer = null
                    },
                )
            }
            else -> {
                BackHandler {
                    val a = openAlbum
                    when {
                        showStorageDialog -> showStorageDialog = false
                        a != null -> {
                            memory.restore = "album:${a.id}"
                            openAlbum = null
                        }
                        section != Section.PHOTOS -> section = Section.PHOTOS
                        else -> {
                            val now = System.currentTimeMillis()
                            if (now - lastBackAt < 2500) {
                                onExit()
                            } else {
                                lastBackAt = now
                                Toast.makeText(ctx, "Press Back again to exit", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val cellMin = when (settings.thumb) {
                    0 -> 120.dp
                    2 -> 210.dp
                    else -> 160.dp
                }
                val grouped = settings.sort == 0 || settings.sort == 1

                fun openViewer(list: List<Media>, index: Int) {
                    viewer = ViewerRequest(list, index)
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = RailCollapsed + 6.dp, end = 34.dp, top = 22.dp, bottom = 10.dp)
                ) {
                    when (section) {
                        Section.PHOTOS -> LibraryScreen(
                            title = "Photos",
                            subtitle = countText(vm.photos.size, "photo") +
                                if (vm.videos.isNotEmpty()) "  •  " + countText(vm.videos.size, "video") else "",
                            list = vm.photos,
                            loading = vm.loading || !vm.hasLoadedOnce,
                            emptyIcon = Icons.Rounded.PhotoLibrary,
                            emptyTitle = "No photos found",
                            emptyText = "Copy pictures to this TV, plug in a USB drive, or choose Select USB.",
                            state = photosState,
                            grouped = grouped,
                            cellMin = cellMin,
                            vm = vm,
                            memory = memory,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onOpen = { i -> openViewer(vm.photos, i) },
                            onSelectUsb = { showStorageDialog = true },
                        )
                        Section.VIDEOS -> LibraryScreen(
                            title = "Videos",
                            subtitle = countText(vm.videos.size, "video"),
                            list = vm.videos,
                            loading = vm.loading || !vm.hasLoadedOnce,
                            emptyIcon = Icons.Rounded.Movie,
                            emptyTitle = "No videos found",
                            emptyText = "MP4, MKV, WebM, MOV, TS and more are supported.",
                            state = videosState,
                            grouped = grouped,
                            cellMin = cellMin,
                            vm = vm,
                            memory = memory,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onOpen = { i -> openViewer(vm.videos, i) },
                            onSelectUsb = { showStorageDialog = true },
                        )
                        Section.FAVORITES -> LibraryScreen(
                            title = "Favorites",
                            subtitle = countText(vm.favoriteItems.size, "item"),
                            list = vm.favoriteItems,
                            loading = false,
                            emptyIcon = Icons.Rounded.Favorite,
                            emptyTitle = "No favorites yet",
                            emptyText = "Open a photo, press OK and tap the heart to add it here.",
                            state = favState,
                            grouped = grouped,
                            cellMin = cellMin,
                            vm = vm,
                            memory = memory,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onOpen = { i -> openViewer(vm.favoriteItems, i) },
                            onSelectUsb = null,
                        )
                        Section.ALBUMS -> {
                            val a = openAlbum
                            if (a == null) {
                                Column(Modifier.fillMaxSize()) {
                                    ScreenHeader(
                                        title = "Albums",
                                        subtitle = countText(vm.albums.size, "album"),
                                        trailing = {
                                            PillButton("Select USB / Storage", Icons.Rounded.Usb, {
                                                showStorageDialog = true
                                            })
                                        }
                                    )
                                    if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        UsbPermissionBanner(onRequestAllFilesAccess)
                                    }
                                    when {
                                        (vm.loading || !vm.hasLoadedOnce) && vm.albums.isEmpty() -> LoadingView()
                                        vm.albums.isEmpty() -> CenterMessage(
                                            icon = Icons.Rounded.Folder,
                                            title = "No albums yet",
                                            subtitle = "Folders on internal storage and USB drives will show up here.",
                                        ) {
                                            PillButton("Select USB Drive / Storage", Icons.Rounded.Usb, {
                                                showStorageDialog = true
                                            })
                                        }
                                        else -> AlbumGrid(
                                            albums = vm.albums,
                                            state = albumsState,
                                            memory = memory,
                                            onOpen = { openAlbum = it },
                                        )
                                    }
                                }
                            } else {
                                // keep the latest content of the album (after deletes)
                                val fresh = vm.albums.firstOrNull { it.id == a.id }
                                key(a.id) {
                                    val detailState = rememberLazyGridState()
                                    LibraryScreen(
                                        title = a.name,
                                        subtitle = countText(fresh?.items?.size ?: 0, "item"),
                                        list = fresh?.items ?: emptyList(),
                                        loading = false,
                                        emptyIcon = Icons.Rounded.Folder,
                                        emptyTitle = "This album is empty",
                                        emptyText = "Press Back to return to your albums.",
                                        state = detailState,
                                        grouped = grouped,
                                        cellMin = cellMin,
                                        vm = vm,
                                        memory = memory,
                                        hasAllFilesAccess = hasAllFilesAccess,
                                        onRequestAllFilesAccess = onRequestAllFilesAccess,
                                        onOpen = { i -> openViewer(fresh?.items ?: emptyList(), i) },
                                        onSelectUsb = null,
                                    )
                                }
                            }
                        }
                        Section.SETTINGS -> SettingsScreen(
                            vm = vm,
                            onSelectUsbFolder = { showStorageDialog = true },
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                        )
                    }
                }

                NavRail(
                    items = NAV,
                    selectedKey = section.name,
                    onSelect = { k ->
                        when (k) {
                            "USB" -> {
                                showStorageDialog = true
                            }
                            "SLIDESHOW" -> {
                                val imgs = vm.photos
                                if (imgs.isEmpty()) {
                                    Toast.makeText(ctx, "No photos to show", Toast.LENGTH_SHORT).show()
                                } else {
                                    val ordered = if (settings.shuffle) imgs.shuffled() else imgs
                                    viewer = ViewerRequest(ordered, 0, slideshow = true)
                                }
                            }
                            "REFRESH" -> {
                                vm.refresh()
                                Toast.makeText(ctx, "Scanning library & USB drives…", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                val s = Section.valueOf(k)
                                if (s != section) {
                                    openAlbum = null
                                    section = s
                                } else if (s == Section.ALBUMS) {
                                    openAlbum = null
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }

        if (showStorageDialog) {
            StorageDialog(
                drives = vm.detectedDrives,
                hasAllFilesAccess = hasAllFilesAccess,
                onDismiss = { showStorageDialog = false },
                onSelectDrive = { drive ->
                    vm.scanSpecificDrive(drive)
                    Toast.makeText(ctx, "Scanning ${drive.name}…", Toast.LENGTH_SHORT).show()
                },
                onScanAll = {
                    vm.refresh()
                    Toast.makeText(ctx, "Scanning all storage & USB drives…", Toast.LENGTH_SHORT).show()
                },
                onRequestAllFilesAccess = onRequestAllFilesAccess,
            )
        }
    }
}

private fun countText(n: Int, noun: String): String =
    "%,d %s%s".format(n, noun, if (n == 1) "" else "s")

@Composable
private fun UsbPermissionBanner(onRequestAllFilesAccess: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33FFB300))
            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Usb, null, tint = Color(0xFFFFCC00), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "USB drives require \"All Files Access\" on Android TV.",
                    color = Color(0xFFFFCC00),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            PillButton("Allow in Settings", Icons.Rounded.Settings, onRequestAllFilesAccess)
        }
    }
}

@Composable
private fun LibraryScreen(
    title: String,
    subtitle: String,
    list: List<Media>,
    loading: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyText: String,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    grouped: Boolean,
    cellMin: androidx.compose.ui.unit.Dp,
    vm: GalleryViewModel,
    memory: FocusMemory,
    hasAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onOpen: (Int) -> Unit,
    onSelectUsb: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, subtitle)
        if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            UsbPermissionBanner(onRequestAllFilesAccess)
        }
        when {
            list.isEmpty() && loading -> LoadingView()
            list.isEmpty() -> CenterMessage(emptyIcon, emptyTitle, emptyText) {
                if (onSelectUsb != null) {
                    PillButton("Select USB / Storage", Icons.Rounded.Usb, onSelectUsb)
                }
            }
            else -> MediaGrid(
                mediaList = list,
                state = state,
                grouped = grouped,
                cellMin = cellMin,
                favorites = vm.favorites,
                showNames = vm.settings.showNames,
                memory = memory,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun PermissionScreen(
    onRequestPermission: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    hasAllFilesAccess: Boolean,
) {
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        delay(300)
        try {
            first.requestFocus()
        } catch (e: Exception) {
        }
    }
    CenterMessage(
        icon = Icons.Rounded.Lock,
        title = "Allow access to photos & USB",
        subtitle = "Prism Gallery needs permission to find photos and videos on this TV and on USB drives.",
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PillButton(
                text = "Allow All Files Access (for USB)",
                icon = Icons.Rounded.Usb,
                onClick = onRequestAllFilesAccess,
                focusRequester = first,
            )
            Spacer(Modifier.padding(6.dp))
            PillButton(
                text = "Standard Storage Access",
                icon = Icons.Rounded.PhotoLibrary,
                onClick = onRequestPermission,
            )
        } else {
            PillButton(
                text = "Grant Storage Access",
                icon = Icons.Rounded.PhotoLibrary,
                onClick = onRequestPermission,
                focusRequester = first,
            )
        }
        Spacer(Modifier.padding(6.dp))
        PillButton("Open TV App Settings", Icons.Rounded.Settings, onOpenAppSettings)
    }
}
