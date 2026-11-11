plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "ru.dikoveriya.lab"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.dikoveriya.lab"
        minSdk = 26
        targetSdk = 35
        versionCode = 60000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "0.6.0-dev"
    }
    signingConfigs {
        if (file("signing/dikoveriya.p12").isFile) {
            create("persistent") {
            storeFile = file("signing/dikoveriya.p12")
            storeType = "PKCS12"
            storePassword = System.getenv("LAB_KEYSTORE_PASS") ?: ""
            keyAlias = System.getenv("LAB_KEY_ALIAS") ?: "dikoveriya"
            keyPassword = System.getenv("LAB_KEY_PASS") ?: ""
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (file("signing/dikoveriya.p12").isFile) {
                signingConfig = signingConfigs.getByName("persistent")
            } else if (System.getenv("GITHUB_ACTIONS") == "true") {
                error("Permanent signing key is missing; refusing an APK signed with a temporary debug key")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (file("signing/dikoveriya.p12").isFile) {
                signingConfig = signingConfigs.getByName("persistent")
            } else if (System.getenv("GITHUB_ACTIONS") == "true") {
                error("Permanent signing key is missing; refusing an APK signed with a temporary key")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
