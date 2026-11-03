package com.yihs.wolgateway.root

import android.os.Build
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AndroidRootCapabilityDetector @Inject constructor() : RootCapabilityDetector {
    override fun detect(): RootCapability = runCatching {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching RootCapability.UNKNOWN
        }
        val output = process.inputStream.bufferedReader().readText()
        if (process.exitValue() == 0 && output.contains("uid=0")) {
            RootCapability.AVAILABLE
        } else RootCapability.UNAVAILABLE
    }.getOrElse { RootCapability.UNKNOWN }
}
