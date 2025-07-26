package org.jmailen.gradle.kotlinter

import org.gradle.api.Project
import org.jmailen.gradle.kotlinter.support.ReporterType
import org.jmailen.gradle.kotlinter.support.versionProperties

open class KotlinterExtension(private val project: Project) {
    companion object {
        const val DEFAULT_IGNORE_FORMAT_FAILURES = true
        const val DEFAULT_IGNORE_LINT_FAILURES = false
        const val DEFAULT_PARALLEL_PROCESSING = false
        val DEFAULT_REPORTER = ReporterType.checkstyle.name

        // Gradle project property name for configuration
        const val PARALLEL_PROCESSING_PROPERTY = "kotlinter.parallel"
    }

    var ktlintVersion = versionProperties.ktlintVersion()
    var ignoreFormatFailures = DEFAULT_IGNORE_FORMAT_FAILURES
    var ignoreLintFailures = DEFAULT_IGNORE_LINT_FAILURES
    var reporters = arrayOf(DEFAULT_REPORTER)

    /**
     * Enable parallel processing for KtLint operations.
     *
     * This property can be configured in multiple ways (in order of precedence):
     * 1. Gradle project property: -Pkotlinter.parallel=true
     * 2. Build script configuration: kotlinter { parallelProcessing = true }
     * 3. Default value: false
     *
     * Examples:
     * - Command line: ./gradlew lintKotlin -Pkotlinter.parallel=true
     * - gradle.properties: kotlinter.parallel=true
     * - Build script: kotlinter { parallelProcessing = true }
     */
    var parallelProcessing: Boolean = resolveParallelProcessing()
        get() = field
        set(value) {
            field = value
        }

    private fun resolveParallelProcessing(): Boolean {
        // Check project property
        project.findProperty(PARALLEL_PROCESSING_PROPERTY)?.let { value ->
            return when (value.toString().lowercase()) {
                "true" -> true
                "false" -> false
                else -> {
                    project.logger.warn(
                        "Invalid value for $PARALLEL_PROCESSING_PROPERTY: '$value'. Using default: $DEFAULT_PARALLEL_PROCESSING",
                    )
                    DEFAULT_PARALLEL_PROCESSING
                }
            }
        }

        // Use default value
        return DEFAULT_PARALLEL_PROCESSING
    }
}
