package com.m57.hermescontrol.e2e

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/**
 * On test failure, dumps the device logcat (crash buffer + errors) into the
 * JUnit system-out stream — which Gradle Managed Devices copies into the XML
 * result artifact. Without this, a GMD run that loses the compose hierarchy
 * gives no device-side evidence at all.
 */
class LogcatOnFailureRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } catch (t: Throwable) {
                    runCatching {
                        val out = ProcessBuilder("logcat", "-d", "-t", "2000",
                            "HermesWsClient:D", "PylonE2E:D", "ChatViewModel:D",
                            "AndroidRuntime:E", "CRASH:E", "System.err:W",
                            "ActivityTaskManager:W", "DataStore:E", "*:F")
                            .redirectErrorStream(true)
                            .start()
                            .inputStream.bufferedReader().readText()
                        println("===== LOGCAT ON FAILURE (${description.methodName}) =====")
                        println(out.takeLast(12000))
                        println("===== END LOGCAT =====")
                    }
                    throw t
                }
            }
        }
}
