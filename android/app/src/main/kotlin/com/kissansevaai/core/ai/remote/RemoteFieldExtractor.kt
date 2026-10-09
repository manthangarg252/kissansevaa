package com.kissansevaai.core.ai.remote

import android.util.Log
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.model.ChatRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteFieldExtractor @Inject constructor(
    private val api: KissanApi,
    private val json: Json,
) : FieldExtractor {

    override suspend fun extract(
        text: String,
        fields: List<String>,
        language: String
    ): Map<String, String> {
        val fieldListStr = fields.joinToString(", ")
        val prompt = """
            You are a form data extractor.
            Extract values for these fields: [$fieldListStr]
            From this spoken text: "$text"
            Language context: $language
            Rules:
            - If a field is not mentioned, omit it or set to null.
            - If soil type: normalize to one of: Sandy, Loamy, Clay, Silty.
            - Return ONLY valid JSON with fields as keys and extracted strings as values.
        """.trimIndent()

        return try {
            val response = api.chat(ChatRequest(message = prompt, mode = "general", language = language))
            val raw = response.response.trim()
            val jsonString = if (raw.contains("{") && raw.contains("}")) {
                raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
            } else {
                raw
            }

            val parsedObj = json.parseToJsonElement(jsonString).jsonObject
            val resultMap = mutableMapOf<String, String>()
            fields.forEach { key ->
                val elem = parsedObj[key]
                if (elem != null && !elem.toString().equals("null", ignoreCase = true)) {
                    val value = try {
                        elem.jsonPrimitive.content
                    } catch (_: Exception) {
                        elem.toString().trim('"')
                    }
                    if (value.isNotBlank() && !value.equals("null", ignoreCase = true)) {
                        resultMap[key] = value
                    }
                }
            }
            resultMap
        } catch (e: Exception) {
            Log.w("RemoteFieldExtractor", "Failed to extract fields via AI: ${e.message}")
            emptyMap()
        }
    }
}
