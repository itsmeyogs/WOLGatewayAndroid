package com.yihs.wolgateway.root

import android.os.Build
import java.io.File
import javax.inject.Inject

class AndroidRootCapabilityDetector @Inject constructor() : RootCapabilityDetector {
    override fun detect(): RootCapability = runCatching {
        if (Build.TAGS?.contains("test-keys") == true || SU_PATHS.any { File(it).exists() }) {
            RootCapability.AVAILABLE
        } else RootCapability.UNAVAILABLE
    }.getOrElse { RootCapability.UNKNOWN }

    private companion object {
        val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su")
    }
}
