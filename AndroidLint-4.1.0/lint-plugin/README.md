> > com.easelint:27.1.1-lint-gradle:0.0.1

# 模块

* 1.替换lint gradle，在 EaseLintCreationAction 的super configure 之前 hook 覆盖
* 2.在 LintTaskHelper apply 时 加载 lint wrapper
* 3.读取checkList,设置给 com.android.tools.lint.gradle.ScanTargetContainer EaseLintReflectiveLintRunner
  内的targetList 变量名要 校对

* 外部制定要扫描的文件列表
* 获取报告
* 动态获取lint rules

# 编译问题

* 如果碰到 引入  `kotlin-dsl` 或者 `maven-publish` 等插件后 无法访问 implementation 或 publishing api，

选择 Invalidate Caches, Sync 一下 即可
