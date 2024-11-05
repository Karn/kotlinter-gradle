package org.jmailen.gradle.kotlinter.functional

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.jmailen.gradle.kotlinter.functional.utils.androidManifest
import org.jmailen.gradle.kotlinter.functional.utils.kotlinClass
import org.jmailen.gradle.kotlinter.functional.utils.resolve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class AndroidProjectTest : WithGradleTest.Android() {

    private lateinit var androidModuleRoot: File
    private lateinit var ruleSetModuleRoot: File

    @BeforeEach
    fun setUp() {
        testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                // language=groovy
                val buildScript =
                    """
                    subprojects {
                        repositories {
                            google()
                            mavenCentral()
                        }
                    }
                    """.trimIndent()
                writeText(buildScript)
            }
            androidModuleRoot = resolve("androidproject") {
                resolve("build.gradle") {
                    // language=groovy
                    val androidBuildScript =
                        """
                        plugins {
                            id 'com.android.library'
                            id 'kotlin-android'
                            id 'org.jmailen.kotlinter'
                        }
                        
                        android {
                            compileSdkVersion 31
                            defaultConfig {
                                minSdkVersion 23
                            }
                            
                            flavorDimensions 'customFlavor'
                            productFlavors {
                                flavorOne {
                                    dimension 'customFlavor'
                                }
                                flavorTwo {
                                    dimension 'customFlavor'
                                }
                            }
                        }
                        
                        dependencies {
                            ktlintRuleset(project(":ktlint-ruleset"))
                        }
                        """.trimIndent()
                    writeText(androidBuildScript)
                }
                resolve("src/main/AndroidManifest.xml") {
                    writeText(androidManifest)
                }
                resolve("src/main/kotlin/MainSourceSet.kt") {
                    writeText(kotlinClass("MainSourceSet"))
                }
                resolve("src/debug/kotlin/DebugSourceSet.kt") {
                    writeText(kotlinClass("DebugSourceSet"))
                }
                resolve("src/test/kotlin/TestSourceSet.kt") {
                    writeText(kotlinClass("TestSourceSet"))
                }
                resolve("src/flavorOne/kotlin/FlavorSourceSet.kt") {
                    writeText(kotlinClass("FlavorSourceSet"))
                }
            }
            ruleSetModuleRoot = resolve("ktlint-ruleset") {
                resolve("build.gradle") {
                    // language=groovy
                    val androidBuildScript =
                        """
                            plugins {
                                id 'kotlin'
                                id 'org.jmailen.kotlinter'
                            }
                            dependencies {
                                implementation("com.pinterest.ktlint:ktlint-cli-ruleset-core:1.3.1")
                                implementation("com.pinterest.ktlint:ktlint-rule-engine-core:1.3.1")
                            }
                        """.trimIndent()

                    writeText(androidBuildScript)
                }
                resolve("src/main/java/com/example/rules/CustomRuleSetProvider.kt") {
                    // language=kotlin
                    val customRuleSetProvider = """
                        package com.example.rules
                        
                        import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
                        import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
                        import com.pinterest.ktlint.rule.engine.core.api.RuleSetId
                        
                        internal val CUSTOM_RULE_SET_ID = "custom-rule-set-id"
                        
                        public class CustomRuleSetProvider : RuleSetProviderV3(RuleSetId(CUSTOM_RULE_SET_ID)) {
                            override fun getRuleProviders(): Set<RuleProvider> =
                                setOf(
                                    RuleProvider { NoVarRule() },
                                )
                        }

                    """.trimIndent()

                    writeText(customRuleSetProvider)
                }
                resolve("src/main/java/com/example/rules/NoVarRule.kt") {
                    // language=kotlin
                    val customRuleSetProvider = """
                        package com.example.rules
                        
                        import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
                        import com.pinterest.ktlint.rule.engine.core.api.ElementType
                        import com.pinterest.ktlint.rule.engine.core.api.Rule
                        import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
                        import com.pinterest.ktlint.rule.engine.core.api.RuleId
                        import org.jetbrains.kotlin.com.intellij.lang.ASTNode
                        
                        class NoVarRule :
                            Rule(
                                ruleId = RuleId("custom:no-var"),
                                about = About("maintainer"),
                            ),
                            RuleAutocorrectApproveHandler {
                            override fun beforeVisitChildNodes(
                                node: ASTNode,
                                emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
                            ) {
                                if (node.elementType == ElementType.VAR_KEYWORD) {
                                    emit(node.startOffset, "Unexpected var, use val instead", false)
                                }
                            }
                        }

                    """.trimIndent()

                    writeText(customRuleSetProvider)
                }
                resolve("src/main/resources/META-INF/services/com.pinterest.ktlint.core.RuleSetProvider") {
                    val customRuleSetProvider = "com.example.rules.CustomRuleSetProvider"

                    writeText(customRuleSetProvider)
                }
                resolve("src/main/resources/META-INF/services/com.pinterest.ktlint.core.RuleSetProviderV2") {
                    val customRuleSetProvider = "com.example.rules.CustomRuleSetProvider"

                    writeText(customRuleSetProvider)
                }
                resolve("src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3") {
                    val customRuleSetProvider = "com.example.rules.CustomRuleSetProvider"

                    writeText(customRuleSetProvider)
                }
            }
        }
    }

    @Test
    fun runsOnAndroidProject() {
        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":androidproject:lintKotlinMain")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":androidproject:lintKotlinDebug")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":androidproject:lintKotlinTest")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":androidproject:lintKotlinFlavorOne")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":androidproject:lintKotlin")?.outcome)
        }
    }

    @Test
    fun loadsCustomRulesOnAndroidProject() {
        val className = "ExampleFile"
        androidModuleRoot.apply {
            resolve("src/main/kotlin/$className.kt") {
                val exampleFile =
                    """
                    class $className {
                        private fun hi() {
                            var helloWorld = "Hello World!"
                            println(helloWorld)
                        }
                    }
        
                    """.trimIndent()

                writeText(exampleFile)
            }
        }

        buildAndFail("lintKotlinMain").apply {
            assertTrue(output.contains("$className.kt:3:9: Lint error > [custom:no-var] Unexpected var, use val instead"))

            val pathPattern = "(.*\\.kt):\\d+:\\d+".toRegex()
            output.lines().filter { it.contains("Lint error") }.forEach { line ->
                val filePath = pathPattern.find(line)?.groups?.get(1)?.value.orEmpty()
                assertTrue(File(filePath).exists())
            }
            assertEquals(FAILED, task(":androidproject:lintKotlinMain")?.outcome)
        }
    }

    // language=groovy
    private val settingsFile =
        """
        rootProject.name = 'kotlinter'
        include 'androidproject'
        include 'ktlint-ruleset'
        """.trimIndent()
}
