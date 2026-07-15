import java.util.Properties
import java.io.FileInputStream

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

// Load .env properties for signing
val envFile = rootProject.file(".env")
val envProps = Properties().apply {
  if (envFile.exists()) {
    load(FileInputStream(envFile))
  }
}

// CI/CD signing support (from GitHub Actions -P parameters)
val ciKeystoreFile: String? by project
val ciKeystorePassword: String? by project
val ciKeyAlias: String? by project
val ciKeyPassword: String? by project

android {
  namespace = "com.phenix.wirelessadb"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.phenix.wirelessadb"
    minSdk = 26
    targetSdk = 35
    versionCode = 7
    versionName = "1.4.0"
  }

  signingConfigs {
    create("release") {
      // CI/CD builds (GitHub Actions) take precedence
      if (ciKeystoreFile != null) {
        storeFile = file(ciKeystoreFile!!)
        storePassword = ciKeystorePassword ?: ""
        keyAlias = ciKeyAlias ?: "phenkey"
        keyPassword = ciKeyPassword ?: ""
      } else {
        // Local builds from .env file
        storeFile = file(envProps.getProperty("KEYSTORE_FILE", "../rootadb.keystore"))
        storePassword = envProps.getProperty("KEYSTORE_PASSWORD", "")
        keyAlias = envProps.getProperty("KEY_ALIAS", "phenkey")
        keyPassword = envProps.getProperty("KEY_PASSWORD", "")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
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
    viewBinding = true
    buildConfig = true
    aidl = true
  }

  testOptions {
    unitTests {
      // Let non-Robolectric unit tests call android.util.Log etc. without
      // "Method not mocked" crashes
      isReturnDefaultValues = true
    }
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

  // TCP Relay Server
  implementation("io.ktor:ktor-network:2.3.7")

  // JSON for trusted device storage
  implementation("com.google.code.gson:gson:2.10.1")

  // Encrypted storage for SSH credentials
  implementation("androidx.security:security-crypto:1.1.0-alpha06")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

  // LocalBroadcastManager
  implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

  // ViewPager2 for tabs
  implementation("androidx.viewpager2:viewpager2:1.0.0")

  // Fragment with Kotlin extensions
  implementation("androidx.fragment:fragment-ktx:1.6.2")

  // Activity with Kotlin extensions (for viewModels delegate)
  implementation("androidx.activity:activity-ktx:1.8.2")

  // ViewModel
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

  // WorkManager for background tasks
  implementation("androidx.work:work-runtime-ktx:2.9.0")

  // JSch for SSH tunneling (Warpgate) - Updated to maintained fork
  implementation("com.github.mwiede:jsch:2.27.7")

  // Shizuku for non-root privileged access
  implementation("dev.rikka.shizuku:api:13.1.5")
  implementation("dev.rikka.shizuku:provider:13.1.5")

  // Conscrypt for TLS 1.3 support (ADB pairing)
  implementation("org.conscrypt:conscrypt-android:2.5.2")

  // ZXing for QR code generation
  implementation("com.google.zxing:core:3.5.2")

  // ZXing Android Embedded for QR code scanning
  implementation("com.journeyapps:zxing-android-embedded:4.3.0")

  // Testing
  testImplementation("junit:junit:4.13.2")
  // Desktop Conscrypt so Robolectric can load a JVM-compatible provider
  // (conscrypt-android's JNI cannot load on the host JVM)
  testImplementation("org.conscrypt:conscrypt-openjdk-uber:2.6.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  testImplementation("io.mockk:mockk:1.13.9")
  testImplementation("org.robolectric:robolectric:4.11.1")
  testImplementation("androidx.test:core:1.5.0")
  testImplementation("androidx.arch.core:core-testing:2.2.0")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
