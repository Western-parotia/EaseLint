plugins {
    `kotlin-dsl`
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
}
repositories {
    mavenCentral()
    google()
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
dependencies {
    implementation("com.android.tools.build:gradle:4.1.0")
}