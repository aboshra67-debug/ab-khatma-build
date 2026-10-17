plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val configuredApiBaseUrl = (
    project.findProperty("KHATMA_API_BASE_URL")
        ?: "https://balanced-creativity-production-f316.up.railway.app"
).toString()
val escapedApiBaseUrl = configuredApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val securePreviewUrl = project.findProperty("KHATMA_V2_API_BASE_URL")?.toString().orEmpty().trimEnd('/')
val escapedSecurePreviewUrl = securePreviewUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val securePreviewEnabled = securePreviewUrl.startsWith("https://") && !securePreviewUrl.contains("balanced-creativity-production-f316.up.railway.app")


val firebaseProjectId = (project.findProperty("KHATMA_FIREBASE_PROJECT_ID") ?: "ab-khatma").toString()
val firebaseAppId = (project.findProperty("KHATMA_FIREBASE_APP_ID") ?: "").toString()
val firebaseApiKey = (project.findProperty("KHATMA_FIREBASE_API_KEY") ?: "").toString()
val googleWebClientId = (project.findProperty("KHATMA_GOOGLE_WEB_CLIENT_ID") ?: "").toString()
fun firebaseBuildValue(value: String) = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"") + "\""

android {
    namespace = "com.ab.khatma"
    compileSdk = 36

    defaultConfig {
        // Same production package as the known-working V0.4 APK.
        applicationId = "com.ab.khatma"
        minSdk = 26
        targetSdk = 36
        versionCode = 107
        versionName = "0.19.78-p83-session-audio-safe-qa"
        buildConfigField("String", "KHATMA_FIREBASE_PROJECT_ID", firebaseBuildValue(firebaseProjectId))
        buildConfigField("String", "KHATMA_FIREBASE_APP_ID", firebaseBuildValue(firebaseAppId))
        buildConfigField("String", "KHATMA_FIREBASE_API_KEY", firebaseBuildValue(firebaseApiKey))
        buildConfigField("String", "KHATMA_GOOGLE_WEB_CLIENT_ID", firebaseBuildValue(googleWebClientId))
    }

    buildTypes {
        debug {
            // CRITICAL: never generate a random per-run debug certificate.
            // CI signs the unsigned preview APK using the owner-controlled stable key.
            signingConfig = null
            // Preview installs beside the working V0.4 instead of replacing it.
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            buildConfigField("String", "API_BASE_URL", "\"" + escapedApiBaseUrl + "\"")
            buildConfigField("String", "V2_API_BASE_URL", "\"" + escapedSecurePreviewUrl + "\"")
            buildConfigField("boolean", "V2_PREVIEW_ENABLED", securePreviewEnabled.toString())
        }
        release {
            // Preserve the installed preview identity for a non-destructive upgrade.
            // A different production package would be a new app with separate data.
            applicationIdSuffix = ".preview"
            buildConfigField("String", "API_BASE_URL", "\"" + escapedApiBaseUrl + "\"")
            buildConfigField("String", "V2_API_BASE_URL", "\"" + escapedSecurePreviewUrl + "\"")
            buildConfigField("boolean", "V2_PREVIEW_ENABLED", securePreviewEnabled.toString())
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Keep Android 36 compatible versions pinned in source; no CI-time sed patching.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.batoulapps.adhan:adhan2:0.0.6")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
