plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"


}

android {
    namespace = "com.example.crimicam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.crimicam"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
    aaptOptions {
        noCompress += "tflite"
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
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.1")
    implementation("androidx.compose.ui:ui")
    implementation(libs.androidx.room.common.jvm)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials.play.services.auth)

    implementation(libs.androidx.foundation)
    implementation("io.insert-koin:koin-annotations:1.3.0")
    ksp("io.insert-koin:koin-ksp-compiler:1.3.0")


    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.foundation.v160)

    implementation(libs.androidx.animation)

    implementation(libs.androidx.foundation.vversion)

    implementation (libs.accompanist.flowlayout)

    implementation (libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.firebase.firestore.ktx.v24103)

    implementation (libs.androidx.datastore.preferences)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Camera dependencies
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-extensions:1.3.4")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:guava:31.1-android")

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.1.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ML Kit for Face Detection
    implementation("com.google.mlkit:face-detection:16.1.5")

    // Image processing
    implementation("io.coil-kt:coil-compose:2.5.0")

    // For image picking
    implementation("androidx.activity:activity-compose:1.8.2")

    // Location
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // OSM Map
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.github.MKergall:osmbonuspack:6.9.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // ML Kit Pose Detection - FIXED VERSION
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")

    // TensorFlow Lite

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.14.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")


    implementation("io.insert-koin:koin-android:3.5.6")

    implementation("io.insert-koin:koin-androidx-compose:3.5.6")



}