package com.kissansevaai.core.ai.voice

import android.util.Log
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.remote.RemoteFieldExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic-first hybrid field extractor.
 * Always executes offline multilingual agricultural rules & crop dictionaries first
 * so forms populate instantaneously even while cloud Gemini quota is exhausted.
 * Optionally queries RemoteFieldExtractor when key fields remain unpopulated and AI is enabled.
 */
@Singleton
class HybridFieldExtractor @Inject constructor(
    private val remoteFieldExtractor: RemoteFieldExtractor,
) : FieldExtractor {

    override suspend fun extract(
        text: String,
        fields: List<String>,
        language: String,
    ): Map<String, String> = extractDetailed(text, fields, language, allowRemoteFallback = true).extractedFields

    suspend fun extractDetailed(
        text: String,
        fields: List<String>,
        language: String,
        allowRemoteFallback: Boolean = true,
    ): VoiceExtractionOutcome {
        val local = AgriculturalVoiceParser.parse(
            transcript = text,
            targetFields = fields,
            language = language,
        )

        val missingFields = fields.filterNot { local.extractedFields.containsKey(it) || local.ambiguousFields.containsKey(it) }
        val shouldTryRemote = allowRemoteFallback &&
            missingFields.isNotEmpty() &&
            text.trim().split(Regex("\\s+")).size >= 3

        if (!shouldTryRemote) {
            return local
        }

        val remoteMap = try {
            remoteFieldExtractor.extract(text, missingFields, language)
        } catch (e: Exception) {
            Log.w("HybridFieldExtractor", "Optional AI extraction unavailable: ${e.message}")
            emptyMap()
        }

        if (remoteMap.isEmpty()) {
            return local
        }

        val merged = LinkedHashMap(local.extractedFields)
        for ((k, v) in remoteMap) {
            if (!merged.containsKey(k) && !local.ambiguousFields.containsKey(k) && v.isNotBlank()) {
                merged[k] = if (k in listOf("crop", "cropName", "cropSearch")) {
                    AgriculturalVoiceParser.normalizeCropName(v) ?: v
                } else {
                    v
                }
            }
        }

        return local.copy(
            extractedFields = merged,
            usedAiFallback = true,
        )
    }
}
