package com.prismtv.gallery.ui

import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CleanHands
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.gallery.data.GalleryViewModel

private val SORTS = listOf(
    "Newest first",
    "Oldest first",
    "Name A–Z",
    "Name Z–A",
    "Largest first",
    "Smallest first",
)
private val GROUPINGS = listOf("By Day", "By Month", "By Year", "Continuous (No grouping)")
private val THUMBS = listOf("Small", "Medium", "Large")
private val INTERVALS = listOf(2, 3, 5, 8, 10, 15, 30)
private val EFFECTS = listOf("Fade", "Slide", "Zoom")

@Composable
fun SettingsScreen(
    vm: GalleryViewModel,
    onSelectUsbFolder: () -> Unit = {},
    hasAllFilesAccess: Boolean = true,
    onRequestAllFilesAccess: () -> Unit = {},
) {
    val s = vm.settings
    val p = LocalPalette.current
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", "Press OK to change any setting")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Theme Customization
            SettingRow(
                icon = Icons.Rounded.DarkMode,
                title = "Dark background style",
                value = DarkThemeStyles[s.darkThemeStyle.coerceIn(0, DarkThemeStyles.lastIndex)].name,
                trailing = {
                    val st = DarkThemeStyles[s.darkThemeStyle.coerceIn(0, DarkThemeStyles.lastIndex)]
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(st.bg)
                            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                },
            ) {
                vm.update { it.copy(darkThemeStyle = (it.darkThemeStyle + 1) % DarkThemeStyles.size) }
            }

            SettingRow(
                icon = Icons.Rounded.Palette,
                title = "Accent color glow",
                value = Palettes[s.palette.coerceIn(0, Palettes.lastIndex)].name,
                trailing = {
                    Palettes[s.palette.coerceIn(0, Palettes.lastIndex)].let { pl ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(pl.c1, pl.c2, pl.c3).forEach {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(it)
                                )
                            }
                        }
                    }
                },
            ) { vm.update { it.copy(palette = (it.palette + 1) % Palettes.size) } }

            // Date & Time Features
            SettingRow(
                icon = Icons.Rounded.CalendarToday,
                title = "Date grouping mode",
                value = GROUPINGS[s.dateGrouping.coerceIn(0, GROUPINGS.lastIndex)],
            ) {
                vm.update { it.copy(dateGrouping = (it.dateGrouping + 1) % GROUPINGS.size) }
            }

            SettingRow(
                icon = Icons.Rounded.DateRange,
                title = "Date source",
                value = if (s.useDateTaken) "Camera Date Taken (EXIF)" else "File Modified Date",
            ) {
                vm.update { it.copy(useDateTaken = !it.useDateTaken) }
            }

            SettingRow(Icons.Rounded.Sort, "Sort order", SORTS[s.sort.coerceIn(0, SORTS.lastIndex)]) {
                vm.update { it.copy(sort = (it.sort + 1) % SORTS.size) }
            }

            // Grid & Display
            SettingRow(Icons.Rounded.GridView, "Thumbnail size", THUMBS[s.thumb.coerceIn(0, 2)]) {
                vm.update { it.copy(thumb = (it.thumb + 1) % THUMBS.size) }
            }
            SettingRow(Icons.Rounded.Favorite, "Show file names on thumbnails", onOff(s.showNames)) {
                vm.update { it.copy(showNames = !it.showNames) }
            }
            SettingRow(Icons.Rounded.Image, "Hide tiny images (icons, stickers)", onOff(s.hideSmall)) {
                vm.update { it.copy(hideSmall = !it.hideSmall) }
            }

            // Fast USB & Cache Acceleration
            SettingRow(
                icon = Icons.Rounded.Bolt,
                title = "Fast persistent cache (Instant USB)",
                value = onOff(s.enableFastCache),
            ) {
                vm.update { it.copy(enableFastCache = !it.enableFastCache) }
            }

            SettingRow(
                icon = Icons.Rounded.CleanHands,
                title = "Clear thumbnail disk cache",
                value = "Clear Now",
            ) {
                vm.clearThumbnailCache()
                Toast.makeText(ctx, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show()
            }

            SettingRow(
                icon = Icons.Rounded.Refresh,
                title = "Rebuild library & media cache",
                value = "Rebuild",
            ) {
                vm.clearMediaCache()
                Toast.makeText(ctx, "Rebuilding cache…", Toast.LENGTH_SHORT).show()
            }

            // Slideshow Settings
            SettingRow(Icons.Rounded.Timer, "Slideshow interval", "${s.intervalSec} seconds") {
                vm.update {
                    val i = INTERVALS.indexOf(it.intervalSec)
                    it.copy(intervalSec = INTERVALS[(i + 1) % INTERVALS.size])
                }
            }
            SettingRow(Icons.Rounded.Slideshow, "Slideshow transition", EFFECTS[s.effect.coerceIn(0, 2)]) {
                vm.update { it.copy(effect = (it.effect + 1) % EFFECTS.size) }
            }
            SettingRow(Icons.Rounded.AutoAwesome, "Slow zoom (Ken Burns) in slideshow", onOff(s.kenBurns)) {
                vm.update { it.copy(kenBurns = !it.kenBurns) }
            }
            SettingRow(Icons.Rounded.Shuffle, "Shuffle slideshow", onOff(s.shuffle)) {
                vm.update { it.copy(shuffle = !it.shuffle) }
            }
            SettingRow(Icons.Rounded.Repeat, "Repeat slideshow", onOff(s.loop)) {
                vm.update { it.copy(loop = !it.loop) }
            }

            // USB & Storage Options
            SettingRow(Icons.Rounded.Usb, "Select USB drive / folder", "Browse…") {
                onSelectUsbFolder()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SettingRow(
                    icon = Icons.Rounded.Storage,
                    title = "All files access (USB / SD cards)",
                    value = if (hasAllFilesAccess) "Granted" else "Grant access…",
                ) {
                    onRequestAllFilesAccess()
                }
            }
            if (vm.customUris.isNotEmpty()) {
                SettingRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Clear custom USB folders (${vm.customUris.size})",
                    value = "Clear",
                ) {
                    vm.clearCustomTreeUris()
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ui.Surface.copy(alpha = 0.8f))
                    .padding(20.dp)
            ) {
                Text("Prism Gallery 2.0 (OLED Edition)", color = Ui.TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Accelerated USB loading with 512MB disk thumbnail cache & instant metadata index.",
                    color = Ui.TextLo, fontSize = 14.sp,
                )
                Text(
                    "Images: JPEG, PNG, WebP, GIF, BMP, HEIC/HEIF*, AVIF (bundled libavif decoder)",
                    color = Ui.TextLo, fontSize = 14.sp,
                )
                Text(
                    "Video: MP4, MKV, WebM, MOV, 3GP, TS, MPEG, FLV, OGV, AVI (Hardware + ExoPlayer)",
                    color = Ui.TextLo, fontSize = 14.sp,
                )
                Text("*HEIC depends on TV hardware decoder availability.", color = p.c3, fontSize = 12.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun onOff(b: Boolean) = if (b) "On" else "Off"

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    FocusCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.02f,
        onClick = onClick,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Ui.SurfaceHi else Ui.Surface.copy(alpha = 0.85f))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(p.diagonal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Ui.Bg, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(18.dp))
            Text(
                title,
                color = Ui.TextHi,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                value,
                color = if (focused) Color.White else p.c2,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
