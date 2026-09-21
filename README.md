# Prism Gallery – photo & video gallery for Android TV

Colourful, D-pad friendly gallery for Android TV (tested design target: 32" TCL, Android TV 11).
One **universal APK** (armeabi-v7a, arm64-v8a, x86, x86_64), minSdk 21.

## Get the APK (GitHub)
1. Create a new GitHub repo and upload the *contents* of this folder (keep `.github/`).
2. Open the **Actions** tab → *Build Android TV APK* → **Run workflow** (it also runs on every push).
3. When it turns green, open the run and download the artifact **PrismGallery-APK**.
   * `PrismGallery-release.apk` – small, optimised (use this one)
   * `PrismGallery-debug.apk` – fallback if you ever need it
4. Push a tag like `v1.0.0` to also get the APK attached to a GitHub Release.

## Install on the TV
Copy the APK to a USB stick and open it with a file manager (enable "unknown sources"),
or `adb connect <tv-ip>` then `adb install PrismGallery-release.apk`.

## Features
* Photos timeline grouped by month, Albums (folders / USB drives), Videos, Favorites
* Full-screen viewer: next/prev with ◀ ▶, zoom, pan, rotate, favourite, details (EXIF), delete
* Slideshow with Fade / Slide / Zoom transitions, Ken Burns motion, shuffle, repeat, interval
* Video player (ExoPlayer): MP4, MKV, WebM, MOV, 3GP, TS, MPEG, FLV, OGV …
* Images: JPEG, PNG, WebP, GIF (animated), BMP, HEIC*, **AVIF** (bundled libavif/dav1d decoder – works on Android 11 which has no native AVIF)
* 6 colour themes, sort options, thumbnail sizes, "Open with" from file managers
* Overscan-safe layout, adaptive grid for any TV resolution

## Remote keys
OK = open / show controls · ◀ ▶ = previous/next (seek ±10 s in videos) · ▲ ▼ = show controls (pan when zoomed)
Back = close / hide controls · Media keys supported.

## Notes
* Targets SDK 29 on purpose so the app can read all folders and USB drives on Android 10–12 TVs and see `.avif` files.
* If the dependency `org.aomedia.avif.android:avif` version ever fails to resolve, check the latest at
  https://central.sonatype.com/artifact/org.aomedia.avif.android/avif and edit `app/build.gradle.kts`.
