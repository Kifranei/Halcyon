pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Android publications of the local compose-miuix-ui checkout (main 9d4b9c85).
        // Each module publishes into its own build/repository/local.
        maven { url = uri("../miuix/miuix-core/build/repository/local") }
        maven { url = uri("../miuix/miuix-material/build/repository/local") }
        maven { url = uri("../miuix/miuix-squircle/build/repository/local") }
        maven { url = uri("../miuix/miuix-blur/build/repository/local") }
        maven { url = uri("../miuix/miuix-ui/build/repository/local") }
        maven { url = uri("../miuix/miuix-icons/build/repository/local") }
        maven { url = uri("../miuix/miuix-preference/build/repository/local") }
        // FFmpegKit's archived Android artifacts are mirrored here after their original
        // distribution repository was retired.
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://jitpack.io") }
        // HONOR Audio Kit publishes the optional HD audio playback SDK here.
        maven { url = uri("https://developer.honor.com/repo") }
    }
}

rootProject.name = "Halcyon"
include(":app")
include(":ffmpeg-decoder")
include(":lyrico-audiotag")
include(":hidden-api")
