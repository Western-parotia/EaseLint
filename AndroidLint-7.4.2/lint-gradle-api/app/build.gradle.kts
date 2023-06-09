plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
project.afterEvaluate {
    project.tasks.findByName("lintDebug")!!.doFirst {
        println("log______ lintDebug")
    }
}


android {
    namespace = "com.easelint.gradle"
    compileSdk = 33
    defaultConfig {
        applicationId = "com.easelint.gradle"
        minSdk = 24
        targetSdk = 33
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.google.android.material:material:1.5.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}