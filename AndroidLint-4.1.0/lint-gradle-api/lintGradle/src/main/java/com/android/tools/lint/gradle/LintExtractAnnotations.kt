package com.android.tools.lint.gradle

import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.KotlinLintAnalyzerFacade
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.annotations.Extractor
import com.android.tools.lint.gradle.api.ExtractAnnotationRequest
import com.intellij.openapi.util.Disposer
import org.gradle.api.logging.LogLevel
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException

@Suppress("unused") // Accessed via reflection
class LintExtractAnnotations {
    fun extractAnnotations(request: ExtractAnnotationRequest) {
        val typedefFile = request.typedefFile
        val logger = request.logger
        val classDir = request.classDir
        val output = request.output
        val sourceFiles = request.sourceFiles
        val roots = request.roots

        val parentDisposable = Disposer.newDisposable()
        val environment = UastEnvironment.create(parentDisposable)

        try {
            val projectEnvironment = environment.projectEnvironment
            projectEnvironment.registerPaths(roots)
            val parsedUnits = Extractor.createUnitsForFiles(
                projectEnvironment.project,
                sourceFiles
            )

            val ktFiles = ArrayList<File>()
            for (file in sourceFiles) {
                if (file.path.endsWith(DOT_KT)) {
                    ktFiles.add(file)
                }
            }

            val facade = KotlinLintAnalyzerFacade()
            facade.analyze(ktFiles, roots, projectEnvironment.project, environment)

            val displayInfo = logger.isEnabled(LogLevel.INFO)
            val extractor = Extractor(null, classDir.files, displayInfo, false, false)

            extractor.extractFromProjectSource(parsedUnits)
            extractor.export(output, null)
            extractor.writeTypedefFile(typedefFile)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        } finally {
            Disposer.dispose(parentDisposable)
        }
    }
}