# 0.0.2

直接借用 IDE 的增量files 成员 完成 靶向扫描

# 首次编译容易碰到的多数异常，都可以按如下方式解决

遵循工程编译流程，进行逐个递进编译，先关注释全部gradle，从buildSrc 开始放开，每放开一个重新编译一次

buildSrc>root setting.gradle>root build.gradle>module.gradle

