plugins {
    id("com.android.application")
}

import java.util.Properties

// Everything personal (name, links, phone numbers) comes from profile.local.properties, which git
// ignores, and only seeds the profile on first launch of a debug build; after that the profile is
// edited in the app. Release builds always start empty. See profile.example.properties.
val profileSeed: String = rootProject.file("profile.local.properties").takeIf { it.exists() }?.readText() ?: ""

// Release signing: environment variables in CI, otherwise keystore.properties (git-ignored) next to
// this project. Without either, assembleRelease still works and leaves the APK unsigned.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.reader().use(::load) }
fun signing(key: String): String? = System.getenv("TILDE_" + key.uppercase()) ?: keystore.getProperty(key)

fun javaString(value: String) =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n") + "\""

android {
    namespace = "com.tbutman.tilde"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tbutman.tilde"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.2.0-beta.2"
    }

    signingConfigs {
        val storeFile = signing("store_file")
        if (storeFile != null) {
            create("release") {
                this.storeFile = rootProject.file(storeFile)
                storePassword = signing("store_password")
                keyAlias = signing("key_alias")
                keyPassword = signing("key_password")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "PROFILE_SEED", javaString(profileSeed))
            // A separate app ("Tilde dev"), so a debug build installs next to a release instead of
            // replacing it: the real Tilde and its profile stay untouched while changes are tested.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            buildConfigField("String", "PROFILE_SEED", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Material 3: bottom navigation, bottom sheet, switches and text fields that behave natively.
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    // Reads the camera rotation of picked photos on Android 8 and 9 (ImageDecoder does it from 9 up).
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is only a stub in unit tests; the real one, for tests only (saved links).
    testImplementation("org.json:json:20260814")
}
