# GrandCouncil 混淆规则（M1 未启用 minify，规则占位）
# kotlinx.serialization 在启用 R8 时需保留（M4 发布前补充）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.grandcouncil.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.grandcouncil.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
