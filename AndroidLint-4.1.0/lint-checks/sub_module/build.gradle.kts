plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {

    compileSdkVersion(30)

    defaultConfig {
        minSdkVersion(21)
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_1_8)
        targetCompatibility(JavaVersion.VERSION_1_8)
    }
    kotlinOptions {
        jvmTarget = ("1.8")
    }
    lintOptions {
        disable("GradleDependency")
        checkOnly("LogDetector")
        lintConfig = file("lint.xml")
        isAbortOnError = properties["lint.isAbortOnError"] ?: "false" == "true"
        isCheckDependencies = false
        textReport = true//输出检测日志
        xmlReport = true
        htmlOutput = file("lint-result.html")
        xmlOutput = file("lint-result.xml")
        baselineFile = file("base_line.xml")
    }
}
dependencies {
    implementation ("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
    implementation (project(":lintWrapper"))
}



