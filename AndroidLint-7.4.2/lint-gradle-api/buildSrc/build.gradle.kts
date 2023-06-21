plugins {
    `kotlin-dsl`
    // gradle 7.x 之后捆绑的 kotlin 更新为 1.4.31
    id("org.jetbrains.kotlin.jvm") version "1.4.31"

}


object Repositories {
    private const val aliyunNexusPublic = "https://maven.aliyun.com/nexus/content/groups/public/"
    private const val aliyunPublic = "https://maven.aliyun.com/repository/public/"
    private const val aliyunGoogle = "https://maven.aliyun.com/repository/google/"
    private const val aliyunJcenter = "https://maven.aliyun.com/repository/jcenter/"
    private const val aliyunCentral = "https://maven.aliyun.com/repository/central/"
    private const val jitpackIo = "https://jitpack.io"
    private const val google = "https://maven.google.com/"

    /**
     * 默认的需要拉的库
     */
    @JvmStatic
    fun defRepositories(resp: RepositoryHandler) {
        resp.apply {
            maven(aliyunNexusPublic)
            maven(aliyunPublic)
            maven(aliyunGoogle)
            maven(aliyunJcenter)
            maven(aliyunCentral)
            maven(jitpackIo)
            maven(google)

        }
    }
}
repositories {
    Repositories.defRepositories(this)
    mavenCentral()
    google()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // 这里导入 gradle 将导致与根目录的 plugin 导入冲突：
// The request for this plugin could not be satisfied because the plugin
// is already on the classpath with an unknown version, so compatibility cannot be checked
//    implementation("com.android.tools.build:gradle:7.4.1")// 7.4.2 与 项目的apg 冲突
//    compileOnly("com.android.tools.build:gradle:7.4.2")// 7.4.2 与 项目的apg 冲突
    //开发完 EaseLintMain 之后将依赖改为 compileOnly，避免因为BuildSrc 的ClassLoader提前加载了 Lint相关类
    // 导致 app module 编译失败
    compileOnly("com.android.tools.lint:lint:30.4.2")
    compileOnly("com.android.tools.lint:lint-api:30.4.2")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    compileOnly("com.android.tools.external.org-jetbrains:uast:30.4.2")

}
