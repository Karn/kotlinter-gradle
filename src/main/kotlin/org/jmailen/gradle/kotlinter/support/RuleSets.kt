package org.jmailen.gradle.kotlinter.support

import com.pinterest.ktlint.core.RuleProvider
import com.pinterest.ktlint.core.RuleSetProviderV2
import org.gradle.api.file.ConfigurableFileCollection
import java.net.URLClassLoader
import java.util.ServiceLoader

internal fun resolveRuleProviders(
    providers: Iterable<RuleSetProviderV2>,
): Set<RuleProvider> = providers
    .asSequence()
    .sortedWith(
        compareBy {
            when (it.id) {
                "standard" -> 0
                else -> 1
            }
        },
    )
    .map(RuleSetProviderV2::getRuleProviders)
    .flatten()
    .toSet()

// statically resolve providers from plugin classpath. ServiceLoader#load alone resolves classes lazily which fails when run in parallel
// https://github.com/jeremymailen/kotlinter-gradle/issues/101
val defaultRuleSetProviders: List<RuleSetProviderV2> =
    ServiceLoader.load(RuleSetProviderV2::class.java).toList()

fun ktlintRulesetsFromClasspath(classpath: ConfigurableFileCollection): List<RuleSetProviderV2> {
    // Load the files from the classpath into a new ClassLoader
    @Suppress("DEPRECATION")
    val fileUris = classpath.map { it.toURL() }.toTypedArray()
    val classLoader = URLClassLoader(fileUris, Thread.currentThread().contextClassLoader)
    return ServiceLoader.load(RuleSetProviderV2::class.java, classLoader).toList()
}
