> > com.easelint:27.1.1-lint-gradle:0.0.1

# 扫描不出 Kotlin JDK API 的问题

lint 发布基于 org.jetbrains.kotlin:kotlin-stdlib:1.4.31

如果被检测的项目高于这个版本，有可能无法检测出来（实际依赖版本需要使用 gradle app:dependencies 确认）
已验证检测不出的版本：1.5.31,1.6.21

* 如何解决？

尽量提高lint-checks 库依赖的kotlin-stdlib版本 。


1.8.20
# 模块

* 1.替换lint gradle，在 EaseLintCreationAction 的super configure 之前 hook 覆盖
* 2.在 LintTaskHelper apply 时 加载 lint wrapper
* 3.读取checkList,设置给 com.android.tools.lint.gradle.ScanTargetContainer EaseLintReflectiveLintRunner
* 4.配置lint 扫描的 扫描文件集合，白名单集合，checkOnly,disableIssue

# 报告输出目录

const val XML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.xml"
const val HTML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.html"

# 编译问题

* 如果碰到 引入  `kotlin-dsl` 或者 `maven-publish` 等插件后 无法访问 implementation 或 publishing api，

选择 Invalidate Caches, Sync 一下 即可
