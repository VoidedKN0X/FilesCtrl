plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vknxworks.filesctrl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vknxworks.filesctrl"
        minSdk = 33
        targetSdk = 35
        // CI passes the run number so every build installs over the previous one.
        // Bump both to publish a new release (see CHANGELOG.md).
        versionCode = 42
        versionName = "1.3.4"

        // 64-bit ARM phones only (arm64-v8a), like every Ctrl app. The NFS client (libnfs) is native.
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=none") }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Release signing from env (GitHub secrets). Local builds fall back to the debug key; CI must never publish
    // a debug-signed release, so it fails instead.
    val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
    val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    val haveReleaseKey = !ksPath.isNullOrBlank() && file(ksPath).exists() &&
        !System.getenv("SIGNING_STORE_PASSWORD").isNullOrBlank() && !System.getenv("SIGNING_KEY_ALIAS").isNullOrBlank()
    val wantsRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
    if (onCi && wantsRelease && !haveReleaseKey) {
        throw GradleException("Release signing values are missing on CI; refusing to sign the release with the debug key.")
    }
    signingConfigs {
        if (haveReleaseKey) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs { useLegacyPackaging = false }
    }
    testOptions {
        // The protocol tests run on the JVM against real servers; Android calls just return defaults there.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources { excludes += listOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST") }
    }
    // No dependency metadata blob in the APK: it's encrypted for Google Play, so nobody checking the build can read it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Shared look with the other Ctrl apps: frosted tab island, app lock.
    implementation("dev.chrisbanes.haze:haze:1.6.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.window:window:1.3.0")

    // Network file systems (NFS is native, via libnfs).
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.rapid7.client:dcerpc:0.12.13") // SMB share listing
    implementation("com.hierynomus:sshj:0.38.0")
    implementation("commons-net:commons-net:3.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")

    // Archives: browse, extract and create zip, 7z, rar (RAR4), tar and compressed files.
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10") // xz, lzma and 7z's LZMA/LZMA2
    implementation("com.github.luben:zstd-jni:1.5.6-9@aar") // Zstandard (.zst)
    implementation("org.brotli:dec:0.1.2") // Brotli (.br)
    implementation("net.lingala.zip4j:zip4j:2.11.5") // zip with ZipCrypto/AES passwords
    implementation("com.github.junrar:junrar:7.5.5") // RAR (RAR4)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")

    val coil = "3.1.0"
    implementation("io.coil-kt.coil3:coil-compose:$coil")
    implementation("io.coil-kt.coil3:coil-video:$coil")
}
