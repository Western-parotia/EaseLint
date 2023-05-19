# 首次变异各种报错，找不到插件，仓库怎么办？

* 1.按项目编译流程逐个编译，先关闭所有编译配置，从buildSrc 开始，编译好一个放开一个。
    * buildSrc
    * settings.gradle.kts
    * root build.gradle.kts
    * deep level module build.gradle.kts
    * top level module build.gradle.kts
  