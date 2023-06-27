plugins {
    id("java-library")
    id("kotlin")
    id("maven-publish")
}
dependencies {
    compileOnly("com.android.tools.lint:lint-api:30.4.2") {
        exclude("com.android.tools.build", "manifest-merger")
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
tasks.register("sourcesJar", org.gradle.jvm.tasks.Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("lintGradle") {
            com.buildsrc.kts.Publish.Maven.setGAV(this)
            from(components["java"])
            artifact(tasks["sourcesJar"])
        }
    }

    repositories {
        if (com.buildsrc.kts.Publish.SNAPSHOT) {
            com.buildsrc.kts.Publish.Maven.aliyunSnapshotRepositories(this)
        } else {
            com.buildsrc.kts.Publish.Maven.aliyunReleaseRepositories(this)
        }
    }
}