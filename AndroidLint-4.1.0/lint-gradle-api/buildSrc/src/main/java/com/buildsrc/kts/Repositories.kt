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
