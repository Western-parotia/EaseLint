// Top-level build file where you can add configuration options common to all sub-projects/modules.
import com.buildsrc.kts.GlobalConfig

//buildSrc的初始化init
GlobalConfig.init(project)

buildscript {

    repositories {
        com.buildsrc.kts.Repositories.defRepositories(this)
    }
    dependencies {
        classpath(group = "com.android.tools.build", name = "gradle", version = "4.1.0")
        classpath(
            group = "org.jetbrains.kotlin",
            name = "kotlin-gradle-plugin",
            version = "1.4.31"
        )

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
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