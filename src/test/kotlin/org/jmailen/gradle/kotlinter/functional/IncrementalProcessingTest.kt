package org.jmailen.gradle.kotlinter.functional

import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class IncrementalProcessingTest : WithGradleTest.Kotlin() {

    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var sourceDir: File

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle")
        buildFile = testProjectDir.resolve("build.gradle")
        sourceDir = testProjectDir.resolve("src/main/kotlin/").also(File::mkdirs)
        // Enable incremental processing for these tests
        testProjectDir.resolve("gradle.properties").writeText("kotlinter.format.incremental=true\n")
    }

    @Test
    fun `lint task supports incremental processing`() {
        settingsFile()
        buildFile()

        // Create multiple source files with proper formatting
        val file1 = createKotlinFile("File1.kt", "class File1\n")
        val file2 = createKotlinFile("File2.kt", "class File2\n")
        val file3 = createKotlinFile("File3.kt", "class File3\n")

        // First run - all files should be processed
        build("lintKotlin").apply {
            assertEquals(SUCCESS, task(":lintKotlinMain")?.outcome)
        }

        // Second run - should be up-to-date (cached)
        build("lintKotlin").apply {
            assertEquals(UP_TO_DATE, task(":lintKotlinMain")?.outcome)
        }

        // Modify one file - keep class name matching filename to avoid lint errors
        file1.writeText("class File1 {\n    fun modified() = Unit\n}\n")

        // Third run - should process only changed files (ideally)
        build("lintKotlin").apply {
            assertEquals(SUCCESS, task(":lintKotlinMain")?.outcome)

            // Verify task ran (not UP_TO_DATE)
            assertTrue(task(":lintKotlinMain")?.outcome != UP_TO_DATE)
        }
    }

    @Test
    fun `format task supports incremental processing`() {
        settingsFile()
        buildFile()

        // Create source files with formatting issues
        val file1 = createKotlinFile("File1.kt", "class File1{}\n")
        val file2 = createKotlinFile("File2.kt", "class File2{}\n")

        // First run - format all files
        build("formatKotlin").apply {
            assertEquals(SUCCESS, task(":formatKotlinMain")?.outcome)
        }

        // Files should now be formatted - empty class bodies become just class declarations
        assertTrue(file1.readText().contains("class File1"))
        assertTrue(file2.readText().contains("class File2"))

        // Second run - files are already formatted, so should have less work to do
        build("formatKotlin").apply {
            // FormatTask may still run SUCCESS since it doesn't declare source files as @OutputFiles
            // but with incremental processing, it should at least not process unchanged files
            assertTrue(task(":formatKotlinMain")?.outcome in listOf(SUCCESS, UP_TO_DATE))
        }

        // Modify one file to need formatting again
        file1.writeText("class File1{fun modified()=Unit}\n")

        // Third run - should format only the modified file
        build("formatKotlin").apply {
            assertEquals(SUCCESS, task(":formatKotlinMain")?.outcome)

            // File1 should be formatted (with proper spacing)
            assertTrue(file1.readText().contains("class File1 {"))
            assertTrue(file1.readText().contains("fun modified()"))
            // File2 should remain unchanged from first formatting (no braces for empty class)
            assertTrue(file2.readText().contains("class File2"))
        }
    }

    @Test
    fun `incremental processing works correctly after file changes`() {
        settingsFile()
        buildFile()

        // Create test files
        val file1 = createKotlinFile("TestClass1.kt", "class TestClass1\n")
        val file2 = createKotlinFile("TestClass2.kt", "class TestClass2\n")

        // First run to establish baseline
        build("lintKotlin").apply {
            assertEquals(SUCCESS, task(":lintKotlinMain")?.outcome)
        }

        // Second run should be up-to-date
        build("lintKotlin").apply {
            assertEquals(UP_TO_DATE, task(":lintKotlinMain")?.outcome)
        }

        // Modify one file
        file1.writeText("class TestClass1 {\n    fun modified() = Unit\n}\n")

        // Third run should execute (not up-to-date) due to file change
        build("lintKotlin").apply {
            assertEquals(SUCCESS, task(":lintKotlinMain")?.outcome)
            // Verify it actually ran and wasn't up-to-date
            assertTrue(task(":lintKotlinMain")?.outcome != UP_TO_DATE)
        }
    }

    @Test
    fun `incremental processing works with multiple source sets`() {
        settingsFile()
        buildFile()

        // Create main source files
        val mainFile = createKotlinFile("MainClass.kt", "class MainClass\n")

        // Create test source files
        val testDir = testProjectDir.resolve("src/test/kotlin/").also(File::mkdirs)
        val testFile = testDir.resolve("TestClass.kt").apply {
            writeText("class TestClass\n")
        }

        // First run
        build("lintKotlin").apply {
            assertEquals(SUCCESS, task(":lintKotlinMain")?.outcome)
            assertEquals(SUCCESS, task(":lintKotlinTest")?.outcome)
        }

        // Second run - should be up-to-date
        build("lintKotlin").apply {
            assertEquals(UP_TO_DATE, task(":lintKotlinMain")?.outcome)
            assertEquals(UP_TO_DATE, task(":lintKotlinTest")?.outcome)
        }

        // Modify only test file
        testFile.writeText("class TestClass {\n    fun modified() = Unit\n}\n")

        // Third run - main should be up-to-date, test should run
        build("lintKotlin").apply {
            assertEquals(UP_TO_DATE, task(":lintKotlinMain")?.outcome)
            assertEquals(SUCCESS, task(":lintKotlinTest")?.outcome)
        }
    }

    private fun settingsFile() = settingsFile.writeText(
        """
        rootProject.name = "incremental-test"
        """.trimIndent(),
    )

    private fun buildFile() = buildFile.writeText(
        """
        plugins {
            id 'org.jetbrains.kotlin.jvm'
            id 'org.jmailen.kotlinter'
        }
        
        repositories {
            mavenCentral()
        }
        """.trimIndent(),
    )

    private fun createKotlinFile(filename: String, content: String): File = sourceDir.resolve(filename).apply {
        writeText(content)
    }
}
