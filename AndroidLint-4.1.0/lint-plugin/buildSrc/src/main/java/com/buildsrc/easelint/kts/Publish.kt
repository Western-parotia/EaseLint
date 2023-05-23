package com.buildsrc.easelint.kts

import com.buildsrc.easelint.kts.Repositories.mavenPassword
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.maven.MavenPublication

object Publish {
    private const val VERSION = "0.0.1"
    const val SNAPSHOT = true

    // 基于 27.1.1版本的 lint gradle 进行二开，对应AGP 4.1.0
    // AGP 7.4.2 对应的是 30.4.2
    private const val ARTIFACT_ID = "4.1.0-lint-plugin"

    //由于阿里云 制品 采取分仓管理snapshot版本，默认也会忽略-SNAPSHOT的策略模式，所以这里从group进行区分，便于管理
    private val GROUP_ID = if (SNAPSHOT) "com.easelint.snapshot" else "com.easelint"

    private fun getTimestamp(): String {
        return java.text.SimpleDateFormat(
            "yyyy-MM-dd-hh-mm-ss",
            java.util.Locale.CHINA
        ).format(java.util.Date())
    }

    object Maven {
        private val repositoryUserName: String
        private val repositoryPassword: String

        init {
            val lp = PropertiesUtils.localProperties
            val name = lp.getProperty("repositoryUserName")
            val password = lp.getProperty("repositoryPassword")
            if (name.isNullOrEmpty() || password.isNullOrEmpty()) {
                throw IllegalArgumentException("请在local.properties添加私有仓库的用户名（repositoryUserName）和密码（repositoryPassword）")
            }
            repositoryUserName = name
            repositoryPassword = password

        }

        fun setGAV(mp: MavenPublication) {
            mp.groupId = GROUP_ID
            mp.artifactId = ARTIFACT_ID
            mp.version = VERSION + "-" + getTimestamp()
            println("publish=> ${mp.groupId}:${mp.artifactId}:${mp.version}")
        }

        /**
         * 使用本地账号密码（用于推送）
         */
        fun aliyunReleaseRepositories(rh: RepositoryHandler) {
            rh.mavenPassword(
                Repositories.aliyunReleaseAndArtifacts,
                repositoryUserName,
                repositoryPassword
            )
        }

        fun aliyunSnapshotRepositories(rh: RepositoryHandler) {
            rh.mavenPassword(
                Repositories.aliyunSnapshotAndArtifacts,
                repositoryUserName,
                repositoryPassword
            )
        }

    }

}
