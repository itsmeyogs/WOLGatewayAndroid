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

    override fun thermalSensors(): List<RootThermalReading> = (0 until MAX_THERMAL_ZONES).mapNotNull { zone ->
        val base = "/sys/class/thermal/thermal_zone$zone"
        val name = runThermal("cat", "$base/type")?.trim()?.let(::safeThermalName) ?: return@mapNotNull null
        val raw = runThermal("cat", "$base/temp")?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
        val celsius = if (raw > 200) raw / 1_000.0 else raw
        celsius.takeIf { it in MIN_THERMAL_C..MAX_THERMAL_C }?.let { RootThermalReading(name, it) }
    }

    private fun safeThermalName(value: String): String? = when {
        value.contains("batt", ignoreCase = true) -> "battery"
        value.contains("cpu", ignoreCase = true) -> "cpu"
        value.contains("gpu", ignoreCase = true) -> "gpu"
        value.contains("soc", ignoreCase = true) -> "soc"
        value.contains("skin", ignoreCase = true) -> "skin"
        value.contains("modem", ignoreCase = true) -> "modem"
        else -> "unknown"
    }

    private fun run(vararg args: String): String? = runCatching {
        runProcess(TIMEOUT_MS, *args)
    }.getOrNull()

    private fun runThermal(vararg args: String): String? = runCatching {
        runProcess(THERMAL_TIMEOUT_MS, *args)
    }.getOrNull()

    private fun runProcess(timeoutMs: Long, vararg args: String): String? {
        val command = args.joinToString(" ") { shellQuote(it) }
        return ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
            .also { if (!it.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) it.destroyForcibly() }
            .takeIf { !it.isAlive }
            ?.inputStream?.bufferedReader()?.readText()
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private companion object {
        const val TIMEOUT_MS = 2_000L
        const val THERMAL_TIMEOUT_MS = 250L
        const val MAX_THERMAL_ZONES = 16
        const val MIN_THERMAL_C = -40.0
        const val MAX_THERMAL_C = 150.0
        val MAC = Regex("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}")
        val IPV4 = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
    }
}
