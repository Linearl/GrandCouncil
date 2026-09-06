plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.grandcouncil.remote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.grandcouncil.remote"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
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

    buildFeatures {
        compose = true
    }
}

// built-in Kotlin：jvmTarget 默认跟随 compileOptions.targetCompatibility（AGP 9 文档），
// 无需单独设置 kotlin.compilerOptions

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose（BOM 统一版本）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 网络：Retrofit 3 + OkHttp 5（SSE）
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // 序列化与并发
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 连接配置持久化（token 安全存储见 M2 Keystore 基线）
    implementation(libs.androidx.datastore.preferences)

    // A4 生物识别锁
    implementation(libs.androidx.biometric)

    // Debug-only embedded API server (BuildConfig.DEBUG builds; see DebugApiServer)
    debugImplementation("org.nanohttpd:nanohttpd:2.3.1")
}
