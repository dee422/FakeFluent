plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    kotlin("kapt")
}

android {
    namespace = "com.dee.android.pbl.fakefluent"
    compileSdk = 35 // 推荐与 JDK 21 配对使用 35

    defaultConfig {
        applicationId = "com.dee.android.pbl.fakefluent"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 【核心修复：Windows 环境下统一 JVM 目标】
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val room_version = "2.6.1"

    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    // 如果你使用的是 Kotlin，需要这个注解处理器
    kapt("androidx.room:room-compiler:$room_version")

    // 如果你没用 kapt，可能需要用 ksp (根据你项目配置选择)
    // ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
}