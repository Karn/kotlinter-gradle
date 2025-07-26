package org.jmailen.gradle.kotlinter.performance

import org.gradle.testkit.runner.TaskOutcome
import org.jmailen.gradle.kotlinter.functional.WithGradleTest
import org.jmailen.gradle.kotlinter.functional.utils.repositories
import org.jmailen.gradle.kotlinter.functional.utils.resolve
import org.jmailen.gradle.kotlinter.functional.utils.settingsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Simple performance test to measure the impact of parallel processing optimization.
 *
 * This test creates a modest number of files and measures the difference between
 * sequential and parallel processing to validate our optimization works.
 */
internal class SimplePerformanceTest : WithGradleTest.Kotlin() {

    lateinit var projectRoot: File

    @BeforeEach
    fun setUp() {
        projectRoot = testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                // language=groovy
                val buildScript =
                    """
                    plugins {
                        id 'kotlin'
                        id 'org.jmailen.kotlinter'
                    }
                    $repositories
                    """.trimIndent()
                writeText(buildScript)
            }
        }
    }

    @EnabledIf("hasMultipleCores")
    @Test
    fun `measure performance difference between sequential and parallel processing`() {
        println("\n=== Performance Measurement Test ===")

        // Create a reasonable number of test files
        val fileCount = 15
        createTestFiles(fileCount)

        // Measure sequential processing time
        println("Testing sequential processing...")
        val sequentialTime = measureTimeMillis {
            build("lintKotlin").apply {
                assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
            }
        }

        // Enable parallel processing and measure
        println("Testing parallel processing...")
        enableParallelProcessing()
        val parallelTime = measureTimeMillis {
            build("lintKotlin").apply {
                assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
            }
        }

        // Calculate and report results
        val speedupRatio = sequentialTime.toDouble() / parallelTime.toDouble()
        val improvementPercent = ((sequentialTime - parallelTime).toDouble() / sequentialTime.toDouble()) * 100

        println("\\n=== RESULTS ===")
        println("Files processed: $fileCount")
        println("Sequential time: ${sequentialTime}ms")
        println("Parallel time: ${parallelTime}ms")
        println("Speedup ratio: ${String.format("%.2f", speedupRatio)}x")
        println("Improvement: ${String.format("%.1f", improvementPercent)}%")

        // On multi-core systems, parallel should not be significantly slower
        val minAcceptableRatio = 0.7 // Allow parallel to be up to 30% slower due to overhead
        if (speedupRatio < minAcceptableRatio) {
            println("WARNING: Parallel processing is significantly slower than expected")
            println("This might indicate an issue with the parallel implementation")
        } else if (speedupRatio > 1.0) {
            println("SUCCESS: Parallel processing shows performance improvement!")
        } else {
            println("INFO: Parallel processing shows acceptable performance (within overhead tolerance)")
        }
    }

    @Test
    fun `measure format task performance`() {
        println("\n=== Format Performance Test ===")

        val fileCount = 10
        createFormattingTestFiles(fileCount)

        // Test sequential formatting
        val sequentialTime = measureTimeMillis {
            build("formatKotlin").apply {
                assertEquals(TaskOutcome.SUCCESS, task(":formatKotlinMain")?.outcome)
            }
        }

        // Reset files and test parallel formatting
        createFormattingTestFiles(fileCount)
        enableParallelProcessing()

        val parallelTime = measureTimeMillis {
            build("formatKotlin").apply {
                assertEquals(TaskOutcome.SUCCESS, task(":formatKotlinMain")?.outcome)
            }
        }

        val speedupRatio = sequentialTime.toDouble() / parallelTime.toDouble()
        val improvementPercent = ((sequentialTime - parallelTime).toDouble() / sequentialTime.toDouble()) * 100

        println("Format Results:")
        println("Sequential: ${sequentialTime}ms, Parallel: ${parallelTime}ms")
        println("Speedup: ${String.format("%.2f", speedupRatio)}x, Improvement: ${String.format("%.1f", improvementPercent)}%")
    }

    @Test
    fun `measure overhead with small file count`() {
        println("\n=== Overhead Measurement ===")

        // Test with just a few files to measure parallel processing overhead
        val fileCount = 3
        createTestFiles(fileCount)

        val sequentialTime = measureTimeMillis {
            build("lintKotlin").apply {
                assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
            }
        }

        enableParallelProcessing()
        val parallelTime = measureTimeMillis {
            build("lintKotlin").apply {
                assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
            }
        }

        val overhead = parallelTime - sequentialTime
        val overheadPercent = (overhead.toDouble() / sequentialTime.toDouble()) * 100

        println("Overhead with $fileCount files:")
        println("Sequential: ${sequentialTime}ms, Parallel: ${parallelTime}ms")
        println("Overhead: ${overhead}ms (${String.format("%.1f", overheadPercent)}%)")

        // Overhead should be reasonable for small file counts
        if (overheadPercent > 100) {
            println("WARNING: High overhead detected with small file count")
        }
    }

    private fun createTestFiles(count: Int) {
        repeat(count) { i ->
            projectRoot.resolve("src/main/kotlin/TestClass$i.kt") {
                writeText(generateKotlinClass("TestClass$i"))
            }
        }
    }

    private fun createFormattingTestFiles(count: Int) {
        repeat(count) { i ->
            projectRoot.resolve("src/main/kotlin/FormatClass$i.kt") {
                writeText(generateUnformattedKotlinClass("FormatClass$i"))
            }
        }
    }

    private fun enableParallelProcessing() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                plugins {
                    id 'kotlin'
                    id 'org.jmailen.kotlinter'
                }
                $repositories
                
                kotlinter {
                    parallelProcessing = true
                }
                """.trimIndent()
            writeText(buildScript)
        }
    }

    private fun generateKotlinClass(className: String): String = """
        package com.example
        
        /**
         * Test class $className for performance testing
         */
        class $className {
            val id: String = "${className.lowercase()}"
            
            fun getId(): String = id
            
            fun process(input: String): String {
                return "Processing: " + input + " in $className"
            }
            
            fun calculate(): Int {
                return (1..100).sum()
            }
            
            override fun toString(): String = "$className(id='" + id + "')"
            
            companion object {
                const val TYPE = "$className"
                
                fun create(): $className = $className()
            }
        }
    """.trimIndent()

    private fun generateUnformattedKotlinClass(className: String): String = """
        package com.example

        class $className{
            val   value   =   42
            fun   getValue(  )  :  Int  {
                return   value
            }
            fun   process(  input  :  String  )  :  String{
                return   input.uppercase(  )
            }
        }
    """.trimIndent()

    companion object {
        @JvmStatic
        fun hasMultipleCores(): Boolean = Runtime.getRuntime().availableProcessors() > 1
    }
}
