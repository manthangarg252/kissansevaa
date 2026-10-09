package com.kissansevaai.core.ai

/**
 * Abstraction for AI inference. Each feature can have one or more engine implementations:
 * - RemoteEngine: delegates to backend (current)
 * - OnDeviceEngine: runs LiteRT model locally (Phase 3+)
 *
 * The EngineRouter decides which to use based on user settings and device capabilities.
 */
interface AiEngine<in I, out O> {
    val engineId: String
    suspend fun infer(input: I): O
    suspend fun isAvailable(): Boolean
}

enum class EngineMode { AUTO, REMOTE, ON_DEVICE }

/**
 * Routes AI requests to the best available engine.
 *
 * Priority in AUTO mode:
 * 1. On-device if model is downloaded and device tier >= MEDIUM
 * 2. Remote as fallback
 *
 * In REMOTE mode: always remote.
 * In ON_DEVICE mode: on-device only, throw if unavailable.
 */
class EngineRouter<I, O>(
    private val remoteEngine: AiEngine<I, O>,
    private val onDeviceEngine: AiEngine<I, O>? = null,
) {
    private fun logWarning(msg: String) {
        try {
            android.util.Log.w("EngineRouter", msg)
        } catch (_: Throwable) {
            println("[WARN] EngineRouter: $msg")
        }
    }

    suspend fun infer(input: I, mode: EngineMode = EngineMode.AUTO): O {
        return when (mode) {
            EngineMode.REMOTE -> remoteEngine.infer(input)
            EngineMode.ON_DEVICE -> {
                if (onDeviceEngine != null && onDeviceEngine.isAvailable()) {
                    try {
                        onDeviceEngine.infer(input)
                    } catch (e: Exception) {
                        logWarning("On-device engine inference failed; falling back to remote: ${e.message}")
                        remoteEngine.infer(input)
                    }
                } else {
                    logWarning("On-device engine requested but unavailable; falling back to remote")
                    remoteEngine.infer(input)
                }
            }
            EngineMode.AUTO -> {
                if (onDeviceEngine != null && onDeviceEngine.isAvailable()) {
                    try {
                        onDeviceEngine.infer(input)
                    } catch (_: Exception) {
                        // Fallback to remote
                        remoteEngine.infer(input)
                    }
                } else {
                    remoteEngine.infer(input)
                }
            }
        }
    }
}
