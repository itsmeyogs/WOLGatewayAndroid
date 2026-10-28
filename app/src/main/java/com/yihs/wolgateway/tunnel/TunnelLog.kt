package com.yihs.wolgateway.tunnel

import android.util.Log

object TunnelLog {
    private const val TAG = "WolGatewayTunnel"

    fun debug(message: String) {
        runCatching { Log.d(TAG, "[tunnel] $message") }
    }

    fun error(message: String) {
        runCatching { Log.e(TAG, "[tunnel] $message") }
    }
}
