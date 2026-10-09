package com.kissansevaai.core.util

import android.app.ActivityManager
import android.content.Context

enum class DeviceTier {
    LOW,      // < 3 GB RAM: Remote AI engine only
    MEDIUM,   // 3 - 6 GB RAM: Remote + on-device STT
    HIGH      // > 6 GB RAM: Full on-device model capabilities (LiteRT, local LLM)
}

object DeviceTierUtil {

    /**
     * Determines the device capability tier based on total RAM.
     * Explicitly uses ActivityManager.MemoryInfo.totalMem (NOT memoryClass).
     */
    fun getDeviceTier(context: Context): DeviceTier {
        val totalMemBytes = getTotalMemoryBytes(context)
        val totalMemGb = totalMemBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return when {
            totalMemGb < 3.0 -> DeviceTier.LOW
            totalMemGb <= 6.0 -> DeviceTier.MEDIUM
            else -> DeviceTier.HIGH
        }
    }

    /**
     * Retrieves total device RAM in bytes via ActivityManager.MemoryInfo.totalMem.
     */
    fun getTotalMemoryBytes(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }
}
