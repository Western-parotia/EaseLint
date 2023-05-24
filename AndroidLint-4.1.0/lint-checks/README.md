# 编译问题

切到kts 后碰到as 编译bug，提示无法加载 lint jar（实际是lintWrapper 无法生成 lint jar）
所以还是使用gradle 进行配置

# 规则开发后刷新检测器

* 1.build 一下 lintChecker
* 2.build 一下 lintWrapper
* 3.重新打开代码文件

如何确认 lint 版本
> 知道 Gradle 插件版本号后，比如 4.2.0-beta06，您可以通过简单地将23 添加到 gradle 插件的主要版本来计算 lint 版本号，并保持一切不变：

lintVersion = gradlePluginVersion + 23.0.0

例如，7 + 23 = 30，所以 AGP 版本7.something对应于 Lint 版本30.something。再举一个例子；在撰写本文时，AGP 的当前稳定版本是 4.1.2，因此对应的
Lint API 版本是 27.1.2。