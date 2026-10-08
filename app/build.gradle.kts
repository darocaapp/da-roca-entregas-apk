
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
        versionCode = 2
        versionName = "2.0"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.13.0")
}
