package org.jmailen.gradle.kotlinter.support

import com.pinterest.ktlint.core.KtLintRuleEngine
import com.pinterest.ktlint.core.RuleSetProviderV2
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.logging.Logger
import java.io.File

// You must create a new KtLintRuleEngine per file with fresh rule providers.
// Otherwise, KtLint errors on resolving rule enable/disable statements in .editorconfig
internal fun createKtlintEngine(vararg providers: RuleSetProviderV2) = KtLintRuleEngine(
    ruleProviders = resolveRuleProviders(defaultRuleSetProviders + providers),
)

internal fun resetEditorconfigCacheIfNeeded(
    changedEditorconfigFiles: ConfigurableFileCollection,
    logger: Logger,
) {
    val engine = createKtlintEngine()
    val changedFiles = changedEditorconfigFiles.files
    if (changedFiles.any()) {
        logger.info("Editorconfig changed, resetting KtLint caches")
        changedFiles.map(File::toPath).forEach {
            engine.reloadEditorConfigFile(it)
        }
    }
}
