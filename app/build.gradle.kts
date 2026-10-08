
plugins {
    id("com.android.application")
}

android {
    namespace = "com.daroca.entregas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daroca.entregas"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }
}

dependencies {
    // WebView do Android
    implementation("androidx.webkit:webkit:1.13.0")

    // Google ML Kit - reconhecimento de texto
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
