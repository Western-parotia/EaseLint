plugins {
    id("com.android.library")
    id("kotlin-android")
    id("maven-publish")
}
android {
    compileSdk = 30

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}
dependencies {
    lintPublish(project(":lintChecker"))
}
// afterEvaluate 才可以读取 components["release"]
afterEvaluate {
//    publishing {
//        publications {
//            create<MavenPublication>("lintWrapper") {
//                Publish.Maven.setGAV(this)
//                from(components["release"])
//            }
//        }
//
//        repositories {
//            if (Publish.SNAPSHOT) {
//                Publish.Maven.aliyunSnapshotRepositories(this)
//            } else {
//                Publish.Maven.aliyunReleaseRepositories(this)
//            }
//        }
//    }
}



