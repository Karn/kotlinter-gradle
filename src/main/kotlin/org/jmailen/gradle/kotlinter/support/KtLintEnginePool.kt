package org.jmailen.gradle.kotlinter.support

import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import org.gradle.api.logging.Logger
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import kotlin.math.min

/**
 * Thread-safe pool of KtLint engine instances that provides better resource management
 * than ThreadLocal approach while maintaining parallel processing performance.
 *
 * Advantages over ThreadLocal:
 * - Bounded memory usage (fixed pool size)
 * - No memory leak risks
 * - Explicit resource management
 * - Better cache consistency
 */
class KtLintEnginePool(private val poolSize: Int = min(4, Runtime.getRuntime().availableProcessors())) {
    private val engines = ConcurrentLinkedQueue<KtLintRuleEngine>()
    private val semaphore = Semaphore(poolSize)
    private val cachedRuleProviders = resolveRuleProviders(defaultRuleSetProviders)

    init {
        // Pre-populate pool with engine instances
        repeat(poolSize) {
            engines.offer(createEngine())
        }
    }

    /**
     * Execute block with an engine instance from the pool.
     * Blocks if no engines are available, ensuring bounded resource usage.
     */
    fun <T> withEngine(block: (KtLintRuleEngine) -> T): T {
        semaphore.acquire()
        try {
            val engine = engines.poll() ?: createEngine()
            try {
                return block(engine)
            } finally {
                engines.offer(engine)
            }
        } finally {
            semaphore.release()
        }
    }

    /**
     * Reload EditorConfig in all pooled engines for consistency.
     * This addresses the cache inconsistency issue in ThreadLocal approach.
     */
    fun reloadEditorConfigFile(path: Path) {
        // Temporarily acquire all engines to update their caches
        val acquiredEngines = mutableListOf<KtLintRuleEngine>()

        repeat(poolSize) {
            semaphore.acquire()
            engines.poll()?.let { acquiredEngines.add(it) }
        }

        try {
            // Update all engines consistently
            acquiredEngines.forEach { engine ->
                engine.reloadEditorConfigFile(path)
            }
        } finally {
            // Return engines to pool
            acquiredEngines.forEach { engines.offer(it) }
            repeat(acquiredEngines.size) { semaphore.release() }
        }
    }

    private fun createEngine(): KtLintRuleEngine = KtLintRuleEngine(ruleProviders = cachedRuleProviders)
}

/**
 * Adaptive processing strategy that chooses optimal approach based on workload.
 *
 * - Small projects: Single engine (simple, fast)
 * - Large projects: Engine pool (parallel benefits outweigh overhead)
 */
class AdaptiveKtLintProcessor {
    private val singleEngine by lazy {
        KtLintRuleEngine(ruleProviders = resolveRuleProviders(defaultRuleSetProviders))
    }
    private val enginePool by lazy { KtLintEnginePool() }

    companion object {
        private const val PARALLEL_THRESHOLD = 25 // files
    }

    /**
     * Process files using adaptive strategy based on workload size and parallel preference.
     */
    fun <T> processFiles(files: List<File>, parallelEnabled: Boolean, processor: (KtLintRuleEngine, File) -> T): List<T> = when {
        !parallelEnabled -> processSequentially(files, processor)
        files.size < PARALLEL_THRESHOLD -> processSequentially(files, processor)
        else -> processInParallel(files, processor)
    }

    private fun <T> processSequentially(files: List<File>, processor: (KtLintRuleEngine, File) -> T): List<T> = files.map { file ->
        processor(singleEngine, file)
    }

    private fun <T> processInParallel(files: List<File>, processor: (KtLintRuleEngine, File) -> T): List<T> =
        files.parallelStream().map { file ->
            enginePool.withEngine { engine ->
                processor(engine, file)
            }
        }.toList()

    /**
     * Reload EditorConfig in both single engine and engine pool for consistency.
     */
    fun reloadEditorConfigFile(path: Path, logger: Logger) {
        logger.info("Reloading EditorConfig in all engines for path: $path")
        singleEngine.reloadEditorConfigFile(path)
        enginePool.reloadEditorConfigFile(path)
    }
}
