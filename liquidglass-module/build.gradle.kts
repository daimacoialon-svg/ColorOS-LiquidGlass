plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "top.liquidglass.vector"
    compileSdk = 37
    defaultConfig {
        applicationId = "top.liquidglass.vector"
        minSdk = 28
        targetSdk = 28
        versionCode = 19205265
        versionName = "1.9.205.265"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    compileOnly(project(":xposed-stubs"))
    implementation(project(":backdrop"))
    implementation(libs.kyant.shapes)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
}
