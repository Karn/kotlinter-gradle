package org.jmailen.gradle.kotlinter.functional.utils

import java.io.File

internal fun File.resolve(path: String, receiver: File.() -> Unit): File =
    resolve(path).apply {
        parentFile.mkdirs()
        receiver()
    }


internal fun File.printRecursively() {
    println(absolutePath)

    this.listFiles()?.forEach {
        it.printRecursively()
    }
}
