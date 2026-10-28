package com.yihs.wolgateway.tunnel.adapter

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import com.yihs.wolgateway.tunnel.TunnelLog
import java.io.File
import javax.inject.Inject

class CloudflaredBinaryLocator @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {
    fun locate(): File? {
        val candidates = sequenceOf(
            File(context.applicationInfo.nativeLibraryDir, NATIVE_BINARY_NAME),
        ).toList()
        TunnelLog.debug("supported ABIs=${Build.SUPPORTED_ABIS.joinToString()}")
        TunnelLog.debug("nativeLibraryDir=${context.applicationInfo.nativeLibraryDir}")
        candidates.forEach { candidate ->
            TunnelLog.debug(
                "runtime candidate path=${candidate.absolutePath} " +
                    "exists=${candidate.exists()} file=${candidate.isFile} executable=${candidate.canExecute()} " +
                    "size=${candidate.length()}",
            )
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    private companion object {
        const val NATIVE_BINARY_NAME = "libcloudflared.so"
    }
}
