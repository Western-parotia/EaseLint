plugins {
    id("com.android.application")
    id("kotlin-android")
//    id("ease.lint")
}
fun String.log() {
    println("temp_kts:$this")
}

val targets = arrayListOf(
//    "JavaLog.java",
//    "JavaPrint.java",
//    "KotlinLog.kt",
    "KotlinPrint.kt"
)
//easeLintExt {
//    val dir = project.projectDir
//    val parent = File(dir, "src/main/java/com/practice/temp")
//    val files = LinkedList<String>()
//    val ignores = LinkedList<String>()
//    parent.listFiles()!!.forEach { file ->
//        targets.forEach { name ->
//            if (file.absolutePath.endsWith(name)) {
//                files.add(file.absolutePath)
//            }
//        }
////        if (it.endsWith("LintTestWhiteFile.kt")) {
////            ignores.add(it.absolutePath)
////        } else {
////            files.add(it.absolutePath)
////        }
//    }
//    targetFiles = files
//    fileWhiteList = ignores
//
//}


android {
    compileSdk = 31

    defaultConfig {
        applicationId = "com.practice.temp"
        minSdk = 21
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }
    kotlinOptions {
        jvmTarget = ("1.8")
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
//    implementation("com.easelint.snapshot:lint-checks:0.0.1-2023-05-22-10-09-35")
    implementation("com.easelint.snapshot:lint-checks:0.0.1-2023-05-23-06-10-27")
    // core-ktx:1.7.0引入了 kotlin_stdlib 1.5.31，lint 规则打包是 基于 1.4.31 的，会检测不出来
//    implementation("androidx.core:core-ktx:1.7.0")
    implementation(com.buildsrc.easelint.Dependencies.Kotlin.kotlin_stdlib)
    //测试1.8.0
//    implementation(com.buildsrc.easelint.Dependencies.Kotlin.kotlin_stdlib)
    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
//    compileOnly("com.android.tools.lint:lint-checks:27.1.1")
}