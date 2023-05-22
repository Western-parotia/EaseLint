123

# 写在最前面

## 如果你要编写lint，建议熟悉一下内置的工具类：LintUtils

### 1.lint 源码中内置几百个 Detector，可以做案例参考：

```kotlin
com.android.tools.lint.checks.BuiltinIssueRegistry
```

### 2.android library lint 检查到Error 会终止Lint，App module 不会

### 3.工程根目录的lint.xml 中可按id 设置不同的lint 配置参数，比如修改error 级别

### 4.配置文件lint.xml需要放在module的根目录下，而不是工程根目录

> doc 原文：如果您是手动创建此文件，请将其放置在 Android 项目的根目录下。

### lint工程结构：

* step.1 建立 lintChecker Module (Java or Kotlin Library)

* step.2 建立 lintWrapper module

> 必须使用 lintWrapper module（Android Library 来做中间层引入
> 可以使用静态 lintModule(Java or Kotlin Library),或者 jar

```kotlin
     lintPublish fileTree(include: ['*.jar'], dir: 'libs')
 
 //    lintPublish project(':lintChecker')
 ```

* step.3 app module 依赖 lintWrapper

> 依赖lintWrapper的aar产物 or maven 导入都可以

### lint 支持全局配置，所有项目生效(不推荐)

Lint主要内容介绍

* Lint关注的问题
    * Correctness 正确性：比如硬编码、使用过时 API 等
    * Security 安全性：比如在 WebView 中允许使用 JavaScriptInterface 等
    * Performance 性能：有影响的编码，比如：静态引用，循环引用等
    * Usability 可用性：有更好的替换的 比如排版、图标格式建议.png格式 等
    * Accessibility 可访问性：比如ImageView的contentDescription往往建议在属性中定义 等
    * Internationalization 国际化：直接使用汉字，没有使用资源引用等

* Lint问题的等级
    * Fatal：致命的，该类型错误， 该类型的错误会直接中断 ADT 导出 APK
    * Error：错误，明确需要解决的问题，包括Crash、明确的Bug、严重性能问题、不符合代码规范等，必须修复。
    * Warning：警告， 警告，包括代码编写建议、可能存在的Bug、一些性能优化等，可能是一个潜在的问题
    * Informational：可能没有问题，但是检查发现关于代码有一些说法
    * ignore：用户不希望看到此问题

* Lint 处理优先级(更多说明查看类Detector）
*
    1. Manifest file
*
    2. Resource files, in alphabetical order by resource type，
       (therefore, "layout" is checked before "values", "values-de" is checked before values-en" but
       after "values", and so on.
*
    3. Java sources
*
    4. Java classes
*
    5. Gradle files
*
    6. Generic files
*
    7. Proguard files
*
    8. Property files

### 可以配置给系统，全工程生效

将lintChecker.jar 直接放到{.android/lint/}中，如果没有则新建目录

 ```kotlin
mv lintChecker . jar ~ / . android / lint /
```