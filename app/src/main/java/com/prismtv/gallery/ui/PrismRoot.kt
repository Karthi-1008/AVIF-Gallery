package com.prismtv.gallery.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.prismtv.gallery.data.DateGrouping
import com.prismtv.gallery.data.GalleryViewModel
import com.prismtv.gallery.data.Media
import com.prismtv.gallery.data.MediaFilter
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
    var showSearchDialog by remember { mutableStateOf(false) }
    var lastBackAt by remember { mutableStateOf(0L) }
    val memory = remember { FocusMemory() }

    val photosState = rememberLazyGridState()
    val videosState = rememberLazyGridState()
    val favState = rememberLazyGridState()
    val albumsState = rememberLazyGridState()

    val dateGrouping = when (settings.dateGrouping) {
        0 -> DateGrouping.DAY
        1 -> DateGrouping.MONTH
        2 -> DateGrouping.YEAR
        else -> DateGrouping.NONE
    }

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
                        lastBackAt = 0L
                    },
                )
            }
            else -> {
                BackHandler {
                    val a = openAlbum
                    when {
                        showStorageDialog -> showStorageDialog = false
                        showSearchDialog -> showSearchDialog = false
                        vm.selectionMode -> vm.clearSelection()
                        a != null -> {
                            memory.restore = "album:${a.id}"
                            openAlbum = null
                            lastBackAt = 0L
                        }
                        section != Section.PHOTOS -> {
                            section = Section.PHOTOS
                            lastBackAt = 0L
                        }
                        else -> {
                            val now = System.currentTimeMillis()
                            if (lastBackAt > 0L && (now - lastBackAt < 2500)) {
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
                            loading = vm.loading && !vm.hasLoadedOnce,
                            emptyIcon = Icons.Rounded.PhotoLibrary,
                            emptyTitle = "No photos found",
                            emptyText = "Copy pictures to this TV, plug in a USB drive, or choose Select USB.",
                            state = photosState,
                            grouping = dateGrouping,
                            cellMin = cellMin,
                            vm = vm,
                            memory = memory,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onOpen = { i -> openViewer(vm.photos, i) },
                            onSelectUsb = { showStorageDialog = true },
                            onOpenSearch = { showSearchDialog = true },
                        )
                        Section.VIDEOS -> LibraryScreen(
                            title = "Videos",
                            subtitle = countText(vm.videos.size, "video"),
                            list = vm.videos,
                            loading = vm.loading && !vm.hasLoadedOnce,
                            emptyIcon = Icons.Rounded.Movie,
                            emptyTitle = "No videos found",
                            emptyText = "MP4, MKV, WebM, MOV, TS and more are supported.",
                            state = videosState,
                            grouping = dateGrouping,
                            cellMin = cellMin,
                            vm = vm,
                            memory = memory,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onOpen = { i -> openViewer(vm.videos, i) },
                            onSelectUsb = { showStorageDialog = true },
                            onOpenSearch = { showSearchDialog = true },
                        )
                        Section.FAVORITES -> LibraryScreen(
                            title = "Favorites",
                            subtitle = countText(vm.favoriteItems.size, "item"),
                            list = vm.favoriteItems,
                            loading = false,
                            emptyIcon = Icons.Rounded.Favorite,
                            emptyTitle = "No favorites yet",
                            emptyText = "Open a photo or video, press OK and tap the heart icon to add it here.",
                            state = favState,
                            grouping = dateGrouping,
                            cellMin = cellMin,
                            vm = vm,
                            memory = memory,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onOpen = { i -> openViewer(vm.favoriteItems, i) },
                            onSelectUsb = null,
                            onOpenSearch = { showSearchDialog = true },
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
                                        vm.loading && !vm.hasLoadedOnce && vm.albums.isEmpty() -> LoadingView()
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
                                        grouping = dateGrouping,
                                        cellMin = cellMin,
                                        vm = vm,
                                        memory = memory,
                                        hasAllFilesAccess = hasAllFilesAccess,
                                        onRequestAllFilesAccess = onRequestAllFilesAccess,
                                        onOpen = { i -> openViewer(fresh?.items ?: emptyList(), i) },
                                        onSelectUsb = null,
                                        onOpenSearch = { showSearchDialog = true },
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
                                val a = openAlbum
                                val candidate = when {
                                    a != null -> (vm.albums.firstOrNull { it.id == a.id }?.items ?: a.items).filter { !it.isVideo }
                                    section == Section.FAVORITES -> vm.favoriteItems.filter { !it.isVideo }
                                    else -> vm.photos
                                }
                                if (candidate.isEmpty()) {
                                    Toast.makeText(ctx, "No photos found for slideshow", Toast.LENGTH_SHORT).show()
                                } else {
                                    val ordered = if (settings.shuffle) candidate.shuffled() else candidate
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

        if (showSearchDialog) {
            SearchDialog(
                currentQuery = vm.searchQuery,
                onDismiss = { showSearchDialog = false },
                onApply = { query ->
                    vm.searchQuery = query
                    showSearchDialog = false
                }
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
    grouping: DateGrouping,
    cellMin: androidx.compose.ui.unit.Dp,
    vm: GalleryViewModel,
    memory: FocusMemory,
    hasAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onOpen: (Int) -> Unit,
    onSelectUsb: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = title,
            subtitle = subtitle,
            trailing = {
                // Date grouping cycler
                PillButton(
                    text = grouping.label,
                    icon = Icons.Rounded.DateRange,
                    onClick = {
                        val next = (vm.settings.dateGrouping + 1) % 4
                        vm.update { it.copy(dateGrouping = next) }
                    }
                )
                Spacer(Modifier.width(8.dp))
                // Search button
                if (onOpenSearch != null) {
                    PillButton(
                        text = if (vm.searchQuery.isNotEmpty()) "\"${vm.searchQuery}\"" else "Search",
                        icon = Icons.Rounded.Search,
                        onClick = onOpenSearch,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                // Multi-selection button
                PillButton(
                    text = if (vm.selectionMode) "Cancel Select" else "Select",
                    icon = if (vm.selectionMode) Icons.Rounded.Close else Icons.Rounded.CheckCircle,
                    onClick = {
                        if (vm.selectionMode) vm.clearSelection() else vm.selectionMode = true
                    }
                )
            }
        )

        // Filter chips bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaFilter.values().forEach { filter ->
                FilterChip(
                    text = filter.label,
                    selected = vm.activeFilter == filter,
                    onClick = { vm.activeFilter = filter }
                )
            }
            if (vm.searchQuery.isNotEmpty()) {
                FilterChip(
                    text = "Clear Search",
                    selected = true,
                    icon = Icons.Rounded.Close,
                    onClick = { vm.searchQuery = "" }
                )
            }
        }

        // Selection Action Bar
        if (vm.selectionMode) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ui.SurfaceHi)
                    .border(1.dp, p.c1.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${vm.selectedIds.size} selected",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Select All", Icons.Rounded.SelectAll, { vm.selectAll(list) })
                        PillButton("Favorite", Icons.Rounded.Favorite, { vm.batchFavorite() })
                        PillButton("Delete", Icons.Rounded.Delete, {
                            vm.batchDelete { count ->
                                Toast.makeText(ctx, "Deleted $count item(s)", Toast.LENGTH_SHORT).show()
                            }
                        }, danger = true)
                        PillButton("Done", Icons.Rounded.Close, { vm.clearSelection() })
                    }
                }
            }
        }

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
                grouping = grouping,
                useDateTaken = vm.settings.useDateTaken,
                cellMin = cellMin,
                favorites = vm.favorites,
                showNames = vm.settings.showNames,
                memory = memory,
                selectionMode = vm.selectionMode,
                selectedIds = vm.selectedIds,
                onToggleSelect = { vm.toggleSelection(it) },
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun SearchDialog(
    currentQuery: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var query by remember { mutableStateOf(currentQuery) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Photos & Videos", color = Ui.TextHi, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name, folder, extension…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(query) }) {
                Text("Search", color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Ui.TextLo)
            }
        },
        containerColor = Ui.Surface,
    )
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
