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
//    private const val aliyunMjDefName = "632a761fe39d7932770f41cf"
//    private const val aliyunMjDefPassword = "obLVJ9r]Cx8["
        private const val aliyunMjDefName = "6082262a8e4139b4d7c9ae3b"
    private const val aliyunMjDefPassword = "MA_JkIG_xgu0"

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
            mavenPassword(
                aliyunSnapshotAndArtifacts,
                aliyunMjDefName,
                aliyunMjDefPassword
            )
            mavenPassword(
                aliyunReleaseAndArtifacts,
                aliyunMjDefName,
                aliyunMjDefPassword
            )
//            可能会影响下载速度，如果需要可以单独放开
//            mavenCentral()
//            mavenLocal()
//            google()
//            过时的jcenter
//            jcenter()
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
