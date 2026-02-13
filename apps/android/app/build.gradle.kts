plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt") version "1.23.0"
}

android {
    namespace = "com.onyx.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.onyx.android"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // PdfiumAndroid and MyScript both bundle libc++_shared.so.
            // Keep one copy to avoid mergeDebugNativeLibs duplicate-path failures.
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

// Room schema export location (for migration tracking)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose BOM (Bill of Materials)
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Core Compose dependencies
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Material Components (for XML themes)
    implementation("com.google.android.material:material:1.11.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // kotlinx.serialization for JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.2")

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Jetpack Ink - Alpha (API may change in future releases)
    implementation("androidx.ink:ink-authoring:1.0.0-alpha02")
    implementation("androidx.ink:ink-brush:1.0.0-alpha02")
    implementation("androidx.ink:ink-geometry:1.0.0-alpha02")
    implementation("androidx.ink:ink-rendering:1.0.0-alpha02")
    implementation("androidx.ink:ink-strokes:1.0.0-alpha02")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // MyScript Interactive Ink SDK v4.3.0
    implementation("com.myscript:iink:4.3.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // MuPDF - PDF rendering (AGPL-3.0 license)
    implementation("com.artifex.mupdf:fitz:1.24.10")

    // PdfiumAndroid - PDF rendering (Apache 2.0 / BSD)
    // P0.0 Pre-Gate: Using min_SDK_28 branch commit (v1.10.0 has minSdk 30, incompatible)
    // Working coordinate: commit b68e47459ab90501fd377aa6456618bc87f06d3c from min_SDK_28 branch
    implementation("com.github.Zoltaneusz:PdfiumAndroid:b68e47459ab90501fd377aa6456618bc87f06d3c")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debugging
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Enable JUnit 5 platform for unit tests
tasks.withType<Test> {
    useJUnitPlatform()
}

// ktlint configuration
ktlint {
    version.set("1.0.1")
    debug.set(false)
    verbose.set(true)
    android.set(true)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(false)

    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

// detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false

    // Use config file if it exists (generated at project root level)
    val detektConfigFile = file("${rootProject.projectDir}/config/detekt/detekt.yml")
    if (detektConfigFile.exists()) {
        config.setFrom(detektConfigFile)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
