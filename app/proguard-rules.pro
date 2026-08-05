# yt-dlp-android maps extractor output through reflection and loads native entry points by name.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# youtubedl-android 0.18.1 bundles Commons Compress 1.12. Its ZIP extra fields are
# registered as Class instances and constructed with reflection. R8 must not merge,
# rename, or remove their constructors (otherwise yt-dlp initialization fails only
# in release builds with "class ... is not a concrete class").
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
