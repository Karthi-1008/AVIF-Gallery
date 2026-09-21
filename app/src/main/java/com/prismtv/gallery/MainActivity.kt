package com.prismtv.gallery

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.prismtv.gallery.data.GalleryViewModel
import com.prismtv.gallery.data.Media
import com.prismtv.gallery.ui.Palettes
import com.prismtv.gallery.ui.PrismRoot
import com.prismtv.gallery.ui.PrismTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()

    private var granted by mutableStateOf(false)
    private var allFilesAccessGranted by mutableStateOf(false)
    private var externalMedia by mutableStateOf<Media?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermission()
        }

    private val selectUsbFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                }
                vm.addCustomTreeUri(uri)
                Toast.makeText(this, "USB folder added! Scanning media…", Toast.LENGTH_SHORT).show()
            }
        }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_MEDIA_MOUNTED,
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Toast.makeText(this@MainActivity, "USB device connected. Scanning…", Toast.LENGTH_SHORT).show()
                    vm.refresh()
                }
                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_REMOVED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Toast.makeText(this@MainActivity, "USB device removed. Refreshing…", Toast.LENGTH_SHORT).show()
                    vm.refresh()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerUsbReceivers()
        updatePermission()
        handleIntent(intent)

        setContent {
            val idx = vm.settings.palette.coerceIn(0, Palettes.lastIndex)
            PrismTheme(Palettes[idx]) {
                PrismRoot(
                    vm = vm,
                    permissionGranted = granted,
                    hasAllFilesAccess = allFilesAccessGranted,
                    externalMedia = externalMedia,
                    onRequestPermission = { requestPermission() },
                    onRequestAllFilesAccess = { requestAllFilesAccess() },
                    onOpenAppSettings = { openAppSettings() },
                    onSelectUsbFolder = { selectUsbFolderLauncher.launch(null) },
                    onExternalClosed = {
                        externalMedia = null
                        if (!granted) finish()
                    },
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
        }
    }

    private fun registerUsbReceivers() {
        try {
            val mediaFilter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addDataScheme("file")
            }
            registerReceiver(usbReceiver, mediaFilter)

            val usbFilter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            registerReceiver(usbReceiver, usbFilter)
        } catch (e: Exception) {
        }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        if (Build.VERSION.SDK_INT >= 33) {
            val img = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            val vid = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
            if (img == PackageManager.PERMISSION_GRANTED || vid == PackageManager.PERMISSION_GRANTED) return true
        }
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasPermission()
        }
    }

    private fun updatePermission() {
        val now = hasPermission()
        allFilesAccessGranted = checkAllFilesAccess()
        if (now && !granted) {
            granted = true
            vm.refresh()
        } else if (!now) {
            granted = false
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                )
            )
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            )
        }
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    openAppSettings()
                }
            }
        } else {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        } catch (e: Exception) {
            // Some TV firmwares do not ship this screen.
        }
    }

    /** "Open with Prism Gallery" support. */
    private fun handleIntent(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_VIEW) return
        val data = i.data ?: return
        var name: String? = null
        var size = 0L
        var path: String? = null
        if (data.scheme == "file") {
            path = data.path
            name = path?.let { File(it).name }
            size = path?.let { File(it).length() } ?: 0L
        } else {
            try {
                contentResolver.query(data, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = c.getString(n)
                        if (s >= 0) size = c.getLong(s)
                    }
                }
            } catch (e: Exception) {
            }
        }
        val fileName = name ?: data.lastPathSegment ?: "Shared file"
        val ext = fileName.substringAfterLast('.', "").lowercase()
        var mime = i.type ?: contentResolver.getType(data)
        if (mime.isNullOrEmpty() || mime == "application/octet-stream") {
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
        val isVideo = mime?.startsWith("video/") == true || ext in com.prismtv.gallery.data.Formats.videoExt
        externalMedia = Media(
            id = data.toString(),
            path = path,
            uri = data,
            name = fileName,
            mime = mime ?: com.prismtv.gallery.data.Formats.mimeFor(ext),
            isVideo = isVideo,
            size = size,
            dateMillis = System.currentTimeMillis(),
            bucketId = "external",
            bucketName = "Opened file",
        )
    }
}
