# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# JNI-bound native methods — native code binds by exact class/method/field name.
-keepclasseswithmembernames class * {
    native <methods>;
}

# ijkplayer/ffmpeg JNI bindings — native side references fields/methods by name.
-keep class tv.danmaku.ijk.media.player.** { *; }
-dontwarn tv.danmaku.ijk.media.player.**

# Conscrypt JNI crypto provider.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# MediaSessionShim (API21+) is gated behind BuildConfig.Y1_ONLY (always true on this
# fixed-API17 device) — the reflective Class.forName call is now dead code by design,
# so R8 is intentionally left free to strip the whole class instead of keeping it.

# jaudiotagger resolves ID3/Vorbis/FLAC frame body classes via Class.forName at runtime.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**