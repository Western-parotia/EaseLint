# 介绍[中文](xxx) [英文]()


# hook内容
* 1.替换lint gradle，在 EaseLintCreationAction 的super configure 之前 hook 覆盖原始
* 2.在 LintTaskHelper apply 时 加载 lint checks
* 3.读取 checkList,设置给 com.android.tools.lint.gradle.ScanTargetContainer EaseLintReflectiveLintRunner
* 4.配置lint 扫描的 扫描文件集合，白名单集合，checkOnly,disableIssue
# gradlew命令及参数配置
```
 ./gradlew easeLint -PtargetFiles="filePath1,filePath2" -PdisableIssues="issue1,issue2" -PcheckOnlyIssues="issue1,issue2" -PfileWhiteList="src/main/java/com/example1,src/main/java/com/example2" -PsuffixWhiteList="md,xml" -PcompareBranch="master" -PcompareCommitId="id12345"
```