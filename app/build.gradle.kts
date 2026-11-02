import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val communityProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val communityKey = communityProperties.getProperty("community.supabase.publishableKey", "")
require(communityKey.isEmpty() || communityKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]{10,200}"))) {
    "Community Android configuration accepts only a Supabase publishable key"
}
val communityUrl = communityProperties.getProperty("community.supabase.url", "")
require(communityUrl.isEmpty() || runCatching {
    val uri = URI(communityUrl)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
        uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty() in listOf("", "/")
}.getOrDefault(false)) { "Community Android configuration requires an HTTPS origin without credentials" }
val authRedirectHost = communityProperties.getProperty("community.auth.redirectHost", "")
require(authRedirectHost.isEmpty() || authRedirectHost.matches(Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*\\.[a-z]{2,63}"))) {
    "Community Auth redirect requires a canonical HTTPS host"
}
val authRequested = communityProperties.getProperty("community.auth.enabled", "false") == "true"
val authIsolated = communityProperties.getProperty("community.auth.isolatedVerified", "false") == "true"
val authProvider = communityProperties.getProperty("community.auth.provider", "")
val authConfigured = authRequested && authIsolated && communityUrl.isNotBlank() && communityKey.isNotBlank() &&
    authRedirectHost.isNotBlank() && !authRedirectHost.endsWith(".supabase.co") && authProvider in setOf("github", "google", "azure", "apple", "gitlab", "keycloak")

fun communityString(name: String): String = "\"" +
    communityProperties.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "").replace("\r", "") + "\""

android {
    namespace = "com.example.oryn.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.oryn.reader"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "COMMUNITY_AUTH_REDIRECT_HOST", communityString("community.auth.redirectHost"))
        buildConfigField("String", "COMMUNITY_AUTH_PROVIDER", communityString("community.auth.provider"))
        buildConfigField("boolean", "COMMUNITY_AUTH_ISOLATED_VERIFIED", authIsolated.toString())
        manifestPlaceholders["communityAuthRedirectHost"] = authRedirectHost
        manifestPlaceholders["communityAuthEnabled"] = "false"
        buildConfigField("boolean", "COMMUNITY_AUTH_ENABLED", "false")
        buildConfigField("String", "COMMUNITY_SUPABASE_URL", communityString("community.supabase.url"))
        buildConfigField("String", "COMMUNITY_SUPABASE_PUBLISHABLE_KEY", communityString("community.supabase.publishableKey"))
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "COMMUNITY_AUTH_ENABLED", authConfigured.toString())
            manifestPlaceholders["communityAuthEnabled"] = authConfigured.toString()
        }
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("io.github.oleksandrbalan:pagecurl:1.5.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

}
