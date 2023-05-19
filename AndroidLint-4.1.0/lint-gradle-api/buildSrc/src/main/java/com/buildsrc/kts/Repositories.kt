package com.buildsrc.kts

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.maven

object Repositories {
    private const val aliyunNexusPublic = "https://maven.aliyun.com/nexus/content/groups/public/"
    private const val aliyunPublic = "https://maven.aliyun.com/repository/public/"
    private const val aliyunGoogle = "https://maven.aliyun.com/repository/google/"
    private const val aliyunJcenter = "https://maven.aliyun.com/repository/jcenter/"
    private const val aliyunCentral = "https://maven.aliyun.com/repository/central/"
    private const val jitpackIo = "https://jitpack.io"
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

        }
    }

    internal fun RepositoryHandler.mavenPassword(url: String, pwdName: String, pwd: String) {
        maven(url) {
            credentials {
                username = pwdName
                password = pwd
            }
        }
    }
}
