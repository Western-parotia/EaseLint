import java.util.*

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("ease.lint")
}
fun String.log() {
    println("temp_kts:$this")
}

val targets = arrayListOf(
    "SubModuleKotlinPrint.kt",
    "JavaParse.java",
    "KotlinParse.kt"
)
easeLintExt {
    val dir = project.projectDir
    val parent = File(dir, "src/main/java/com/practice/temp")
    val files = LinkedList<String>()
    val ignores = LinkedList<String>()
    parent.listFiles()!!.forEach { file ->
        targets.forEach { name ->
            if (file.absolutePath.endsWith(name)) {
                files.add(file.absolutePath)
            }
        }
    }
    files.add("/Volumes/D/CodeProject/AndroidProject/EaseLint/AndroidLint-4.1.0/lint-plugin/temp/src/main/java/com/practice/temp/KotlinPrint.kt")
    targetFiles = files
    fileWhiteList = ignores
    checkOnlyIssues = LinkedList<String>().apply {
        //    add("SerializationDetector")
//    add("ParcelableDetector")
        add("LogDetector")
//    add("ViewIdDetector")
//    add("RelativeLayoutDetector")
        add("ParseStringDetector")
//    add("ParseColorDetector")
    }
    disableIssues = LinkedList<String>().apply {
//        add("LogDetector")
    }
    setGitDiffConfig("main","")
}


android {
    compileSdk = 31

    defaultConfig {
        minSdk = 21
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
//            isMinifyEnabled = true
            multiDexEnabled = true
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
        isCheckDependencies = true
    }
}

dependencies {
    implementation(com.buildsrc.easelint.kts.Dependencies.Kotlin.kotlin_stdlib)

}