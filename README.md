# EaseLint

EaseLint是对Android Lint的二次开发，通过Hook AGP 源码来实现自定义扫描功能。

也可以作为一个学习kts构建，AGP开发，熟悉AGP源码的练习项目。


> 从实践来看，每个团队的CI技术服务技术栈都存在较大区别，标准化服务与适应性不可兼得，EaseLint也是一样，
> 你需要熟悉它的每个环节，以适合当下环境的方式引入你的团队。

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

# 使用

# 命令方式：推荐

