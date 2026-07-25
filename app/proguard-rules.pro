# ProGuard rules for Halcyon

# JNI entry points are resolved by class and method names.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Lyricon is a provider-facing API; keep its published model names stable.
-keep class io.github.proify.lyricon.** { *; }
-dontwarn io.github.proify.lyricon.**

# SuperLyricApi references this hidden framework class on supported systems.
-keep class com.hchen.superlyricapi.** { *; }
-dontwarn android.os.ServiceManager

# Lyric Getter's Xposed module finds and hooks the public API by class and member names.
-keep class cn.lyric.getter.api.** { *; }

# Settings search enumerates setting resources to keep new preferences discoverable. R8 must retain
# these reflected fields in minified builds.
-keepclassmembers class com.ella.music.R$string {
    public static int settings_*;
}


# FFmpeg native symbols use Java_androidx_media3_decoder_ffmpeg_* names.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# Ktor / MCP SDK — suppress warnings for JVM-only classes not available on Android
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn io.ktor.**
-dontwarn io.modelcontextprotocol.**
