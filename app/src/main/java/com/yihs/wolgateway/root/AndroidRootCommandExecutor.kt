package com.yihs.wolgateway.root

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Fixed root commands only. No UI/API supplied command is ever executed. */
class AndroidRootCommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : RootCommandExecutor {
    override fun verify(): Boolean = run("id")?.contains("uid=0") == true

    override fun startGatewayService(): Boolean = run(
        "am", "start-foreground-service",
        "-n", "${context.packageName}/.androidservice.GatewayService",
    ) != null

    override fun stopGatewayService(): Boolean = run(
        "am", "stopservice",
        "-n", "${context.packageName}/.androidservice.GatewayService",
    ) != null

    override fun isGatewayServiceRunning(): Boolean = run(
        "dumpsys", "activity", "services", context.packageName,
    )?.let { output ->
        output.contains(".androidservice.GatewayService") &&
            !output.contains("not found", ignoreCase = true)
    } == true

    override fun isApiReady(port: Int): Boolean = runCatching {
        (URL("http://127.0.0.1:$port/api/v1/health").openConnection() as HttpURLConnection).run {
            connectTimeout = 1_000
            readTimeout = 1_000
            requestMethod = "GET"
            responseCode == HttpURLConnection.HTTP_OK
        }
    }.getOrDefault(false)

    override fun neighborMacs(): Map<String, String> {
        val output = run("ip", "neigh") ?: return emptyMap()
        return output.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val address = parts.firstOrNull() ?: return@mapNotNull null
            val macIndex = parts.indexOf("lladdr") + 1
            val mac = parts.getOrNull(macIndex)?.takeIf { it.matches(MAC) } ?: return@mapNotNull null
            address to mac.uppercase()
        }.toMap()
    }

    override fun resolveNeighborMac(address: String): String? {
        if (!address.matches(IPV4)) return null
        run("ping", "-c", "1", "-W", "1", address)
        val output = run("ip", "neigh", "show", address) ?: return null
        val parts = output.trim().split(Regex("\\s+"))
        val index = parts.indexOf("lladdr") + 1
        return parts.getOrNull(index)?.takeIf { it.matches(MAC) }?.uppercase()
    }

    private fun run(vararg args: String): String? = runCatching {
        val command = args.joinToString(" ") { shellQuote(it) }
        ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
            .also { if (!it.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) it.destroyForcibly() }
            .takeIf { !it.isAlive }
            ?.inputStream?.bufferedReader()?.readText()
    }.getOrNull()

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private companion object {
        const val TIMEOUT_MS = 2_000L
        val MAC = Regex("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}")
        val IPV4 = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
    }
}
