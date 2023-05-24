import java.util.*

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("ease.lint")
}
fun String.log() {
    println("temp_kts:$this")
}

val targets = arrayListOf(
//    "JavaLog.java",
    "JavaPrint.java",
//    "KotlinLog.kt",
    "KotlinPrint.kt"
//    "LintParcelableKt.kt",
//    "LintSerializableKt.kt"

)
val checkOnlyIssues = LinkedList<String>().apply {
//    add("SerializationDetector")
//    add("ParcelableDetector")
    add("LogDetector")
//    add("ViewIdDetector")
//    add("RelativeLayoutDetector")
//    add("ParseStringDetector")
//    add("ParseColorDetector")
}
easeLintExt {
    val dir = project.projectDir
    val parent = File(dir, "src/main/java/com/practice/temp")
    val files = LinkedList<String>()
    val ignores = LinkedList<String>()
    parent.listFiles()!!.forEach { file ->
        targets.forEach {
            if (file.absolutePath.endsWith(it)) {
                files.add(file.absolutePath)
            }
        }
    }

    targetFiles = files
    fileWhiteList = ignores
//    checkOnlyConfig = checkOnlyIssues
//    issueDisableList =
}


android {
    compileSdk = 31

    defaultConfig {
        applicationId = "com.practice.temp"
        minSdk = 21
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("normalSign") {
            storeFile = file("test.jks")
            storePassword = "android"
            keyAlias = "android"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("normalSign")
//            isMinifyEnabled = true
            multiDexEnabled = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lintOptions {
        isAbortOnError = false
        isShowAll = true
    }
}

dependencies {
// 单独测试，需要先取消引入 ease.lint 插件
//    implementation("com.easelint.snapshot:27.1.0-lint-checks:0.0.1-2023-05-24-10-18-01")
    // core-ktx:1.7.0引入了 kotlin_stdlib 1.5.31，lint 规则打包是 基于 1.4.31 的，会检测不出来
//    implementation("androidx.core:core-ktx:1.7.0")
    implementation(com.buildsrc.easelint.kts.Dependencies.Kotlin.kotlin_stdlib)

    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
//    compileOnly("com.android.tools.lint:lint-checks:27.1.1")
}