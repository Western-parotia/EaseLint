# easeLint 7.x版本 使用文档

命令行：

```
 ./gradlew easeLint -PtargetFiles="filePath1,filePath2" -PdisableIssues="LogDetector" -PcheckOnlyIssues="LogDetector,ParseStringDetector" -PfileWhiteList="src/main/java/com/example1,src/main/java/com/example2" -PsuffixWhiteList="md,xml" -PcompareBranch="master" -PcompareCommitId="id12345"
 ./gradlew easeLint -PtargetFiles="filePath1,filePath2" -PdisableIssues="LogDetector" -PcheckOnlyIssues="LogDetector,ParseStringDetector" -PfileWhiteList="src/main/java/com/example1,src/main/java/com/example2" -PsuffixWhiteList="md,xml" -PcompareBranch="main" -PcompareCommitId="id12345"
```