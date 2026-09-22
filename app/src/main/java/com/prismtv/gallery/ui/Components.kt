package com.prismtv.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.prismtv.gallery.data.StorageDrive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

val RailCollapsed = 84.dp

/** Remembers which grid cell had focus so we can put focus back after the viewer closes. */
@Stable
class FocusMemory {
    var last: String? = null
    var restore by mutableStateOf<String?>(null)
}

/** Focus requester for a grid cell; also restores focus when [FocusMemory.restore] matches. */
@Composable
fun rememberCellFocus(key: String, memory: FocusMemory, first: FocusRequester?): FocusRequester {
    val own = remember { FocusRequester() }
    val fr = first ?: own
    val restore = memory.restore
    LaunchedEffect(restore) {
        if (restore != null && restore == key) {
            delay(80)
            try {
                fr.requestFocus()
            } catch (e: Exception) {
            }
            memory.restore = null
        }
    }
    return fr
}

/**
 * The basic D-pad friendly building block: grows, glows and gets a gradient outline when focused.
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    focusRequester: FocusRequester? = null,
    focusedScale: Float = 1.07f,
    borderWidth: Dp = 3.dp,
    onFocusChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(160), label = "cardScale")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "cardGlow")
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = (20f * glow).dp,
                shape = shape,
                clip = false,
                ambientColor = p.c1,
                spotColor = p.c2,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(shape)
    ) {
        content(focused)
        if (focused) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(borderWidth, p.horizontal, shape)
            )
        }
    }
}

@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    danger: Boolean = false,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    FocusCard(
        modifier = modifier,
        shape = shape,
        focusRequester = focusRequester,
        focusedScale = 1.06f,
        borderWidth = 0.dp,
        onClick = onClick,
    ) { focused ->
        val bg: Brush = when {
            focused && danger -> Brush.horizontalGradient(listOf(Color(0xFFFF3D71), Color(0xFFFF7A59)))
            focused -> p.horizontal
            else -> SolidColor(Ui.SurfaceHi)
        }
        val fg = if (focused) Ui.Bg else Ui.TextHi
        Row(
            Modifier
                .background(bg)
                .padding(horizontal = 26.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = TextStyle(brush = p.horizontal, fontSize = 34.sp, fontWeight = FontWeight.Black),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (subtitle != null) {
            Spacer(Modifier.width(16.dp))
            Text(
                subtitle,
                color = Ui.TextLo,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun LoadingView(text: String = "Loading your library…") {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = p.c2, strokeWidth = 5.dp, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(18.dp))
            Text(text, color = Ui.TextLo, fontSize = 18.sp)
        }
    }
}

@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val p = LocalPalette.current
    FocusCard(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        borderWidth = 0.dp,
        onClick = onClick,
    ) { focused ->
        val bg: Brush = when {
            selected && focused -> p.horizontal
            selected -> Brush.horizontalGradient(listOf(p.c1.copy(alpha = 0.40f), p.c2.copy(alpha = 0.40f)))
            focused -> SolidColor(Ui.SurfaceHi)
            else -> SolidColor(Ui.Surface.copy(alpha = 0.85f))
        }
        val borderModifier = if (selected && !focused) {
            Modifier.border(1.dp, p.c1.copy(alpha = 0.65f), RoundedCornerShape(50))
        } else Modifier

        Row(
            Modifier
                .then(borderModifier)
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = if (selected) Color.White else Ui.TextLo,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = if (selected) Color.White else Ui.TextLo,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun CenterMessage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val p = LocalPalette.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(p.diagonal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Ui.Bg, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(
                title,
                color = Ui.TextHi,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                color = Ui.TextLo,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(520.dp),
            )
            Spacer(Modifier.height(26.dp))
            actions()
        }
    }
}

data class NavItem(val key: String, val label: String, val icon: ImageVector)

/**
 * Left navigation rail: icons only until a rail item gets focus, then it slides open
 * over the content and shows labels.
 */
@Composable
fun NavRail(
    items: List<NavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val expanded = focusedKey != null
    val width by animateDpAsState(if (expanded) 252.dp else RailCollapsed, tween(200), label = "railWidth")
    val bgAlpha by animateFloatAsState(if (expanded) 0.97f else 0f, tween(200), label = "railAlpha")

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(
                Brush.horizontalGradient(
                    listOf(Ui.Bg.copy(alpha = bgAlpha), Ui.Bg.copy(alpha = bgAlpha * 0.90f))
                )
            )
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(p.diagonal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Ui.Bg, modifier = Modifier.size(24.dp))
            }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "Prism",
                    style = TextStyle(brush = p.horizontal, fontSize = 24.sp, fontWeight = FontWeight.Black),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        items.forEach { item ->
            NavRow(
                item = item,
                selected = item.key == selectedKey,
                expanded = expanded,
                onFocus = { f ->
                    if (f) focusedKey = item.key
                    else if (focusedKey == item.key) focusedKey = null
                },
                onClick = { onSelect(item.key) },
            )
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun NavRow(
    item: NavItem,
    selected: Boolean,
    expanded: Boolean,
    onFocus: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    val bg: Brush = when {
        focused -> p.horizontal
        selected -> SolidColor(Color.White.copy(alpha = 0.12f))
        else -> SolidColor(Color.Transparent)
    }
    val fg = when {
        focused -> Ui.Bg
        selected -> Ui.TextHi
        else -> Ui.TextLo
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(shape)
            .background(bg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            null,
            tint = if (selected && !focused) p.c3 else fg,
            modifier = Modifier.size(26.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                item.label,
                color = fg,
                fontSize = 17.sp,
                fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun StorageDialog(
    drives: List<StorageDrive>,
    hasAllFilesAccess: Boolean,
    onDismiss: () -> Unit,
    onSelectDrive: (StorageDrive) -> Unit,
    onScanAll: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
) {
    val p = LocalPalette.current
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try {
            firstRequester.requestFocus()
        } catch (e: Exception) {
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(620.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Ui.Surface)
                .border(2.dp, p.c1.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(28.dp)
                .clickable(enabled = false) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Storage & USB Drives",
                    style = TextStyle(brush = p.horizontal, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    "${drives.size} drive(s) found",
                    color = Ui.TextLo,
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x33FFB300))
                        .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            "⚠️ USB drives need \"All Files Access\"",
                            color = Color(0xFFFFCC00),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Android 11 TV requires allowing All Files Access to view USB photos and videos.",
                            color = Ui.TextHi,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        PillButton(
                            text = "Grant All Files Access in Settings",
                            icon = Icons.Rounded.Settings,
                            onClick = {
                                onRequestAllFilesAccess()
                                onDismiss()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (drives.isEmpty()) {
                    Text(
                        "No external storage drives detected yet. Plug in your USB drive.",
                        color = Ui.TextLo,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    drives.forEachIndexed { index, drive ->
                        FocusCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            focusRequester = if (index == 0) firstRequester else null,
                            onClick = {
                                onSelectDrive(drive)
                                onDismiss()
                            },
                        ) { focused ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (focused) Ui.SurfaceHi else Color.Black.copy(alpha = 0.3f))
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (drive.isUsb) p.diagonal else SolidColor(Color(0xFF3B4B75))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (drive.isUsb) Icons.Rounded.Usb else Icons.Rounded.Folder,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        drive.name,
                                        color = Ui.TextHi,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val storageInfo = if (drive.totalBytes > 0) {
                                        val free = com.prismtv.gallery.data.MediaInfo.formatSize(drive.freeBytes)
                                        val total = com.prismtv.gallery.data.MediaInfo.formatSize(drive.totalBytes)
                                        "$free free of $total  •  ${drive.path.absolutePath}"
                                    } else {
                                        drive.path.absolutePath
                                    }
                                    Text(
                                        storageInfo,
                                        color = Ui.TextLo,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    "Select",
                                    color = if (focused) Color.White else p.c2,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                PillButton("Scan All Drives", Icons.Rounded.Refresh, onClick = {
                    onScanAll()
                    onDismiss()
                })
                PillButton("Close", Icons.Rounded.Close, onClick = onDismiss)
            }
        }
    }
}
