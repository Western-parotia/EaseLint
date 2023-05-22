import com.buildsrc.kts.Publish

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("maven-publish")
}

android {
    compileSdk = 30

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    lintPublish(project(":lintChecker"))
}


afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("lintGradle") {
                from(components["release"])
                Publish.Maven.setGAV(this)
            }
        }
        repositories {
            if (Publish.SNAPSHOT) {
                Publish.Maven.aliyunSnapshotRepositories(this)
            } else {
                Publish.Maven.aliyunReleaseRepositories(this)
            }
        }
    }
}


