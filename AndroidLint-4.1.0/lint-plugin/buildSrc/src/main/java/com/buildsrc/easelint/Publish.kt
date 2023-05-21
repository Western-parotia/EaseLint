package com.buildsrc.easelint

import com.buildsrc.easelint.Repositories.mavenPassword
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.maven.MavenPublication

object Publish {
    private const val VERSION = "0.0.1"
    const val SNAPSHOT = true

    // 基于 27.1.1版本的 lint gradle 进行二开，对应AGP 4.1.0
    // AGP 7.4.2 对应的是 30.4.2
    private const val ARTIFACT_ID = "4.1.0-lint-plugin"
    private const val GROUP_ID = "com.easelint"

    object Version {
        val versionName = if (SNAPSHOT) "$VERSION-SNAPSHOT" else VERSION
        const val versionCode = 1
        const val artifactId = ARTIFACT_ID

        private fun getTimestamp(): String {
            return java.text.SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss",
                java.util.Locale.CHINA
            ).format(java.util.Date())
        }

        fun getVersionTimestamp(): String {
            return "$versionName-${getTimestamp()}"
        }
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
            mp.version = VERSION
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
