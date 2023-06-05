// plugins 内 无法访问 buildSrc,plugins
plugins {
    id("com.android.library").version("7.4.2").apply(false)
    id("com.android.application").version("7.4.2").apply(false)
    id("org.jetbrains.kotlin.android").version("1.6.21").apply(false)
}
