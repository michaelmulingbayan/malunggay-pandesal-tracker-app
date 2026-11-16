import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Reads values from env.properties in the project root (like Laravel's .env).
 * The file is git-ignored; copy env.properties.example to env.properties to start.
 */
val envProperties = Properties().apply {
    val envFile = rootProject.file("env.properties")
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}

fun env(key: String, default: String): String = envProperties.getProperty(key)?.trim() ?: default

android {
    namespace = "com.example.malunggaytrackerapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.malunggaytrackerapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Set API_BASE_URL_DEBUG in env.properties. Default 10.0.2.2 is your PC's localhost
            // as seen from the Android emulator (run: php artisan serve --host=0.0.0.0 --port=8000).
            buildConfigField("String", "API_BASE_URL", "\"${env("API_BASE_URL_DEBUG", "http://10.0.2.2:8000/")}\"")
        }
        release {
            // Set API_BASE_URL_RELEASE in env.properties to your production HTTPS URL.
            buildConfigField("String", "API_BASE_URL", "\"${env("API_BASE_URL_RELEASE", "https://api.example.com/")}\"")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
