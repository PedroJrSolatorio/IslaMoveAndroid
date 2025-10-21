import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") version "4.4.2"
    id("com.google.firebase.crashlytics") version "3.0.2"
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

// Helper function to get local properties
fun getLocalProperty(propertyName: String, defaultValue: String = ""): String {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val localProperties = Properties()
        localProperties.load(FileInputStream(localPropertiesFile))
        return localProperties.getProperty(propertyName) ?: defaultValue
    }
    return defaultValue
}

android {
    namespace = "com.rj.islamove"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rj.islamove"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Add Mapbox access token to manifest (Google Maps API key kept as optional fallback)
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = getLocalProperty("GOOGLE_MAPS_API_KEY", "")
        manifestPlaceholders["MAPBOX_ACCESS_TOKEN"] = getLocalProperty("MAPBOX_ACCESS_TOKEN", "")

        // Add Cloudinary configuration
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${getLocalProperty("CLOUDINARY_CLOUD_NAME", "")}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${getLocalProperty("CLOUDINARY_API_KEY", "")}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${getLocalProperty("CLOUDINARY_API_SECRET", "")}\"")
        buildConfigField("String", "RENDER_BASE_URL", "\"${getLocalProperty("RENDER_BASE_URL", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../islamove-release.keystore")
            storePassword = "islamove123"
            keyAlias = "islamove"
            keyPassword = "islamove123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Firebase BOM - manages versions
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    
    // Google Play Services (keeping auth and location for other features)
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Mapbox SDK dependencies (using compatible versions)
    implementation("com.mapbox.maps:android:11.2.0")
    implementation("com.mapbox.extension:maps-compose:11.2.0")
    // Mapbox Search SDK for place discovery and geocoding
    implementation("com.mapbox.search:mapbox-search-android-ui:2.0.0")
    implementation("com.mapbox.search:mapbox-search-android:2.0.0")
    // For direct HTTP requests to Mapbox Directions API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")

    // Removed Firebase Admin SDK - not suitable for Android apps
    // Using client-side Firestore approach instead

    // Removed Google Places - now using Mapbox Search SDK
    // implementation("com.google.android.libraries.places:places:3.2.0")

    // Hilt for dependency injection
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Material Icons Extended (for Visibility and VisibilityOff icons)
    implementation("androidx.compose.material:material-icons-extended:1.7.0")

    // Cloudinary for image upload
    implementation("com.cloudinary:cloudinary-android:2.8.0")
    
    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Date picker
    implementation("io.github.vanpra.compose-material-dialogs:datetime:0.9.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}