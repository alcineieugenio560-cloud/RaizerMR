plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.raizer.mr"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.raizer.mr"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Etapas 1-5 não precisam de bibliotecas externas (menos pontos de falha na compilação).
// MediaPipe entra na Etapa 6.
dependencies { }
