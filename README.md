# EaseLint

项目包含三模块来完成，分别是
[lintPlugin](AndroidLint-4.1.0/lint-plugin),
[Lint-gradle](AndroidLint-4.1.0/lint-gradle-api)
,[Lint-checks](AndroidLint-4.1.0/lint-checks)

# 版本兼容

* AGP 4.x ✅
* AGP 7.x 准备中...

# 特性

* 精准指定扫描目标，比如某一次 git 新增或修改的代码
* 动态控制 lintOptions,在lint task 运行前都可以通过修改 LintSlot 的属性进行修改。

目前已经支持的属性：

```java
object LintSlot{
        //扫描目标，统一为文件全路径
        var targetFiles:LinkedList<String> =LinkedList()

        //需要关闭的 issue 清单，部署到CI时用与快速降级，快速停用个别异常issue，优先级最高
        var disableIssues:LinkedList<String> =LinkedList()

        // 用于定向控制所有的 issue ,主要用于上线自己开发的 Issue
        var checkOnlyIssues:LinkedList<String> =LinkedList()

        //扫描文件白名单,有些文件始终都不需要被扫描
        var fileWhiteList:LinkedList<String> =LinkedList()

        }
```

# 使用

## 1.动态导入 lint-checks

```kotlin
LintWrapperHelper.init(true, "0.0.1-2023-05-24-10-18-01")
```

## 2.使用 EaseLintExtension或者gradlew参数完成单项目 easelint 配置

### EaseLintExtension配置

```kotlin
plugins {
  id("com.android.library")
  id("kotlin-android")
  id("ease.lint")
}

val targets = arrayListOf(
  "SubModuleKotlinPrint.kt",
  "JavaParse.java",
  "KotlinParse.kt"
)
easeLintExt {
  val dir = project.projectDir
  val parent = File(dir, "src/main/java/com/practice/temp")
  val files = LinkedList<String>()
  val ignores = LinkedList<String>()
  parent.listFiles()!!.forEach { file ->
    targets.forEach { name ->
      if (file.absolutePath.endsWith(name)) {
        files.add(file.absolutePath)
      }
    }
  }
  files.add("/Volumes/D/CodeProject/AndroidProject/EaseLint/AndroidLint-4.1.0/lint-plugin/temp/src/main/java/com/practice/temp/KotlinPrint.kt")
  //指定需要检查文件
  targetFiles = files
  //文件白名单（检查时需要忽略的文件）
  fileWhiteList = ignores
  //检查指定规则（只检查checkOnlyIssues中包含的规则，其余规则均不检查）
  checkOnlyIssues = LinkedList<String>().apply {
    add("LogDetector")
    add("ParseStringDetector")
  }
  //指定不检查的规则
  disableIssues = LinkedList<String>().apply {
    add("LogDetector")
  }
  //文件后缀白名单（检查时会忽略对应后缀的文件）
  suffixWhiteList = LinkedList<String>().apply {
    add("kt")
  }
  /*
  通过git diff命令获取需要检查的差异文件，
  其中compareBranch为需要对比的分支，
  compareCommitId为需要对比的提交记录。
  两个配置二选一，若只配配置compareBranch，则与该分支的最新提交记录比较
  若只配置compareCommitId，则与该提交记录比较。
  （若不配置GitDiffConfig参数，则只检查targetFiles中指定的文件，
  反之则检查targetFiles和获取到的差异文件）
  */
  setGitDiffConfig(compareBranch = "main", compareCommitId = "")
}
```

### gradlew配置

使用gradlew命令执行easeLint任务时可在命令中配置检查参数，所有参数与EaseLintExtension配置中的参数相同，
每一项参数值优先使用gradlew命令中配置的参数，若未配置则使用EaseLintExtension中配置的参数。

```
 ./gradlew easeLint -PtargetFiles="filePath1,filePath2" -PdisableIssues="LogDetector" -PcheckOnlyIssues="LogDetector,ParseStringDetector" -PfileWhiteList="src/main/java/com/example1,src/main/java/com/example2" -PsuffixWhiteList="md,xml" -PcompareBranch="master" -PcompareCommitId="id12345"
```

## 3.PrepareEaseLintTask

easeLint 任务执行前的钩子，用于获取动态配置，并设置参数
比如：

* 1.获取lint-gradle,lint-checks 库的目标版本号（不建议用last等默认下载最新版本的配置，
  这对排查问题会造成极大的阻力）

* 2.获取 issue 清单配置,动态上下线不同的 Issue

* 3.读取git 记录，挑选本次需要扫描的文件清单

在 easelint 运行前 都可以通过修改 [com.buildsrc.easelint.lint.helper.LintSlot]
来动态管理本次扫描的配置

## 4.TreatEaseLintResultTask

easeLint 任务执行结束的的钩子，用于处理 easeLint执行产物
比如：

* 1.上传 lint 报告 或发送邮件

* 2.解析 lint 报告，通知企业 IM

# Tips

基于Android Lint 本身的特特性，只需要有一个 module 引入了 easeLint 插件，就可以扫描任何可访问到的文件。

