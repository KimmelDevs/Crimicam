plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
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
    }
    buildFeatures {
        viewBinding = true
    }
    aaptOptions {
        noCompress += "tflite"  // ✅ Also correct
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

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.foundation.v160)

    implementation(libs.androidx.animation)

    implementation(libs.androidx.foundation.vversion)

    implementation (libs.accompanist.flowlayout)

    implementation (libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.firebase.firestore.ktx.v24103)

    implementation (libs.androidx.datastore.preferences)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    //ine na 7 ayaw ig update ira version
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-extensions:1.3.4")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:guava:31.1-android")
    implementation("io.getstream:stream-webrtc-android:1.1.3")

    // Or use Google's WebRTC
    implementation("io.getstream:stream-webrtc-android:1.1.3")

    // Coroutines for WebRTC
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // ML Kit for Face Detection (FREE)
    implementation("com.google.mlkit:face-detection:16.1.5")

    // Image processing
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // For image picking
    implementation("androidx.activity:activity-compose:1.8.2")

    //kanan location
    implementation("com.google.android.gms:play-services-location:21.1.0")

    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.github.MKergall:osmbonuspack:6.9.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    //jetpackcompose lacking dependenciasodasiodjasd
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // ML Kit Pose Detection uHDIUHOIHOIAJS for ml in progress
    implementation("com.google.mlkit:pose-detection:18.0.0-beta3")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta3")

    //tensor flow stuff
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

}