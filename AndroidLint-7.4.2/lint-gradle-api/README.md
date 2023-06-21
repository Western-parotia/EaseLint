# 7.x 适配

[gradle guide](https://docs.gradle.org/current/userguide/kotlin_dsl.html)

[6.x 迁移至 7.0 gradle guide](https://docs.gradle.org/current/userguide/upgrading_version_6.html)

# lint-gradle:

根据文档介绍，lint gradle 版本 = 23+gradle 版本

目前选择7.4.2 那么 lint gradle 版本可以选
30+,从[lint gradle maven list](https://mvnrepository.com/artifact/com.android.tools.lint/lint-gradle?repo=google)
挑出最后一个30的版本为 30.4.2 作为 Lint Gradle 的hook版本

# task 顺序

./gradlew lint --dry-run

:app:preBuild SKIPPED
:app:preDebugBuild SKIPPED
:app:compileDebugAidl SKIPPED
:app:compileDebugRenderscript SKIPPED
:app:generateDebugBuildConfig SKIPPED
:app:checkDebugAarMetadata SKIPPED
:app:generateDebugResValues SKIPPED
:app:mapDebugSourceSetPaths SKIPPED
:app:generateDebugResources SKIPPED
:app:mergeDebugResources SKIPPED
:app:packageDebugResources SKIPPED
:app:parseDebugLocalResources SKIPPED
:app:createDebugCompatibleScreenManifests SKIPPED
:app:extractDeepLinksDebug SKIPPED
:app:processDebugMainManifest SKIPPED
:app:processDebugManifest SKIPPED
:app:processDebugManifestForPackage SKIPPED
:app:processDebugResources SKIPPED
:app:compileDebugKotlin SKIPPED
:app:javaPreCompileDebug SKIPPED
:app:compileDebugJavaWithJavac SKIPPED
:app:bundleDebugClassesToCompileJar SKIPPED
:app:preDebugAndroidTestBuild SKIPPED
:app:processDebugAndroidTestManifest SKIPPED
:app:compileDebugAndroidTestRenderscript SKIPPED
:app:extractProguardFiles SKIPPED
:app:generateDebugAndroidTestResValues SKIPPED
----------------------------------
:app:lintAnalyzeDebug SKIPPED
:app:lintReportDebug SKIPPED
:app:lintDebug SKIPPED
:app:lint SKIPPED
lint-results-debug.html
lint-results-debug.txt
lint-results-debug.xml