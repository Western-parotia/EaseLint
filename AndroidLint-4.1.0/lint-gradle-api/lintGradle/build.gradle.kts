import com.buildsrc.kts.Dependencies
import com.buildsrc.kts.Publish
import org.gradle.jvm.tasks.Jar

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
    compileOnly(gradleApi())
}

tasks.register("sourcesJar", Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("lintGradle") {
            Publish.Maven.setGAV(this)
            from(components["java"])
            artifact(tasks["sourcesJar"])
        }
    }

    repositories {
        if (Publish.SNAPSHOT) {
            Publish.Maven.aliyunSnapshotRepositories(this)
        } else {
            Publish.Maven.aliyunReleaseRepositories(this)
        }
    }
}
