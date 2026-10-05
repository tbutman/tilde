package com.tbutman.nfcshare

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every vector drawable's pathData must parse the way Android's PathParser parses it. Android reads
 * each number greedily, so the compact SVG arc flags browsers accept ("0 01-5.03" for "0 0 1 -5.03")
 * leave an arc short of its 7 values and crash the app when the icon loads. That happened once with
 * the WhatsApp logo; this keeps it from happening again.
 */
class VectorPathTest {
    private val number = Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
    private val arity = mapOf('m' to 2, 'l' to 2, 'h' to 1, 'v' to 1, 'c' to 6, 's' to 4, 'q' to 4, 't' to 2, 'a' to 7, 'z' to 0)

    @Test
    fun everyVectorPathParsesTheWayAndroidDoes() {
        val drawables = File("src/main/res/drawable").listFiles { f -> f.extension == "xml" }.orEmpty()
        assertTrue("no drawables found; run from the app module", drawables.isNotEmpty())
        var paths = 0
        for (file in drawables) {
            for (match in Regex("""android:pathData="([^"]+)"""").findAll(file.readText())) {
                paths++
                for (segment in Regex("""[a-zA-Z][^a-zA-Z]*""").findAll(match.groupValues[1])) {
                    val command = segment.value[0]
                    val values = number.findAll(segment.value.substring(1)).count()
                    val needed = arity[command.lowercaseChar()] ?: fail("${file.name}: unknown command $command") as Int
                    val ok = if (needed == 0) values == 0 else values > 0 && values % needed == 0
                    if (!ok) fail("${file.name}: '$command' has $values values, needs a multiple of $needed near: ${segment.value.take(40)}")
                }
            }
        }
        assertTrue("no paths checked", paths > 0)
    }
}
