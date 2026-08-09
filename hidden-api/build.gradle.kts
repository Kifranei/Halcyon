plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.ella.music.hiddenapi"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
}
