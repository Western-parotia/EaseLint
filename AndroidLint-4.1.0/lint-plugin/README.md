> > com.easelint:27.1.1-lint-gradle:0.0.1

# 扫描不出 Kotlin JDK API 的问题

lint 发布基于 org.jetbrains.kotlin:kotlin-stdlib:1.4.31

经测试 只有 基于 1.4.31 打包的lint规则 可以检测 1.4.31的 项目

其他版本就算lint 打包依赖的版本高与项目版本也不行。相同也不行，1.5.31,1.6.21 都测试不行。

* 如何解决？
  使用1.4.31 版本

或使用7.0 AGP ? 待验证

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
