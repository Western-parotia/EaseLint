plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
}


android {

    compileSdkVersion(30)

    defaultConfig {
        applicationId("com.zsw.devopshsyq")
        minSdkVersion(21)
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_1_8)
        targetCompatibility(JavaVersion.VERSION_1_8)
    }
    kotlinOptions {
        jvmTarget = ("1.8")
    }
    buildFeatures {
        viewBinding = true
    }
    val lintIds = arrayOf(
//        "SerializationDetector",
//        "ParseColorDetector",
        "LogDetector"

    )
    lintOptions {
//        disable("GradleDependency")
        checkOnly(*lintIds)
//        lintConfig = file("lint.xml")
        isAbortOnError = false
        isCheckDependencies = false
        textReport = false//输出检测日志
        xmlReport = true
//        baselineFile = file("base_line.xml")
    }
}
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${com.buildsrc.kts.Dependencies.Kotlin.kotlin_version}")
//    implementation(project(":lintWrapper"))
    implementation("com.easelint.snapshot:27.1.0-lint-checks:0.0.1-2023-05-24-10-12-14")
    implementation("com.google.code.gson:gson:2.9.0")

}
