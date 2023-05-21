// Top-level build file where you can add configuration options common to all sub-projects/modules.
com.buildsrc.easelint.GlobalConfig.init(project)
plugins {
    `maven-publish`
}
buildscript {

    repositories {
        com.buildsrc.easelint.Repositories.defRepositories(this)
    }
    dependencies {
        //buildscript 偶尔编译错误 找不到包名，给全包名即可
        classpath(com.buildsrc.easelint.Dependencies.Gradle.agp)
        classpath(com.buildsrc.easelint.Dependencies.Gradle.kgp)
    }
}
allprojects {
    repositories {
        com.buildsrc.easelint.Repositories.defRepositories(this)
    }
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.buildDir)
}

publishing {
    publications {
        /*在buildSrc gradle 添加的任务在AS右侧 看不到task 列表*/
        create<MavenPublication>("lintPlugin") {
            com.buildsrc.easelint.Publish.Maven.setGAV(this)
            artifact("buildSrc/build/libs/buildSrc.jar")
            pom.withXml {
                fun groovy.util.Node.addDependencies(group: String) {
                    val groups = group.split(":")
                    val depNode = appendNode("dependency")
                    depNode.appendNode("groupId", groups[0])
                    depNode.appendNode("artifactId", groups[1])
                    depNode.appendNode("version", groups[2])
                }

                val dependenciesNode = asNode().appendNode("dependencies")
                val srcGradleFile = File(rootDir, "buildSrc/build.gradle.kts")
                val pomRegex = "====pom start====[\\s\\S]+====pom end====".toRegex()
                pomRegex.find(srcGradleFile.readText())?.value?.let { pomSt ->
                    val implRegex = "(?<=implementation\\(\").+(?=\"\\))".toRegex()
                    implRegex.findAll(pomSt).forEach {
                        dependenciesNode.addDependencies(it.value)
                        println("buildSrc implementation to pom:" + it.value)
                    }
                }
            }
        }
        repositories {
            if (com.buildsrc.easelint.Publish.SNAPSHOT) {
                com.buildsrc.easelint.Publish.Maven.aliyunSnapshotRepositories(this)
            } else {
                com.buildsrc.easelint.Publish.Maven.aliyunReleaseRepositories(this)
            }
        }
    }

}