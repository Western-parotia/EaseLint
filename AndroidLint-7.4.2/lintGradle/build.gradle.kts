import com.easelint.buildsrc.QuickDependencies

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
    compileOnly("com.android.tools.lint:lint-gradle:${QuickDependencies.Lint.lint_version}")
    compileOnly(gradleApi())
}

tasks.register("sourcesJar", Jar::class.java) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
//    from(sourceSets.main.get().allSource)
}
