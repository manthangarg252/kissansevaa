package com.kissansevaai.core.ai

/**
 * Extracts structured form fields from spoken transcript text.
 */
interface FieldExtractor {
    suspend fun extract(
        text: String,
        fields: List<String>,
        language: String
    ): Map<String, String>
}
