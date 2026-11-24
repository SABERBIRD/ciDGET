plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// versionCode: CI run number, else 1. versionName: the tag without the leading "v", else 0.0.0-dev.
val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val tagVersion: String? = System.getenv("GITHUB_REF_NAME")
    ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }
    ?.removePrefix("v")

android {
    namespace = "io.github.saberbird.cidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.saberbird.cidget"
        minSdk = 31
        targetSdk = 36
        versionCode = ciRunNumber
        versionName = tagVersion ?: "0.0.0-dev"
    }

    // Public signing key committed on purpose (D14); see docs/BUILD_AND_RELEASE.md.
    signingConfigs {
        create("cidget") {
            storeFile = rootProject.file("app/signing/cidget-public.jks")
            storePassword = "cidget-public"
            keyAlias = "cidget"
            keyPassword = "cidget-public"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("cidget")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("cidget")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        // T15: every user-visible string must exist in values/ and values-uk/.
        error += "MissingTranslation"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":github"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
