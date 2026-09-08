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
        versionName = "0.1.00"
    }

    // 统一 release 签名：本地与 CI 共用一把 key，避免不同签名覆盖安装被拦截。
    // keystore 从环境变量读；缺省回退 debug 签名（开发/未配置密钥时不打断构建）。
    signingConfigs {
        create("release") {
            val storeFileEnv = System.getenv("SIGNING_STORE_FILE")
            val storePassEnv = System.getenv("SIGNING_STORE_PASSWORD")
            val keyAliasEnv = System.getenv("SIGNING_KEY_ALIAS")
            val keyPassEnv = System.getenv("SIGNING_KEY_PASSWORD")
            if (!storeFileEnv.isNullOrBlank() && storePassEnv != null &&
                keyAliasEnv != null && keyPassEnv != null
            ) {
                storeFile = file(storeFileEnv)
                storePassword = storePassEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPassEnv
            }
        }
    }

    buildTypes {
        release {
            // 有统一签名环境变量则用之，否则回退 debug（开发构建不签名也能装）
            signingConfig = if (System.getenv("SIGNING_STORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation("org.nanohttpd:nanohttpd:2.3.1") // release builds compile DebugApiServer too (runtime-guarded by FLAG_DEBUGGABLE)
}
