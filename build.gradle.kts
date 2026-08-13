// GrandCouncil（军机处）— 手机端多 agent 编排客户端
// 根构建脚本：仅声明插件版本，模块配置见 app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
