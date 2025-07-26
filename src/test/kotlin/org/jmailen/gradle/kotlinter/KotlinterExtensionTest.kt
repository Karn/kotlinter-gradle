package org.jmailen.gradle.kotlinter

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KotlinterExtensionTest {

    private fun createTestProject(): Project = ProjectBuilder.builder().build()

    @Test
    fun `extension has correct default values`() {
        val project = createTestProject()
        val extension = KotlinterExtension(project)

        assertTrue(extension.ignoreFormatFailures)
        assertFalse(extension.ignoreLintFailures)
        assertFalse(extension.parallelProcessing)
        assertEquals(listOf("checkstyle"), extension.reporters.toList())
    }

    @Test
    fun `extension allows configuring parallelProcessing`() {
        val project = createTestProject()
        val extension = KotlinterExtension(project)

        // Default should be false
        assertFalse(extension.parallelProcessing)

        // Should be configurable
        extension.parallelProcessing = true
        assertTrue(extension.parallelProcessing)

        extension.parallelProcessing = false
        assertFalse(extension.parallelProcessing)
    }

    @Test
    fun `extension respects kotlinter parallel property`() {
        val project = createTestProject()
        project.extensions.extraProperties.set("kotlinter.parallel", "true")
        
        val extension = KotlinterExtension(project)
        assertTrue(extension.parallelProcessing)
    }


    @Test
    fun `extension handles invalid property values gracefully`() {
        val project = createTestProject()
        project.extensions.extraProperties.set("kotlinter.parallel", "invalid")
        
        val extension = KotlinterExtension(project)
        assertFalse(extension.parallelProcessing) // Should fall back to default
    }

    @Test
    fun `extension constants have expected values`() {
        assertTrue(KotlinterExtension.DEFAULT_IGNORE_FORMAT_FAILURES)
        assertFalse(KotlinterExtension.DEFAULT_IGNORE_LINT_FAILURES)
        assertFalse(KotlinterExtension.DEFAULT_PARALLEL_PROCESSING)
        assertEquals("checkstyle", KotlinterExtension.DEFAULT_REPORTER)
        assertEquals("kotlinter.parallel", KotlinterExtension.PARALLEL_PROCESSING_PROPERTY)
    }
}
