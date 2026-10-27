package com.yihs.wolgateway.domain.service

import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.StatusCheckResult

interface StatusChecker {
    suspend fun check(ipAddress: String, method: StatusCheckMethod, port: Int): StatusCheckResult
}
