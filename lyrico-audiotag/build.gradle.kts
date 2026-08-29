plugins {
    alias(libs.plugins.androidLibrary)
    id("kotlin-parcelize")
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
    // Only arm64 has a checked-in prebuilt. Multi-ABI and non-arm64 builds must compile TagLib.
    .getOrElse(configuredAbis.any { it != "arm64-v8a" })

android {
    namespace = "com.lonx.audiotag"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
        if (buildNative) {
            externalNativeBuild {
                cmake {
                    cppFlags += ""
                }
            }
        }
        ndk {
            abiFilters += configuredAbis
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    if (buildNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation(libs.kotlinx.coroutines.android)
}
