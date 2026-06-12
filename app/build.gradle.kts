import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    kotlin("kapt")
}

android {
    namespace = "com.app.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.app.assistant"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Read local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        // Define BuildConfig fields for API keys
        // Make sure YOUTUBE_API_KEY and GROQ_API_KEY exist in your local.properties
        buildConfigField("String", "YOUTUBE_API_KEY",
            "\"${localProperties.getProperty("YOUTUBE_API_KEY") ?: ""}\"" // Add quotes
        )

        buildConfigField("String", "GROQ_API_KEY",
            "\"${localProperties.getProperty("GROQ_API_KEY") ?: ""}\"" // Add quotes
        )

        buildConfigField("String", "EDGE_TTS_SUBSCRIPTION_KEY",
            "\"${localProperties.getProperty("EDGE_TTS_SUBSCRIPTION_KEY") ?: ""}\"" // Add quotes
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // You might want to also define buildConfigFields for release
            // if you have different keys for release builds, or if local.properties is not available in CI
        }
        debug {
            // BuildConfigFields from defaultConfig are inherited.
            // Add specific debug configurations here if needed, for example:
            // isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true // Ensure buildConfig is enabled
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidxComposeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.okhttp)
    constraints {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") {
            because("Kotlin 1.9.0 compatibility")
        }
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3") {
            because("Kotlin 1.9.0 compatibility")
        }
    }
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.translate)
    implementation(libs.commonmark)
    //implementation ("com.github.jeziellago:compose-markdown:0.5.4")
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.play.services.location)
    implementation(libs.androidx.ui.test.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.runtime.saveable)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //MediaPipe library
    implementation(libs.mediapipe.tasks.text)

    // Sherpa ONNX Offline STT
    implementation(files("libs/sherpa-onnx-1.13.2.aar"))
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3")
    }
}
