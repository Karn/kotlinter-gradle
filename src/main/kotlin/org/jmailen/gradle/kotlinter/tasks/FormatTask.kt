package org.jmailen.gradle.kotlinter.tasks

import org.gradle.api.file.FileTree
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import org.jmailen.gradle.kotlinter.KotlinterExtension
import org.jmailen.gradle.kotlinter.support.LintFailure
import org.jmailen.gradle.kotlinter.tasks.format.FormatWorkerAction
import javax.inject.Inject

open class FormatTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : ConfigurableKtLintTask(
    projectLayout = projectLayout,
    objectFactory = objectFactory,
) {

    @OutputFile
    @Optional
    val report: RegularFileProperty = objectFactory.fileProperty()

    @Input
    val ignoreFormatFailures: Property<Boolean> = objectFactory.property(
        default = KotlinterExtension.DEFAULT_IGNORE_FORMAT_FAILURES,
    )

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Incremental
    override fun getSource(): FileTree = super.getSource()

    // Note: FormatTask now supports proper incremental processing and caching.
    // Previously had outputs.upToDateWhen { false } which disabled all caching.
    // With @Incremental input tracking, Gradle can properly determine UP_TO_DATE status.

    @TaskAction
    fun run(inputChanges: InputChanges) {
        val changedFiles = getChangedFiles(inputChanges)

        val workQueue = workerExecutor.processIsolation { config ->
            config.classpath.setFrom(ktlintClasspath)
        }
        workQueue.submit(FormatWorkerAction::class.java) { p ->
            p.name.set(name)
            p.files.from(changedFiles)
            p.projectDirectory.set(projectLayout.projectDirectory.asFile)
            p.output.set(report)
            p.changedEditorConfigFiles.from(getChangedEditorconfigFiles(inputChanges))
        }
        try {
            workQueue.await()
        } catch (e: WorkerExecutionException) {
            if (e.hasRootCause(LintFailure::class.java)) {
                if (!ignoreFormatFailures.get()) {
                    throw e
                }
            } else {
                throw e
            }
        }
    }
}
