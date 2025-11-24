plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.colour.sniff"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.colour.sniff"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        androidResources {
            localeFilters += setOf("en")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("cs.p12")
            storePassword = "1234"
            keyAlias = "US"
            keyPassword = "1234"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation fileTree(dir: "libs", include: ["*.jar"])
    implementation "org.jetbrains.kotlin:kotlin-stdlib:2.3.0-Beta2"

    implementation "androidx.core:core-ktx:1.17.0"

    implementation "androidx.appcompat:appcompat:1.7.0-alpha03"

    implementation "androidx.constraintlayout:constraintlayout:2.2.0-alpha02"

    implementation "androidx.legacy:legacy-support-v4:1.0.0"

    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0-RC"

    implementation 'com.intuit.sdp:sdp-android:1.0.6'

    implementation "com.google.android.material:material:1.10.0-alpha03"

    def room_version = "2.8.0-alpha01"
    implementation "androidx.room:room-runtime:$room_version"
    kapt "androidx.room:room-compiler:$room_version"
    implementation "androidx.room:room-ktx:$room_version"

    def lifecycle_version = "2.10.0-alpha03"
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version"
    implementation "androidx.lifecycle:lifecycle-common-java8:$lifecycle_version"

    def camerax_version = "1.6.0-alpha01"
    implementation "androidx.camera:camera-camera2:$camerax_version"
    implementation "androidx.camera:camera-lifecycle:$camerax_version"
    implementation "androidx.camera:camera-view:1.6.0-alpha01"
}