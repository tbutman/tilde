import java.util.Properties

plugins {
    id("com.android.application")
}

// Phone numbers for the "Share contact" mode and the WhatsApp preset come from
// contact.local.properties, which git ignores, so they never land in the repository. Without the
// file the contact card has no numbers and the WhatsApp preset is hidden.
val contactProperties = Properties().apply {
    val file = rootProject.file("contact.local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val phones: List<Pair<String, String>> = generateSequence(1) { it + 1 }
    .map { (contactProperties.getProperty("phone.$it.label") ?: "") to (contactProperties.getProperty("phone.$it.number") ?: "") }
    .takeWhile { it.second.isNotBlank() }
    .toList()
val whatsapp: String = contactProperties.getProperty("whatsapp.number") ?: ""

fun javaString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.tbutman.nfcshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tbutman.nfcshare"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
        buildConfigField("String[]", "PHONE_LABELS", "{" + phones.joinToString(",") { javaString(it.first) } + "}")
        buildConfigField("String", "WHATSAPP_NUMBER", javaString(whatsapp))
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
