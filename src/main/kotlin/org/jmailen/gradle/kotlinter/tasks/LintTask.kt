package org.jmailen.gradle.kotlinter.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jmailen.gradle.kotlinter.KotlinterExtension.Companion.DEFAULT_IGNORE_FAILURES
import org.jmailen.gradle.kotlinter.support.KotlinterError
import org.jmailen.gradle.kotlinter.support.LintFailure
import org.jmailen.gradle.kotlinter.tasks.lint.LintWorkerAction
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@CacheableTask
abstract class LintTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    private val objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : ConfigurableKtLintTask(
    projectLayout = projectLayout,
    objectFactory = objectFactory,
) {
    @OutputFiles
    val reports: MapProperty<String, File> = objectFactory.mapProperty(default = emptyMap())

    @Input
    val ignoreFailures: Property<Boolean> = objectFactory.property(default = DEFAULT_IGNORE_FAILURES)

    @TaskAction
    fun run(inputChanges: InputChanges) {
        ruleSetsClassPath.files
            .onEach { println("Files: $it ${it.exists()}") }
            .filter { it.name in setOf("ktlint-ruleset.jar", "ktlint-0.0.11.jar") }
            .forEach { ruleset ->
                println("Using ruleset: ${ruleset.absolutePath} ${ruleset.exists()}")

                ruleset.takeIf { it.exists() } ?: return@forEach

                ZipFile(ruleset).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        println("Found file: ${entry.name}")
                    }
                }
            }

        val result = with(
            workerExecutor.processIsolation { spec ->
                spec.classpath.setFrom(ruleSetsClassPath.files)
            },
        ) {
            submit(LintWorkerAction::class.java) { p ->
                p.name.set(name)
                p.files.from(source)
                p.projectDirectory.set(projectLayout.projectDirectory.asFile)
                p.reporters.putAll(reports)
                p.ktLintParams.set(getKtLintParams())
                p.changedEditorconfigFiles.from(getChangedEditorconfigFiles(inputChanges))
            }
            runCatching { await() }
        }

        result.exceptionOrNull()?.workErrorCauses<KotlinterError>()?.ifNotEmpty {
            forEach { logger.error(it.message, it.cause) }
            throw GradleException("error linting sources for $name")
        }

        val lintFailures = result.exceptionOrNull()?.workErrorCauses<LintFailure>() ?: emptyList()
        if (lintFailures.isNotEmpty() && !ignoreFailures.get()) {
            throw GradleException("$name sources failed lint check")
        }
    }
}
