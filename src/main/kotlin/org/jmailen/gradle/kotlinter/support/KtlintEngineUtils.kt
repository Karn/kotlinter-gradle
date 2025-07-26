package org.jmailen.gradle.kotlinter.support

import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.logging.Logger
import java.io.File

// Global adaptive processor instance for consistent engine management
private val adaptiveProcessor = AdaptiveKtLintProcessor()

// Legacy ThreadLocal approach (kept for backward compatibility and comparison)
private val cachedRuleProviders = resolveRuleProviders(defaultRuleSetProviders)
private val threadLocalEngines = ThreadLocal.withInitial {
    KtLintRuleEngine(ruleProviders = cachedRuleProviders)
}

/**
 * Legacy ThreadLocal engine access (maintained for backward compatibility).
 * Consider using processFilesWithAdaptiveEngine for new implementations.
 */
internal val ktlintEngine: KtLintRuleEngine get() = threadLocalEngines.get()

/**
 * Process files using adaptive engine strategy that chooses optimal approach
 * based on workload size and parallel processing preference.
 *
 * This is the recommended approach over direct ktlintEngine access.
 */
internal fun <T> processFilesWithAdaptiveEngine(
    files: List<File>,
    parallelEnabled: Boolean,
    processor: (KtLintRuleEngine, File) -> T,
): List<T> = adaptiveProcessor.processFiles(files, parallelEnabled, processor)

internal fun resetEditorconfigCacheIfNeeded(changedEditorconfigFiles: ConfigurableFileCollection, logger: Logger) {
    val changedFiles = changedEditorconfigFiles.files
    if (changedFiles.any()) {
        logger.info("EditorConfig changed, updating all engines")
        changedFiles.map { it.toPath() }.forEach { path ->
            // Update both legacy ThreadLocal engines and adaptive processor engines
            ktlintEngine.reloadEditorConfigFile(path) // Legacy ThreadLocal approach
            adaptiveProcessor.reloadEditorConfigFile(path, logger) // New adaptive approach
        }
    }
}
