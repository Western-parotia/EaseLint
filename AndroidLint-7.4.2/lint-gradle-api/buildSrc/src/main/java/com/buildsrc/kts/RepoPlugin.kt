package com.buildsrc.kts

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.buildscript
import org.gradle.kotlin.dsl.repositories

abstract class RepoPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        println("RepoPlugin--------------------------111")
        project.beforeEvaluate {
            project.repositories {
                Repositories.defRepositories(this)
            }
            project.buildscript {
                this.repositories {
                    Repositories.defRepositories(this)
                }
            }
        }

    }
}