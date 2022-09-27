package org.jmailen.gradle.kotlinter.functional

import org.gradle.testkit.runner.TaskOutcome
import org.jmailen.gradle.kotlinter.functional.utils.androidManifest
import org.jmailen.gradle.kotlinter.functional.utils.kotlinClass
import org.jmailen.gradle.kotlinter.functional.utils.resolve
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

internal class AndroidProjectTest : WithGradleTest.Android() {

    private lateinit var androidModuleRoot: File
    private lateinit var ruleSetModuleRoot: File

    @Before
    fun setUp() {
        testProjectDir.root.apply {
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
                            implementation("com.pinterest.ktlint:ktlint-core:0.47.1")
                        }
                        """.trimIndent()

                    writeText(androidBuildScript)
                }
                resolve("src/main/java/com/example/rules/CustomRuleSetProvider.kt") {
                    // language=kotlin
                    val customRuleSetProvider = """
                        package com.example.rules
                        
                        import com.pinterest.ktlint.core.RuleProvider
                        import com.pinterest.ktlint.core.RuleSetProviderV2
                        
                        class CustomRuleSetProvider : RuleSetProviderV2(
                            id = "custom",
                            about = NO_ABOUT
                        ) {
                            override fun getRuleProviders() = setOf(
                                RuleProvider { NoVarRule() }
                            )
                        }

                    """.trimIndent()

                    writeText(customRuleSetProvider)
                }
                resolve("src/main/java/com/example/rules/NoVarRule.kt") {
                    // language=kotlin
                    val customRuleSetProvider = """
                        package com.example.rules
                        
                        import com.pinterest.ktlint.core.Rule
                        import com.pinterest.ktlint.core.ast.ElementType.VAR_KEYWORD
                        import org.jetbrains.kotlin.com.intellij.lang.ASTNode
                        
                        public class NoVarRule : Rule("custom:no-var") {
                        
                            override fun beforeVisitChildNodes(
                                node: ASTNode,
                                autoCorrect: Boolean,
                                emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
                            ) {
                                if (node.elementType == VAR_KEYWORD) {
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

    // language=groovy
    private val settingsFile =
        """
        rootProject.name = 'kotlinter'
        include ':androidproject'
        include ':ktlint-ruleset'
        """.trimIndent()
}
