package org.jmailen.gradle.kotlinter.functional

import org.gradle.testkit.runner.TaskOutcome
import org.jmailen.gradle.kotlinter.functional.utils.kotlinClass
import org.jmailen.gradle.kotlinter.functional.utils.repositories
import org.jmailen.gradle.kotlinter.functional.utils.resolve
import org.jmailen.gradle.kotlinter.functional.utils.settingsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class ParallelProcessingTest : WithGradleTest.Kotlin() {

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

    @Test
    fun `both parallel and sequential processing work with multiple files`() {
        // Create test files
        repeat(5) { i ->
            projectRoot.resolve("src/main/kotlin/TestClass$i.kt") {
                writeText(kotlinClass("TestClass$i"))
            }
        }

        // Test sequential processing (default)
        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
        }

        // Test parallel processing
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                kotlinter {
                    parallelProcessing = true
                }
                """.trimIndent()
            appendText(script)
        }

        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
        }
    }

    @Test
    fun `parallel processing handles formatting correctly`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                kotlinter {
                    parallelProcessing = true
                }
                """.trimIndent()
            appendText(script)
        }

        // Create files that need formatting
        repeat(3) { i ->
            projectRoot.resolve("src/main/kotlin/BadFormatClass$i.kt") {
                writeText(
                    """
                    class BadFormatClass$i{
                        val   x    =    1
                        fun   method(  )  {
                        }
                    }
                    """.trimIndent(),
                )
            }
        }

        build("formatKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":formatKotlinMain")?.outcome)
        }

        // Verify files were actually formatted
        repeat(3) { i ->
            val formattedFile = projectRoot.resolve("src/main/kotlin/BadFormatClass$i.kt")
            val content = formattedFile.readText()
            assertTrue(content.contains("class BadFormatClass$i {"), "File $i should be formatted")
            assertTrue(content.contains("val x = 1"), "File $i spacing should be fixed")
        }
    }

    @Test
    fun `parallel and sequential processing produce same results`() {
        // Create test files with various formatting issues
        repeat(3) { i ->
            projectRoot.resolve("src/main/kotlin/TestClass$i.kt") {
                writeText(
                    """
                    package com.example
                    
                    class TestClass$i{
                        val   value    =    $i
                        fun   getValue(  )  :  Int  {
                            return   value
                        }
                    }
                    """.trimIndent(),
                )
            }
        }

        // Test sequential processing
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                kotlinter {
                    parallelProcessing = false
                }
                """.trimIndent()
            appendText(script)
        }

        build("formatKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":formatKotlinMain")?.outcome)
        }

        // Capture sequential results
        val sequentialResults = mutableMapOf<String, String>()
        repeat(3) { i ->
            val file = projectRoot.resolve("src/main/kotlin/TestClass$i.kt")
            sequentialResults["TestClass$i.kt"] = file.readText()
        }

        // Reset files to original state
        repeat(3) { i ->
            projectRoot.resolve("src/main/kotlin/TestClass$i.kt") {
                writeText(
                    """
                    package com.example
                    
                    class TestClass$i{
                        val   value    =    $i
                        fun   getValue(  )  :  Int  {
                            return   value
                        }
                    }
                    """.trimIndent(),
                )
            }
        }

        // Test parallel processing
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

        build("formatKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":formatKotlinMain")?.outcome)
        }

        // Compare results
        repeat(3) { i ->
            val file = projectRoot.resolve("src/main/kotlin/TestClass$i.kt")
            val parallelResult = file.readText()
            val sequentialResult = sequentialResults["TestClass$i.kt"]
            assertEquals(
                sequentialResult,
                parallelResult,
                "Parallel and sequential formatting should produce identical results for TestClass$i.kt",
            )
        }
    }
}
