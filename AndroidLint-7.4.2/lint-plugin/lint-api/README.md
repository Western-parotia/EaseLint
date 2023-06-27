# hook LintRequest

```kotlin
 fun setProjects(projects: Collection<Project>?): LintRequest {
    // add file
    val project = projects?.first()
    project?.addFile(file)
    this.projects = projects
    return this
}
```
