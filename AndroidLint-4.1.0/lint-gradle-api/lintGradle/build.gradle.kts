import com.buildsrc.kts.Dependencies
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
            groupId = "com.mj.lint"
            artifactId = "lint-gradle"
            version = "0.0.1"
            from(components["java"])
            artifact(tasks["sourcesJar"])
        }
    }

    repositories {
        maven {
            url = uri("https://mijukeji-maven.pkg.coding.net/repository/jileiku/base_maven/")
            credentials {
                username = "base_maven-1639657395993"
                password = "631a6828fffb502f53b0c51db9072ebf76418ac3"
            }
        }
    }
}
