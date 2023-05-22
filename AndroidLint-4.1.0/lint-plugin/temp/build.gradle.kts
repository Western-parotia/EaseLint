import java.util.*

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("ease.lint")
}
fun String.log() {
    println("temp_kts:$this")
}
easeLintExt {
    val dir = project.projectDir
    val parent = File(dir, "src/main/java/com/practice/temp")
    val files = LinkedList<String>()
    val ignores = LinkedList<String>()
    parent.listFiles()!!.forEach {
        if (it.endsWith("LintTestWhiteFile.kt")) {
            ignores.add(it.absolutePath)
        } else {
            files.add(it.absolutePath)
        }
    }
    targetFiles = files
    fileWhiteList = ignores

}



android {
    compileSdk = 31

    defaultConfig {
        applicationId = "com.practice.temp"
        minSdk = 21
        targetSdk = 31
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

dependencies {

    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
//    compileOnly("com.android.tools.lint:lint-checks:27.1.1")
}