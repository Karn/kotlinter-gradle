package org.jmailen.gradle.kotlinter.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jmailen.gradle.kotlinter.support.KotlinterError
import org.jmailen.gradle.kotlinter.tasks.format.FormatWorkerAction
import java.util.zip.ZipFile
import javax.inject.Inject

@CacheableTask
abstract class FormatTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    private val objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : ConfigurableKtLintTask(
    projectLayout = projectLayout,
    objectFactory = objectFactory,
) {
    @OutputFile
    @Optional
    val report: RegularFileProperty = objectFactory.fileProperty()

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run(inputChanges: InputChanges) {
        ruleSetsClassPath.files
            .onEach { println("Files: $it") }
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
            submit(FormatWorkerAction::class.java) { p ->
                p.name.set(name)
                p.files.from(source)
                p.projectDirectory.set(projectLayout.projectDirectory.asFile)
                p.ktLintParams.set(getKtLintParams())
                p.output.set(report)
                p.changedEditorConfigFiles.from(getChangedEditorconfigFiles(inputChanges))
            }
            runCatching { await() }
        }

        result.exceptionOrNull()?.workErrorCauses<KotlinterError>()?.ifNotEmpty {
            forEach { logger.error(it.message, it.cause) }
            throw GradleException("error formatting sources for $name")
        }
    }
}
