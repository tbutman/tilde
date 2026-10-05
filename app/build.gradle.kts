import java.util.Properties

plugins {
    id("com.android.application")
}

// Phone numbers for the "Share contact" mode come from contact.local.properties, which git ignores,
// so they never land in the repository. Without the file the contact card simply has no numbers.
val phones: List<Pair<String, String>> = Properties().run {
    val file = rootProject.file("contact.local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    generateSequence(1) { it + 1 }
        .map { (getProperty("phone.$it.label") ?: "") to (getProperty("phone.$it.number") ?: "") }
        .takeWhile { it.second.isNotBlank() }
        .toList()
}

fun javaString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.tbutman.nfcshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tbutman.nfcshare"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
        buildConfigField("String[]", "PHONE_LABELS", "{" + phones.joinToString(",") { javaString(it.first) } + "}")
        buildConfigField("String[]", "PHONE_NUMBERS", "{" + phones.joinToString(",") { javaString(it.second) } + "}")
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
    implementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
}
