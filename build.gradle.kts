// language: Kotlin, file: build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    language = "en"
    description = "Redflix — Free HD movies & TV series via TMDB + videasy/vidlink"
    authors = listOf("XeonMovies")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://redflix.club/redflix-192x192.png"
}

android {
    namespace = "com.redflix"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.lagradost:cloudstream3:pre-release")
    implementation("org.jsoup:jsoup:1.17.2")
}
