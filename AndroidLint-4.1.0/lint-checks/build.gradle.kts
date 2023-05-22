// Top-level build file where you can add configuration options common to all sub-projects/modules.

//buildSrc的初始化init
com.buildsrc.easelint.GlobalConfig.init(project)

buildscript {

    repositories {
        com.buildsrc.easelint.Repositories.defRepositories(this)
    }
    dependencies {
        classpath(com.buildsrc.easelint.Dependencies.Gradle.agp)
        classpath(com.buildsrc.easelint.Dependencies.Gradle.kgp)
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
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