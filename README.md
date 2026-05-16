# AVIF-Gallery
A simple gallery-style AVIF image viewer designed for Android TV and Android devices.   This project provides a fullscreen, folder-based image viewing experience with swipe and remote navigation support.

# AVIF Viewer for Android TV and Android

A lightweight gallery-style AVIF image viewer built for Android TV and Android devices.  
This project provides a fullscreen viewing experience with folder selection, swipe navigation, and TV remote support.

---

# Features

- Fullscreen image viewing
- Gallery-style browsing
- Folder selection support
- Left / Right remote navigation
- Swipe gesture navigation
- Lightweight HTML + CSS + JavaScript project
- Optimized for Android TV screens

---

# Screenshots

## Main Viewer
- Fullscreen image fit
- Black background for TV viewing
- Smooth navigation

## Navigation
- Left Arrow → Previous Image
- Right Arrow → Next Image
- Swipe Left / Right supported

---

# Project Structure

```text
AVIF Viewer For Android TV and Android/
│
├── index.html
├── style.css
├── script.js
└── README.md
```

---

# index.html

```html
<!DOCTYPE html>
<html>
<head>
    <title>AVIF TV Gallery</title>

    <meta name="viewport"
          content="width=device-width,
                   initial-scale=1.0,
                   user-scalable=no">

    <link rel="stylesheet" href="style.css">
</head>

<body>

<div id="topbar">
    <input type="file"
           webkitdirectory
           multiple
           onchange="loadImages(event)">
</div>

<div id="viewer">
    <img id="img">
</div>

<script src="script.js"></script>

</body>
</html>
```

---

# style.css

```css
body {
    margin: 0;
    background: black;
    overflow: hidden;
}

/* Fullscreen viewer */
#viewer {
    width: 100vw;
    height: 100vh;

    display: flex;
    justify-content: center;
    align-items: center;
}

/* Perfect image fitting */
#viewer img {
    width: 100%;
    height: 100%;

    object-fit: contain;
}

/* Top bar */
#topbar {
    position: fixed;
    top: 10px;

    width: 100%;

    text-align: center;

    z-index: 10;

    opacity: 0.3;
}
```

---

# script.js

```javascript
let images = [];
let index = 0;

/* Load folder */
function loadImages(event) {

    const files = Array.from(event.target.files);

    images = files.filter(f =>
        f.type.startsWith("image")
    );

    index = 0;

    if (images.length > 0) {
        showImage();
    }
}

/* Show image */
function showImage() {

    const url =
        URL.createObjectURL(images[index]);

    document
        .getElementById("img")
        .src = url;
}

/* Remote navigation */
document.addEventListener(
    "keydown",
    function(e) {

        if (!images.length) return;

        if (e.key === "ArrowRight")
            next();

        if (e.key === "ArrowLeft")
            prev();
    }
);

/* Swipe support */
let startX = 0;

document.addEventListener(
    "touchstart",
    e => {
        startX =
            e.touches[0].clientX;
    }
);

document.addEventListener(
    "touchend",
    e => {

        let endX =
            e.changedTouches[0].clientX;

        if (startX - endX > 50)
            next();

        if (endX - startX > 50)
            prev();
    }
);

/* Next image */
function next() {

    index =
        (index + 1) % images.length;

    showImage();
}

/* Previous image */
function prev() {

    index =
        (index - 1 + images.length)
        % images.length;

    showImage();
}
```

---

# How to Use

## Option 1 — HTML Project

1. Download the repository
2. Open `index.html`
3. Select a folder containing images
4. Browse using:
   - TV remote
   - Arrow keys
   - Swipe gestures

---

## Option 2 — APK Version

1. Download APK
2. Install on Android TV or Android device
3. Open app
4. Select image folder
5. Start browsing

---

# Android TV Support

This project is optimized for:

- Android TV
- Smart TVs
- TV remotes
- Large screen viewing

---

# Navigation Controls

| Action | Control |
|--------|---------|
| Next Image | Right Arrow / Swipe Left |
| Previous Image | Left Arrow / Swipe Right |

---

# Notes

- Best viewed in fullscreen mode
- Requires browser/WebView image support
- Folder-based viewing supported
- Lightweight and fast

---

# Future Improvements

- Thumbnail grid
- Auto slideshow
- Folder memory
- Zoom support
- Auto scan storage
- Better TV UI

---

# License

Open for personal and educational use.
