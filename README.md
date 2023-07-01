# EaseLint

从多个实践反馈来看，每个团队的CI 或 DevOps 技术服务技术栈都存在较大区别，适配通常需要内部开发者根据需要来定制开发，
所以 EaseLint 并不会提供傻瓜式的集成式服务。

EaseLint 将尽量保持较为独立功能结构，并以此为基础追求丝滑的集成实践示范。

Tips:
关于首次编译的"请在local.properties添加私有仓库的用户名（repositoryUserName）和密码（repositoryPassword）"
这是用于发布的私有密钥，如果无需发布注释掉 整个 publishing 代码即可

## AGP 4.x ✅

[lintPlugin](AndroidLint-4.1.0/lint-plugin),
[Lint-gradle](AndroidLint-4.1.0/lint-gradle-api)
,[Lint-checks](AndroidLint-4.1.0/lint-checks)

| 功能名称 | 完成状态 | 备注 |
|------|--|--------|
| 自定义扫描文件目标 | ✅ | 无反射 |
| 动态修改LintOptions | ✅ | 无反射 |
| 基于Git diff 抓取目标文件 | ✅ | 支持基于 分支与commitId |
| 动态导入 lint-checks | ✅ | |
| 结果解析 | ✅ | |

## AGP 7.x 进行中...

[lintPlugin](AndroidLint-7.4.2/lint-plugin)
[lint-api module](AndroidLint-7.4.2/lint-plugin/lint-api)

| 功能名称 | 完成状态 | 备注 |
|------|--|--------|
| 自定义扫描文件目标 | ✅ | 无反射 |
| 动态修改LintOptions | 对勾 | |
| 基于Git diff 抓取目标文件 | 对勾 | 支持基于 分支与commitId |
| 动态导入 lint-checks | - | |
| 结果解析 | - | |

# 使用建议

## 1.在CI上建议自定义 Task 动态导入 lint-checks ，并从远端拉取配置进行修改

* 精准指定扫描目标，比如某一次 git 新增或修改的代码
* 动态控制 lintOptions,在 a1EaseLint task 运行前都可以通过修改 LintSlot 的属性。

```kotlin
LintWrapperHelper.init(true, "0.0.1-2023-05-24-10-18-01")
```

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

## 2. 如果只是在本地使用，可以在module中使用 EaseLintExtension 完成单项目 easelint 配置

```kotlin
plugins {
  id("com.android.library")
  id("kotlin-android")
  id("ease.lint")
}

easeLintExt {
}
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

