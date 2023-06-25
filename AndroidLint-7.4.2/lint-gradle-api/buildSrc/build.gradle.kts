plugins {
    `kotlin-dsl`
    kotlin("jvm") version "1.6.21"
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
    /* The request for this plugin could not be satisfied because the plugin
     is already on the classpath with an unknown version, so compatibility cannot be checked

     *so there all Lint lib need compileOnly*
    */
    gradleApi()
    implementation("com.android.tools.build:gradle:7.4.2")
    implementation(kotlin("stdlib"))
    compileOnly("com.android.tools.lint:lint:30.4.2")
    compileOnly("com.android.tools.lint:lint-model:30.4.2")
    compileOnly("com.android.tools:common:30.4.2")
    compileOnly("com.android.tools:sdk-common:30.4.2")

}
