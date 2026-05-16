let images = [];
let index = 0;

/* Load folder */
function loadImages(event) {
    const files = Array.from(event.target.files);
    images = files.filter(f => f.type.startsWith("image"));
    index = 0;

    if (images.length > 0) {
        showImage();
    }
}

/* Show image */
function showImage() {
    const url = URL.createObjectURL(images[index]);
    document.getElementById("img").src = url;
}

/* Remote navigation */
document.addEventListener("keydown", function(e) {
    if (!images.length) return;

    if (e.key === "ArrowRight") next();
    if (e.key === "ArrowLeft") prev();
});

/* Swipe support */
let startX = 0;

document.addEventListener("touchstart", e => {
    startX = e.touches[0].clientX;
});

document.addEventListener("touchend", e => {
    let endX = e.changedTouches[0].clientX;

    if (startX - endX > 50) next();
    if (endX - startX > 50) prev();
});

/* Navigation */
function next() {
    index = (index + 1) % images.length;
    showImage();
}

function prev() {
    index = (index - 1 + images.length) % images.length;
    showImage();
}