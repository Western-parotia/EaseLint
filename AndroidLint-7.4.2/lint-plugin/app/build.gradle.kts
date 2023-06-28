plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    //一定要晚于 com.android.application 插件
    id("ease.lint")
}

android {

    namespace = "com.easelint.gradle"
//    compileSdk = 33
//    compileSdkVersion = "android-33"
    defaultConfig {
        applicationId = "com.easelint.gradle"
        minSdk = 26
        targetSdk = 33
        compileSdkVersion = "android-33"

    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf("proguard-android-optimize.txt", "proguard-rules.pro"))
        }
    }
    compileOptions {
        sourceCompatibility(11)
        targetCompatibility(11)
    }
    lint {
        abortOnError = false
        xmlReport = true
        htmlReport = true
        textReport = false
//        disable.add("LogDetector")
//        checkOnly.add("ParseStringDetector")
//        checkOnly.add("LogDetector")
    }

}

dependencies {
// debug 源码
//    compileOnly("com.android.tools.build:gradle:7.4.2") // 1
//     Main.java 在 lint包中
//    compileOnly("com.android.tools.lint:lint:30.4.2")
//    compileOnly("com.android.tools.lint:lint-model:30.4.2") //2
//    compileOnly("com.android.tools:common:30.4.2")
//    compileOnly("com.android.tools.lint:lint-checks:30.4.2")
//    compileOnly("com.android.tools.lint:lint-gradle:30.4.2")

    implementation("com.easelint:27.1.0-lint-checks:0.0.1-2023-06-20-05-03-30")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.core:core-ktx:1.8.0")
    //查看源码
    compileOnly("com.easelint.snapshot:30.4.2-lint-api:0.0.1-2023-06-28-06-30-10")

}


//com.android.tools.lint:lint-checks:30.4.2
//+--- com.android.tools.lint:lint-api:30.4.2
//|    +--- com.android.tools.lint:lint-model:30.4.2
//|    +--- com.android.tools.external.com-intellij:intellij-core:30.4.2
//|    +--- com.android.tools.external.com-intellij:kotlin-compiler:30.4.2
//|    +--- com.android.tools.external.org-jetbrains:uast:30.4.2
//|    +--- com.android.tools.build:manifest-merger:30.4.2
//|    +--- com.android.tools:common:30.4.2
//|    |    +--- com.android.tools:annotations:30.4.2
//|    |    \--- com.google.guava:guava:30.1-jre
//|    |         +--- com.google.guava:failureaccess:1.0.1
//|    |         +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
//|    |         +--- com.google.code.findbugs:jsr305:3.0.2
//|    |         +--- org.checkerframework:checker-qual:3.5.0
//|    |         +--- com.google.errorprone:error_prone_annotations:2.3.4
//|    |         \--- com.google.j2objc:j2objc-annotations:1.3
//|    +--- com.android.tools.layoutlib:layoutlib-api:30.4.2
//|    +--- com.android.tools:sdk-common:30.4.2
//|    +--- com.android.tools:sdklib:30.4.2
//|    |    \--- com.android.tools:repository:30.4.2
//|    +--- commons-io:commons-io:2.4
//|    +--- net.sf.kxml:kxml2:2.3.0
//|    +--- org.jetbrains.kotlin:kotlin-reflect:1.7.10
//|    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10
//|    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10
//|    |         \--- org.jetbrains:annotations:13.0
//|    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.10
//|    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 (*)
//|    |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.10
//|    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 (*)
//|    +--- org.ow2.asm:asm:9.2
//|    \--- org.ow2.asm:asm-tree:9.2
//|         \--- org.ow2.asm:asm:9.2
//\--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.10 (*


