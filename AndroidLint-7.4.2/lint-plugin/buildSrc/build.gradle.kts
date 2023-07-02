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
    internal const val aliyunReleaseAndArtifacts =
        "https://packages.aliyun.com/maven/repository/2196753-release-jjUEtd/"
    internal const val aliyunSnapshotAndArtifacts =
        "https://packages.aliyun.com/maven/repository/2196753-snapshot-XaSZiY"

    //公共账号密码，只可用于拉取
    private const val aliyunMjDefName = "642b9f209f62bf75b33fc1ae"
    private const val aliyunMjDefPassword = "EkNR7ao]bCHh"

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
            mavenPassword(
                aliyunReleaseAndArtifacts,
                aliyunMjDefName,
                aliyunMjDefPassword
            )
            mavenPassword(
                aliyunSnapshotAndArtifacts,
                aliyunMjDefName,
                aliyunMjDefPassword
            )

        }
    }

    private fun RepositoryHandler.mavenPassword(url: String, pwdName: String, pwd: String) {
        maven(url) {
            credentials {
                username = pwdName
                password = pwd
            }
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
    implementation("com.android.tools.build:gradle:7.4.2")
    implementation(kotlin("stdlib"))
    compileOnly("com.android.tools.lint:lint-api:30.4.2")

}
