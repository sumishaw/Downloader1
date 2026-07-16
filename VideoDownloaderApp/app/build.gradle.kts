plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.videodownloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videodownloader"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // HTML parsing to find publicly-served <video>/<source> links
    implementation("org.jsoup:jsoup:1.17.2")

    // Background downloads
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Additional dependencies for enhanced video extraction
    // JSON parsing for complex script data
    implementation("com.google.code.gson:gson:2.10.1")

    // Headless browser for JavaScript-heavy sites (Pexels, Vimeo, etc)
    // This extension is typically used in tests (Kotest + Playwright). Scope it to testImplementation so
    // it is not required on the app runtime classpath, which fixes resolution during appDebug builds.
    testImplementation("io.github.detekt.kotest:kotest-extensions-playwright:1.3.3")
    
    // WebView for rendering JavaScript content
    implementation("androidx.webkit:webkit:1.7.0")
}
