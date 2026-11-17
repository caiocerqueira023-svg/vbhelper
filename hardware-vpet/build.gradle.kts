plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tamerslab.hardware.vpet"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // A-Com USB serial adapter (CDC/ACM); the version is fixed by the recovery notes.
    implementation(libs.usb.serial)
    testImplementation(libs.junit)
}
