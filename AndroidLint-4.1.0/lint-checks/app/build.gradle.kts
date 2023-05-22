import com.buildsrc.kts.Dependencies

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
        "LogDetector",
        "SerializationDetector",
        "ParseColorDetector"
    )
    lintOptions {
//        disable("GradleDependency")
//        checkOnly(*lintIds)
//        lintConfig = file("lint.xml")
        isAbortOnError = properties["lint.isAbortOnError"] ?: "false" == "true"
        isCheckDependencies = false
        textReport = false//输出检测日志
        xmlReport = true
        htmlOutput = file("lint-result.html")
        xmlOutput = file("lint-result.xml")
//        baselineFile = file("base_line.xml")
    }
}
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Dependencies.Kotlin.kotlin_version}")
    implementation(project(":lintWrapper"))
    implementation("com.google.code.gson:gson:2.9.0")

}
