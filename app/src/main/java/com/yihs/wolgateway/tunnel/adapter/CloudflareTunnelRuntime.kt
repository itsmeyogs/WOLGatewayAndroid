package com.yihs.wolgateway.tunnel.adapter

import android.content.Context
import com.yihs.wolgateway.tunnel.TunnelLog
import com.yihs.wolgateway.tunnel.model.TunnelRuntimeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CloudflareTunnelRuntime @Inject constructor(
    @ApplicationContext context: Context,
    private val binaryLocator: CloudflaredBinaryLocator,
) : TunnelRuntime {
    private val tokenDirectory = File(context.cacheDir, "tunnel")
    private var process: Process? = null
    private var outputDrainer: Thread? = null
    private var tokenFile: File? = null
    private var configFile: File? = null

    override suspend fun start(config: TunnelRuntimeConfig) = withContext(Dispatchers.IO) {
        TunnelLog.debug("start requested origin=${config.originAddress}")
        check(process?.isAlive != true) { "Tunnel runtime is already running" }
        val binary = binaryLocator.locate()
            ?: throw IOException("Cloudflare runtime binary not found or not executable")
        require(config.token.isNotBlank()) { "Tunnel token is unavailable" }
        require(config.originAddress.isNotBlank()) { "Tunnel origin is unavailable" }

        tokenDirectory.mkdirs()
        check(tokenDirectory.isDirectory && tokenDirectory.canWrite()) {
            "Tunnel runtime directory is not writable: ${tokenDirectory.absolutePath}"
        }
        val tokenFile = File.createTempFile("cloudflared-token-", ".txt", tokenDirectory)
        val configFile = File(tokenDirectory, "cloudflared-config.yml")
        var activeProcess: Process? = null
        try {
            tokenFile.writeText(config.token, Charsets.UTF_8)
            configFile.writeText(renderCloudflaredConfig(config.originAddress), Charsets.UTF_8)
            this@CloudflareTunnelRuntime.tokenFile = tokenFile
            this@CloudflareTunnelRuntime.configFile = configFile
            TunnelLog.debug(
                "starting runtime binary=${binary.absolutePath} size=${binary.length()} " +
                    "config=${configFile.absolutePath} tokenFile=${tokenFile.absolutePath}",
            )
            val command = cloudflaredCommand(
                binary.absolutePath,
                configFile.absolutePath,
                tokenFile.absolutePath,
            )
            TunnelLog.debug("starting runtime command=${command.joinToString(" ")}")
            val startedProcess = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            activeProcess = startedProcess
            process = activeProcess
            val outputCapture = OutputCapture(config.token)
            outputDrainer = Thread {
                try {
                    startedProcess.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach(outputCapture::record)
                    }
                } catch (error: Exception) {
                    TunnelLog.error("runtime output read failed error=${error.safeMessage()}")
                } finally {
                    TunnelLog.debug(
                        "runtime output stream closed exitCode=${runCatching { startedProcess.exitValue() }.getOrDefault(-1)}",
                    )
                }
            }.apply {
                name = "cloudflared-output-drainer"
                isDaemon = true
                start()
            }
            if (!awaitStartup(startedProcess, outputCapture)) {
                awaitOutputDrain()
                val exitCode = runCatching { startedProcess.exitValue() }.getOrDefault(-1)
                val lastOutput = outputCapture.lastOutput()
                TunnelLog.error(
                    "runtime failed readiness exitCode=$exitCode " +
                        "lastOutput=${lastOutput.ifBlank { "<none>" }}",
                )
                throw IOException(
                    "Cloudflare runtime did not register a tunnel connection exitCode=$exitCode" +
                        lastOutput.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                )
            }
            TunnelLog.debug("runtime tunnel connection registered")
        } catch (error: Exception) {
            TunnelLog.error("runtime start failed error=${error.safeMessage()}")
            if (activeProcess?.isAlive == true) {
                activeProcess.destroy()
                if (!activeProcess.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    activeProcess.destroyForcibly()
                    activeProcess.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
            awaitOutputDrain()
            throw error
        } finally {
            if (process?.isAlive != true) {
                deleteRuntimeFiles(tokenFile, configFile)
                this@CloudflareTunnelRuntime.tokenFile = null
                this@CloudflareTunnelRuntime.configFile = null
                process = null
                outputDrainer = null
            }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        val active = process ?: run {
            TunnelLog.debug("stop requested but runtime is not running")
            return@withContext
        }
        TunnelLog.debug("stopping runtime")
        active.destroy()
        if (!active.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            active.destroyForcibly()
            active.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        awaitOutputDrain()
        process = null
        outputDrainer = null
        deleteRuntimeFiles(tokenFile, configFile)
        tokenFile = null
        configFile = null
        TunnelLog.debug("runtime stopped exitCode=${runCatching { active.exitValue() }.getOrDefault(-1)}")
    }

    override fun isRunning(): Boolean = process?.isAlive == true

    private fun awaitStartup(activeProcess: Process, outputCapture: OutputCapture): Boolean {
        repeat(STARTUP_CHECKS) {
            if (!activeProcess.isAlive) return false
            if (outputCapture.isRegistered()) return true
            Thread.sleep(STARTUP_CHECK_INTERVAL_MS)
        }
        return activeProcess.isAlive && outputCapture.isRegistered()
    }

    private fun awaitOutputDrain() {
        outputDrainer?.join(OUTPUT_DRAIN_TIMEOUT_MS)
        if (outputDrainer?.isAlive == true) {
            TunnelLog.error("runtime output drainer did not finish within timeout")
        } else {
            TunnelLog.debug("runtime output drainer completed")
        }
    }

    private fun deleteRuntimeFiles(tokenFile: File?, configFile: File?) {
        if (tokenFile?.delete() == false) TunnelLog.debug("token file cleanup failed")
        if (configFile?.delete() == false) TunnelLog.debug("config file cleanup failed")
    }

    private fun Throwable.safeMessage(): String = message?.take(300) ?: javaClass.simpleName

    private class OutputCapture(
        private val secret: String,
    ) {
        private val lastLines = ArrayDeque<String>(MAX_CAPTURED_LINES)
        private val registered = CountDownLatch(1)

        @Synchronized
        fun record(line: String) {
            val sanitized = CloudflaredOutputSanitizer.sanitize(line, secret)
            if (lastLines.size == MAX_CAPTURED_LINES) lastLines.removeFirst()
            lastLines.addLast(sanitized)
            if (isCloudflaredRegisteredOutput(sanitized)) registered.countDown()
            logChunks(sanitized)
        }

        fun isRegistered(): Boolean = registered.count == 0L

        @Synchronized
        fun lastOutput(): String = lastLines.joinToString(" | ").takeLast(MAX_DIAGNOSTIC_LENGTH)

        private fun logChunks(line: String) {
            if (line.isEmpty()) {
                TunnelLog.debug("runtime output:")
                return
            }
            line.chunked(LOG_CHUNK_LENGTH).forEachIndexed { index, chunk ->
                TunnelLog.debug("runtime output[$index]: $chunk")
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_SECONDS = 3L
        const val STARTUP_CHECKS = 120
        const val STARTUP_CHECK_INTERVAL_MS = 100L
        const val OUTPUT_DRAIN_TIMEOUT_MS = 2_000L
        const val LOG_CHUNK_LENGTH = 3_500
        const val MAX_CAPTURED_LINES = 50
        const val MAX_DIAGNOSTIC_LENGTH = 4_000
    }
}

internal fun isCloudflaredRegisteredOutput(line: String): Boolean =
    "Registered tunnel connection" in line

internal fun cloudflaredCommand(
    binaryPath: String,
    configPath: String,
    tokenPath: String,
): List<String> = buildList {
    add(binaryPath)
    addAll(listOf("--no-autoupdate", "--config", configPath))
    add("tunnel")
    add("--edge-ip-version")
    add("4")
    STATIC_EDGE_ADDRESSES.forEach {
        add("--edge")
        add(it)
    }
    addAll(listOf("run", "--token-file", tokenPath))
}

private val STATIC_EDGE_ADDRESSES = listOf(
    "198.41.192.167:7844",
    "198.41.192.67:7844",
    "198.41.200.13:7844",
    "198.41.200.193:7844",
)

internal fun renderCloudflaredConfig(originAddress: String): String =
    "ingress:\n" +
        "  - service: http://$originAddress\n"
