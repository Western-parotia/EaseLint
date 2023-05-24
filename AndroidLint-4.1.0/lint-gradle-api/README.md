# 0.0.2

直接借用 IDE 的增量files 成员 完成 靶向扫描

# 首次编译容易碰到的多数异常，都可以按如下方式解决

遵循工程编译流程，进行逐个递进编译，先关注释全部gradle，从buildSrc 开始放开，每放开一个重新编译一次

buildSrc>root setting.gradle>root build.gradle>module.gradle

lintVersion = gradlePluginVersion + 23.0.0

例如，7 + 23 = 30，所以 AGP 版本7.something对应于 Lint 版本30.something。
再举一个例子；在撰写本文时，AGP 的当前稳定版本是 4.1.2，因此对应的 Lint API 版本是 27.1.2。

