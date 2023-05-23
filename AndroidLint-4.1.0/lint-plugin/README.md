# EaseLint

# 介绍[中文](xxx) [英文]()

# 功能

* 自由指定扫描文件目标，哪些文件需要被扫描，完全由开发者决定，具体到单个文件，而不是一个范围。
* 预留代码插槽拓展支持 Lint 规则动态下发，降级
* 预留代码插槽拓展支持动态设置 Lint Options

# 版本兼容

* AGP 4.x ✅
* AGP 7.x 准备中...

# 它是如何工作的

* 1.修改 Lint-gradle 库，借助预留给 IDE 使用的扫描目标设置逻辑，装载自定义的扫描目标

```kotlin
// LintGradleClient 文件中 hook 点1 

fun run(registry: IssueRegistry): Pair<List<Warning>, LintBaseline?> {
    //先判断是否有待检查的文件，如果没有直接结束task
    if (!ScanTargetContainer.hasTarget()) {
        "There are no target files to scan".log("LintGradleClient")
        return Pair(emptyList(), null)
    }
    val exitCode = run(registry, ScanTargetContainer.checkFileList)
}

// LintGradleClient 文件中 hook 点2

override fun configureLintRequest(lintRequest: LintRequest) {
    // 这里setProjects(null),那么在 LintDriver 中
    // 就可以走 request.getProjects() ?: computeProjects(request.files)的逻辑
    lintRequest.setProjects(null)
}
```

# hook内容

* 1.替换lint gradle，在 EaseLintCreationAction 的super configure 之前 hook 覆盖原始
* 2.在 LintTaskHelper apply 时 加载 lint checks
* 3.读取 checkList,设置给 com.android.tools.lint.gradle.ScanTargetContainer EaseLintReflectiveLintRunner
* 4.配置lint 扫描的 扫描文件集合，白名单集合，checkOnly,disableIssue