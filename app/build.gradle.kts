plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.remotwrt.bot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.remotwrt.bot"
        minSdk = 26
        targetSdk = 34
        // Passed by CI as -PverCode=<github.run_number> so it always matches
        // the GitHub Release tag (vN) the in-app updater compares against.
        // Falls back to 1 for local/manual builds where that's not set.
        versionCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0.0"
    }

    // Populated from env vars that only exist in CI (set from the
    // RELEASE_KEYSTORE_BASE64/RELEASE_KEYSTORE_PASSWORD/etc. repo secrets).
    // Signing must be *consistent* across every release build -- if each
    // build used a different (or the default auto-generated) key, Android
    // would refuse to install an "update" over the existing app at all
    // (signature mismatch), which would break the whole in-app updater.
    val hasReleaseSigning = System.getenv("RELEASE_KEYSTORE_PATH") != null
    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("RELEASE_KEYSTORE_PATH")!!)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
