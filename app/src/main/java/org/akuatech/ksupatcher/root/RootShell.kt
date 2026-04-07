package org.akuatech.ksupatcher.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter

/**
 * Thin wrapper around a persistent su process for structured root command execution.
 * Special thanks / Credit to JoshuaDoes for the initial stream-based implementation.
 */
object RootShell {

    private var suProcess: Process? = null
    private var suStdin: BufferedWriter? = null
    private var suStdout: BufferedReader? = null

    @Synchronized
    private fun runRootBlock(input: String): Triple<Boolean, String, String> {
        if (suProcess == null) {
            try {
                suProcess = Runtime.getRuntime().exec("su")
                suStdin = suProcess!!.outputStream.bufferedWriter()
                suStdout = suProcess!!.inputStream.bufferedReader()
            } catch (e: Exception) {
                e.printStackTrace()
                return Triple(false, "", e.toString())
            }
        }

        if (input.isBlank()) {
            return Triple(false, "", "")
        }

        val marker = "__CMD_DONE_${System.nanoTime()}__"

        try {
            suStdin!!.write("( set -e")
            suStdin!!.newLine()
            suStdin!!.write(input)
            suStdin!!.newLine()
            suStdin!!.write(") 2>&1")
            suStdin!!.newLine()
            suStdin!!.write("rc=$?")
            suStdin!!.newLine()
            suStdin!!.write("printf '%s:%s\\n' '$marker' \"${'$'}rc\"")
            suStdin!!.newLine()
            suStdin!!.flush()

            val outBuf = StringBuilder()
            var exitCode: Int? = null

            while (true) {
                val line = suStdout!!.readLine() ?: break
                if (line.startsWith("$marker:")) {
                    exitCode = line.substringAfter(':').toIntOrNull()
                    break
                }
                outBuf.appendLine(line)
            }

            val output = outBuf.toString().trimEnd()
            val code = exitCode ?: return Triple(false, output, "Shell execution failed: missing completion marker")
            if (code != 0) {
                val error = buildString {
                    append("Shell command exited with code ")
                    append(code)
                    if (output.isNotBlank()) {
                        append('\n')
                        append(output)
                    }
                }
                return Triple(false, output, error)
            }

            return Triple(true, output, "")
        } catch (e: Exception) {
            e.printStackTrace()
            suProcess?.destroy()
            suProcess = null
            suStdin = null
            suStdout = null
            return Triple(false, "", e.toString())
        }
    }

    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        val (success, out, _) = runRootBlock("id")
        success && out.contains("uid=0(root)")
    }

    suspend fun run(vararg cmds: String): String = withContext(Dispatchers.IO) {
        val cmdStr = cmds.joinToString("\n")
        val (success, out, err) = runRootBlock(cmdStr)
        if (!success) {
            error("Shell execution failed. Error: $err")
        }
        out
    }

    suspend fun getProp(key: String): String? = withContext(Dispatchers.IO) {
        run("getprop $key").trim().takeIf { it.isNotEmpty() }
    }
}
