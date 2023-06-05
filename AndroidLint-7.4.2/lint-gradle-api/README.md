# 7.x 适配

[gradle guide](https://docs.gradle.org/current/userguide/kotlin_dsl.html)

[6.x 迁移至 7.0 gradle guide](https://docs.gradle.org/current/userguide/upgrading_version_6.html)

# lint-gradle:

根据文档介绍，lint gradle 版本 = 23+gradle 版本

目前选择7.4.2 那么 lint gradle 版本可以选
30+,从[lint gradle maven list](https://mvnrepository.com/artifact/com.android.tools.lint/lint-gradle?repo=google)
挑出最后一个30的版本为 30.4.2 作为 Lint Gradle 的hook版本

# 编译顺序

* buildScr
* setting.gradle.kts
* build.gradle.kts
* module:build.gradle.kts

# buildSrc 导入gradle 将会导致如下错误

```

[//]: # (//这里导入 gradle 将导致与根目录的 plugin 导入冲突：)
implementation("com.android.tools.build:gradle:7.4.2")

[//]: # (The request for this plugin could not be satisfied because the plugin )
[//]: # (is already on the classpath with an unknown version, so compatibility cannot be checked)
```

