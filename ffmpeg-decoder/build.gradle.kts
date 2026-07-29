plugins {
    id("com.android.library")
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 37
    val buildNative = providers.gradleProperty("ellaBuildNative")
        .map { it.toBoolean() }
        .getOrElse(false)

    defaultConfig {
        minSdk = 26
        ndk {
            val abiIncludes = providers.gradleProperty("ellaAbi")
                .orNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a")

            abiFilters += abiIncludes
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
