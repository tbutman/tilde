plugins {
    id("com.android.application")
}

// Everything personal (name, links, phone numbers) comes from profile.local.properties, which git
// ignores, and only seeds the profile on first launch; after that the profile is edited in the app.
// A build without the file starts with an empty profile. See profile.example.properties.
val profileSeed: String = rootProject.file("profile.local.properties").takeIf { it.exists() }?.readText() ?: ""

fun javaString(value: String) =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n") + "\""

android {
    namespace = "com.tbutman.nfcshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tbutman.nfcshare"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.1"
        buildConfigField("String", "PROFILE_SEED", javaString(profileSeed))
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
    implementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
}
