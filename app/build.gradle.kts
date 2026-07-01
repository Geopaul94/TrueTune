plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "geo.truetune"
    compileSdk = 35

    // Pin the NDK so every dev machine builds the native detector identically.
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "geo.truetune"
        minSdk = 26          // Oreo: needed for the best AAudio low-latency path.
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase0"

        vectorDrawables { useSupportLibrary = true }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Oboe ships as a *shared* prefab, so the whole app must use the
                // shared C++ runtime (libc++_shared.so) rather than the static one.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        ndk {
            // Phase 0: arm64 for real devices, x86_64 for the emulator. Release
            // will use ABI splits (Phase 5) so users don't download every ABI.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        // Lets CMake consume the Oboe AAR's prefab package via find_package().
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // Dependency injection.
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Low-latency audio I/O (Oboe input + output).
    implementation(libs.oboe)
}
