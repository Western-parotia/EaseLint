# 扫描不出 Kotlin JDK API 的问题

lint 发布基于 org.jetbrains.kotlin:kotlin-stdlib:1.4.31

经测试 只有 基于 1.4.31 打包的lint规则 可以检测 1.4.31的 项目

其他版本就算lint 打包依赖的版本高与项目版本也不行。相同也不行，1.5.31,1.6.21 都测试不行。

* 如何解决？
  使用1.4.31 版本

* 使用7.0 AGP 是否可避免， 待验证

# hook内容

* 1.替换lint gradle，在 EaseLintCreationAction 的super configure 之前 hook 覆盖原始
* 2.在 LintTaskHelper apply 时 加载 lint checks
* 3.读取 checkList,设置给 com.android.tools.lint.gradle.ScanTargetContainer EaseLintReflectiveLintRunner
* 4.配置lint 扫描的 扫描文件集合，白名单集合，checkOnly,disableIssue

# 首次打开项目可能碰到 KTS 编译报错问题

按照 android studio 脚本生命周期进行逐层编译（如果部分插件找不到，注释掉插件引用，先保证kts脚本被加载）