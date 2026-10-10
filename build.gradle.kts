plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.ggdigitalmixer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.ggdigitalmixer"
        minSdk = 23
        targetSdk = 28
        versionCode = 5
        versionName = "1.5.0"
    }
}
