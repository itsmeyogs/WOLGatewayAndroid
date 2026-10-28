package com.yihs.wolgateway.tunnel

import android.util.Log

object TunnelLog {
    private const val TAG = "WolGatewayTunnel"

    fun debug(message: String) {
        Log.d(TAG, "[tunnel] $message")
    }

    fun error(message: String) {
        Log.e(TAG, "[tunnel] $message")
    }
}
