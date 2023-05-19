# 首次变异各种报错，找不到插件，仓库怎么办？

* 1.按项目编译流程逐个编译，先关闭所有编译配置，从buildSrc 开始，编译好一个放开一个。
    * buildSrc
    * settings.gradle.kts
    * root build.gradle.kts
    * deep level module build.gradle.kts
    * top level module build.gradle.kts

# 根 build.gradle 报错找不到 library 或 android 插件 版本

1. 在 buildSrc 中引入了下面的 gradle 版本

```
implementation("com.android.tools.build:gradle:7.4.2")
```

更换为

```kotlin
gradleApi()
```

2. 移除 根 build.gradle 中插件的版本配置

```kotlin
id("com.android.library") version "7.4.2" apply false
id("com.android.application") version "7.4.2" apply false
>>
id("com.android.library") apply false
id("com.android.application") apply false

```

在 lint 项目中 不关心 app module 所以这里移除 版本配置。
具体原因暂未查

# 如何确定 Lint-gradle 与 AGP的版本

挺难搜索的，好像没有这样的资源整理。当前项目配置 制定的AGP版本，然后运行 .gradlew lint,查看它 下载的 lint 版本，
这便是agp 对应的 lint 版本。估计源码内应该也是有的，达到目的就行。
