package com.buildsrc.lint

import com.android.tools.lint.EaseLintMain
import org.gradle.api.Plugin
import org.gradle.api.Project

class EaseLintPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val args = Array<String>(1){""}

        project.afterEvaluate {

            tasks.register("easeLint").get().doLast {
                EaseLintMain().run(args)
            }
        }
    }
}
