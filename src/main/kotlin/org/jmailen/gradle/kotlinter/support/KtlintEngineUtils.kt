package org.jmailen.gradle.kotlinter.support

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.logging.Logger
import java.io.File

internal fun createKtlintEngine(providers: List<RuleSetProviderV3>) = KtLintRuleEngine(
    ruleProviders = resolveRuleProviders(defaultRuleSetProviders + providers),
)

internal fun KtLintRuleEngine.resetEditorconfigCacheIfNeeded(changedEditorconfigFiles: ConfigurableFileCollection, logger: Logger) {
    val changedFiles = changedEditorconfigFiles.files
    if (changedFiles.any()) {
        logger.info("Editorconfig changed, resetting KtLint caches")
        changedFiles.map(File::toPath).forEach {
            this.reloadEditorConfigFile(it)
        }
    }
}
