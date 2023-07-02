# EaseLint

从多个实践反馈来看，每个团队的CI 或 DevOps 技术服务技术栈都存在较大区别，适配通常需要内部开发者根据需要来定制开发，
所以 EaseLint 并不会提供傻瓜式的集成式服务。

EaseLint 将尽量保持较为独立功能结构，并以此为基础追求丝滑的集成实践示范。

Tips:
关于首次编译的"请在local.properties添加私有仓库的用户名（repositoryUserName）和密码（repositoryPassword）"
这是用于发布的私有密钥，如果无需发布注释掉 整个 publishing 代码即可

## AGP 4.x ✅

[lintPlugin](AndroidLint-4.1.0/lint-plugin)
[Lint-gradle](AndroidLint-4.1.0/lint-gradle-api)
[Lint-checks](AndroidLint-4.1.0/lint-checks)

| 功能名称 | 完成状态 | 备注 |
|------|--|--------|
| 自定义扫描文件目标 | ✅ | 无反射 |
| 动态修改LintOptions | ✅ | 无反射 |
| 基于Git diff 抓取目标文件 | ✅ | 支持基于分支与commitId |
| 动态导入 lint-checks | ✅ | |
| 结果解析 | ✅ | |

## AGP 7.x ✅

[lintPlugin](AndroidLint-7.4.2/lint-plugin)
[lint-api module](AndroidLint-7.4.2/lint-plugin/lint-api)

| 功能名称 | 完成状态 | 备注 |
|------|--|--------|
| 自定义扫描文件目标 | ✅ | 无反射 |
| 动态修改LintOptions | ✅ | 无反射 |
| 基于Git diff 抓取目标文件 | ✅ | 支持基于分支与commitId |
| 动态导入 lint-checks | ✅ | |
| 结果解析 | - | |
