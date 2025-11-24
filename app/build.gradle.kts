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
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    implementation 'androidx.core:core-ktx:1.3.1'
    implementation 'androidx.appcompat:appcompat:1.2.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.0.1'
    implementation 'androidx.legacy:legacy-support-v4:1.0.0'

    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.8"

    //sdp
    implementation 'com.intuit.sdp:sdp-android:1.0.6'

    //Material design
    implementation 'com.google.android.material:material:1.2.1'

    def room_version = "2.8.2"
    def lifecycle_version = "2.10.0-alpha02"

    implementation "androidx.room:room-runtime:$room_version"
    kapt "androidx.room:room-compiler:$room_version"
    implementation "androidx.room:room-ktx:$room_version"

    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version"
    implementation "androidx.lifecycle:lifecycle-common-java8:$lifecycle_version"

    def camerax_version = "1.0.0-beta08"

    implementation "androidx.camera:camera-camera2:$camerax_version"
    implementation "androidx.camera:camera-lifecycle:$camerax_version"
    implementation "androidx.camera:camera-view:1.0.0-alpha15"
}