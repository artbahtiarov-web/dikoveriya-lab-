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
        versionCode = 90000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "0.9.0-rc1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            } else if (System.getenv("GITHUB_ACTIONS") == "true" && System.getenv("LAB_PR_DEBUG_BUILD") != "true") {
                // Only pull-request validation is allowed to use the temporary Android debug certificate.
                // Release builds must always use the existing permanent signing certificate.
                error("Permanent signing key is missing; refusing an APK signed with a temporary debug key")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (file("signing/dikoveriya.p12").isFile) {
                signingConfig = signingConfigs.getByName("persistent")
            } else if (System.getenv("GITHUB_ACTIONS") == "true" && System.getenv("LAB_PR_DEBUG_BUILD") != "true") {
                // The release variant is configured for PR debug tests but NEVER built there.
                // The main/release workflow requires and verifies the permanent certificate.
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
// The font files are not downloaded at runtime. They are vendor-pinned OFL
// assets verified during the build and packaged into the installed APK.
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/font-assets"))
val prepareLabelFonts by tasks.registering(Exec::class) {
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/prepare_label_fonts.py")
    inputs.file(rootProject.file("scripts/label_fonts_manifest.json"))
    inputs.file(rootProject.file("scripts/prepare_label_fonts.py"))
    outputs.dir(layout.buildDirectory.dir("generated/font-assets"))
}
tasks.named("preBuild") { dependsOn(prepareLabelFonts) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(prepareLabelFonts) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
