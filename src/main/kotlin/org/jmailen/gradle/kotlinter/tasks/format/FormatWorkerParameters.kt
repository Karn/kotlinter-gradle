package org.jmailen.gradle.kotlinter.tasks.format

import com.pinterest.ktlint.core.RuleSetProviderV2
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.workers.WorkParameters
import org.jmailen.gradle.kotlinter.support.KtLintParams

interface FormatWorkerParameters : WorkParameters {
    val name: Property<String>
    val changedEditorConfigFiles: ConfigurableFileCollection
    val files: ConfigurableFileCollection
    val projectDirectory: RegularFileProperty
    val ktLintParams: Property<KtLintParams>
    val output: RegularFileProperty
    val customRuleSetProviders: ConfigurableFileCollection
}
