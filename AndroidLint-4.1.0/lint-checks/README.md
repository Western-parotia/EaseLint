# 编译问题

切到kts 后碰到as 编译bug，提示无法加载 lint jar（实际是lintWrapper 无法生成 lint jar）
所以还是使用gradle 进行配置

# 规则开发后刷新检测器

* 1.build 一下 lintChecker
* 2.build 一下 lintWrapper
* 3.重新打开代码文件