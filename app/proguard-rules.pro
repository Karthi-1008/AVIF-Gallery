# AVIF decoder uses JNI and looks up fields on Info by name
-keep class org.aomedia.avif.android.** { *; }
-keepclassmembers class * { native <methods>; }

# Our Coil decoder is referenced from code only, keep for safety
-keep class com.prismtv.gallery.avif.** { *; }

-dontwarn org.aomedia.avif.android.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
