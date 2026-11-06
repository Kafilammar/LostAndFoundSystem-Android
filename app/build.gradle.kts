plugins {
    alias(libs.plugins.android.application)

    // Google Services - Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.lostandfoundsystem"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.lostandfoundsystem"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // AndroidX
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}