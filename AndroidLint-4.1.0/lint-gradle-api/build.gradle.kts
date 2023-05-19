// Top-level build file where you can add configuration options common to all sub-projects/modules.
com.buildsrc.kts.GlobalConfig.init(project)
buildscript {

    repositories {
        com.buildsrc.kts.Repositories.defRepositories(this)
    }
    dependencies {
        //buildscript 偶尔编译错误 找不到包名，给全包名即可
        classpath(com.buildsrc.kts.Dependencies.Gradle.agp)
        classpath(com.buildsrc.kts.Dependencies.Gradle.kgp)
    }
}
allprojects {
    repositories {
        com.buildsrc.kts.Repositories.defRepositories(this)
    }
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.buildDir)
}