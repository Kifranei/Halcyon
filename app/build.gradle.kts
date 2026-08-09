import java.util.Locale
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

val appVersionName = "1.2.5"

fun variantChannelMarker(variantName: String): String =
    when (variantName.lowercase(Locale.US)) {
        "debug" -> "d"
        "fastrelease" -> "f"
        "release" -> "r"
        else -> variantName.firstOrNull()?.lowercaseChar()?.toString() ?: "x"
    }

abstract class CopyRenamedApksTask : DefaultTask() {
    @get:InputDirectory
    abstract val apkDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val channelMarker: Property<String>

    @get:Input
    abstract val gitHash: Property<String>

    @TaskAction
    fun copyApks() {
        val sourceDir = apkDir.get().asFile
        val targetDir = outputDir.get().asFile
        targetDir.mkdirs()

        val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        val marker = channelMarker.get()
        val variant = variantName.get()
        val version = versionName.get()
        val git = gitHash.get()
        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .forEach { apk ->
                val lowerName = apk.name.lowercase(Locale.US)
                val abi = knownAbis.firstOrNull { lowerName.contains(it.lowercase(Locale.US)) }
                    ?: "universal"
                val hash = apk.shortContentHash()
                // Embed the git short SHA (g<sha>) so every packaged APK self-identifies the exact
                // commit it was built from.
                val target = targetDir.resolve(
                    "Halcyon-$version-$marker-g$git-$hash-$abi-$variant.APK"
                )
                val oldNamePrefix = "Halcyon-$version-"
                val oldNameSuffix = "-$abi-$variant.APK"
                targetDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith(oldNamePrefix) && it.name.endsWith(oldNameSuffix) && it != target }
                    ?.forEach { it.delete() }
                apk.copyTo(target, overwrite = true)
                logger.lifecycle("Renamed APK copied to: ${target.absolutePath}")
            }
    }

    private fun java.io.File.shortContentHash(): String {
        val digest = MessageDigest.getInstance("SHA-1")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(6)
    }
}

android {
    namespace = "com.ella.music"
    compileSdk = 37
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
        ?: listOf(file("release.jks"), rootProject.file("release.jks"))
            .firstOrNull { it.exists() }
        ?: file("release.jks")
    val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "kidn0x1"
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "kidn0x1"
    val hasReleaseSigning = releaseStoreFile.exists() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()
    val allowDebugSignedRelease = System.getenv("CI").equals("true", ignoreCase = true) ||
        System.getenv("ALLOW_DEBUG_SIGNED_RELEASE").equals("true", ignoreCase = true)

    defaultConfig {
        applicationId = "com.ella.music"
        minSdk = 29
        targetSdk = 37
        versionCode = 33
        versionName = appVersionName
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Oboe (prefab) ships against the shared STL.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    splits {
        abi {
            val abiIncludes = providers.gradleProperty("ellaAbi")
                .orNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a")

            isEnable = true
            reset()
            include(*abiIncludes.toTypedArray())
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else if (allowDebugSignedRelease) {
                logger.warn("Release signing is not configured; using debug signing for this release build.")
                signingConfigs.getByName("debug")
            } else {
                throw GradleException(
                    "Release signing is not configured. Put release.jks in app/ or project root, " +
                    "set RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD, " +
                    "or set ALLOW_DEBUG_SIGNED_RELEASE=true to produce a debug-signed release APK."
                )
            }
        }

    create("fastRelease") {
        initWith(getByName("release"))
        isMinifyEnabled = false
        isShrinkResources = false
        matchingFallbacks += listOf("release")
        signingConfig = if (hasReleaseSigning) {
            signingConfigs.getByName("release")
        } else if (allowDebugSignedRelease) {
            signingConfigs.getByName("debug")
        } else {
            signingConfigs.getByName("release")
        }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // FFmpegKit and the app's native audio path both use the shared C++ runtime.
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        val variantNameCapitalized = variantName.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }

        val apkDirProvider = variant.artifacts.get(SingleArtifact.APK)
        val outputDirProvider = layout.buildDirectory.dir("outputs/renamed-apk/$variantName")

        val renameTask = tasks.register<CopyRenamedApksTask>("copy${variantNameCapitalized}RenamedApks") {
            apkDir.set(apkDirProvider)
            outputDir.set(outputDirProvider)
            versionName.set(appVersionName)
            this.variantName.set(variantName)
            channelMarker.set(variantChannelMarker(variantName))
            // Query git via providers.exec (deferred to execution time) so it stays compatible with
            // Gradle's configuration cache — running an external process at configuration time is not.
            gitHash.set(
                providers.exec {
                    commandLine("git", "rev-parse", "--short", "HEAD")
                    isIgnoreExitValue = true
                }.standardOutput.asText.map { it.trim().ifBlank { "nogit" } }.orElse("nogit")
            )
        }

        tasks.matching { it.name == "assemble$variantNameCapitalized" }
            .configureEach {
                finalizedBy(renameTask)
            }
        }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Installs the bundled baseline profile (src/main/baseline-prof.txt) so ART AOT-compiles
    // the startup/library paths at install time instead of JIT-compiling them on first launch.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Material 3 Expressive shapes (cookie / scallop / clover) for the daily-mix cover thumbnails.
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    // Optional on-device enhancement: enables HONOR's 96-192 kHz playback path when the
    // device exposes HNAUDIO_SERVICE_HIGHSAMPLERATEPLAY. Unsupported devices simply no-op.
    implementation("com.hihonor.mcs:media-audio:1.2.0.300")
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lyricon.provider)
    implementation(libs.lyric.getter.api)
    implementation("com.github.HChenX:SuperLyricApi:3.4")
    // Full LGPL build supplies muxers and encoders for the local conversion tool.
    implementation("com.arthenica:ffmpeg-kit-full:6.0-2.LTS")
    implementation(libs.reorderable)
    implementation(libs.compose.material.icons.extended)
    implementation(project(":lyrico-audiotag"))
    implementation("wang.harlon.quickjs:wrapper-android:2.4.0")
    implementation(project(":ffmpeg-decoder"))
    implementation("com.google.oboe:oboe:1.9.0")

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation("androidx.webkit:webkit:1.12.1")

    // MCP Server
    implementation(libs.mcp.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.focus.api)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.api.bypass)
    compileOnly(project(":hidden-api"))

    testImplementation("junit:junit:4.13.2")
}
