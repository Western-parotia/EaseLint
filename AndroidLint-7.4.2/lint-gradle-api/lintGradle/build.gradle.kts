import com.buildsrc.kts.Dependencies

plugins {
    id("java-library")
    id("kotlin")
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(Dependencies.Kotlin.kotlin_stdlib)
    compileOnly(Dependencies.Lint.lint_gradle)
    compileOnly(Dependencies.Gradle.agp)
    compileOnly(gradleApi())
}

//
//publishing {
//    publications {
//        create<MavenPublication>("lintGradle") {
//            Publish.Maven.setGAV(this)
//            from(components["java"])
//            artifact(tasks["sourcesJar"])
//        }
//    }
//
//    repositories {
//        if (Publish.SNAPSHOT) {
//            Publish.Maven.aliyunSnapshotRepositories(this)
//        } else {
//            Publish.Maven.aliyunReleaseRepositories(this)
//        }
//    }
//}
