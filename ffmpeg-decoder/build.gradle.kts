plugins {
    id("com.android.library")
}

val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val configuredAbis = providers.gradleProperty("ellaAbi")
    .orNull
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.ifEmpty { null }
    ?: supportedAbis
val buildNative = providers.gradleProperty("ellaBuildNative")
    .map { it.toBoolean() }
    // Only arm64 has a checked-in JNI prebuilt; the four static FFmpeg archives are available
    // for multi-ABI builds, so link their JNI bridge automatically when another ABI is requested.
    .getOrElse(configuredAbis.any { it != "arm64-v8a" })

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += configuredAbis
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    if (buildNative && file("src/main/jni/ffmpeg").exists()) {
        externalNativeBuild {
            cmake {
                path = file("src/main/jni/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

dependencies {
    compileOnly(libs.androidx.media3.decoder)
    compileOnly(libs.androidx.media3.exoplayer)
    compileOnly("androidx.annotation:annotation:1.9.1")
    compileOnly(libs.kotlin.annotations.jvm)
}
