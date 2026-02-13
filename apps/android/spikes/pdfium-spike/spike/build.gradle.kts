plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.onyx.spike.pdfium"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.onyx.spike.pdfium"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    // PdfiumAndroid - PDF rendering (Apache 2.0 / BSD)
    // P0.0 Pre-Gate: Using min_SDK_28 branch commit (v1.10.0 has minSdk 30, incompatible)
    // Working coordinate: commit b68e47459ab90501fd377aa6456618bc87f06d3c from min_SDK_28 branch
    implementation("com.github.Zoltaneusz:PdfiumAndroid:b68e47459ab90501fd377aa6456618bc87f06d3c")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
