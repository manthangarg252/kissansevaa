package com.kissansevaai.core.ai.voice

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class VoiceExtractionOutcome(
    val rawTranscript: String,
    val normalizedTranscript: String,
    val extractedFields: Map<String, String> = emptyMap(),
    val ambiguousFields: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val usedAiFallback: Boolean = false,
) {
    val hasAmbiguities: Boolean get() = ambiguousFields.isNotEmpty()
}

/**
 * Deterministic multilingual farmer speech parser covering Hindi (Devanagari & Romanized),
 * English, Punjabi, Marathi, Bhojpuri, and Indian code-mixed sentences.
 * Never guesses ambiguous dates, regional area units, dosages, or animal temperatures.
 */
object AgriculturalVoiceParser {

    private data class CropEntry(val canonical: String, val synonyms: List<String>)

    private val CROP_DICTIONARY = listOf(
        CropEntry("Wheat", listOf("wheat", "gehun", "gehu", "gehoo", "gahu", "kanak", "गेहूं", "गेहूँ", "गहूं", "गहू", "कनक", "ਕਣਕ")),
        CropEntry("Paddy", listOf("paddy", "dhaan", "dhan", "jhona", "jhonha", "धान", "झोना", "ਝੋਨਾ")),
        CropEntry("Rice", listOf("rice", "chawal", "chaawal", "bhat", "tandul", "चावल", "भात", "तांदूळ", "ਚੌਲ")),
        CropEntry("Maize", listOf("maize", "corn", "makka", "makai", "makki", "bhutta", "मक्का", "मकई", "भुट्टा", "ਮੱਕੀ")),
        CropEntry("Cotton", listOf("cotton", "kapas", "kapaas", "kapus", "kapah", "narma", "rui", "कपास", "कापूस", "नरमा", "ਕਪਾਹ")),
        CropEntry("Potato", listOf("potato", "potatoes", "aloo", "alu", "batata", "आलू", "आलु", "बटाटा", "ਆਲੂ")),
        CropEntry("Tomato", listOf("tomato", "tomatoes", "tamatar", "tamator", "tamata", "टमाटर", "टमाटा", "ਟਮਾਟਰ")),
        CropEntry("Onion", listOf("onion", "onions", "pyaj", "pyaz", "pyaaz", "kanda", "प्याज", "कांदा", "ਪਿਆਜ਼")),
        CropEntry("Soybean", listOf("soybean", "soyabean", "soya", "सोयाबीन", "सोया", "ਸੋਇਆਬੀਨ")),
        CropEntry("Sugarcane", listOf("sugarcane", "ganna", "oos", "ganna", "गन्ना", "ऊस", "ਗੰਨਾ")),
        CropEntry("Mustard", listOf("mustard", "sarson", "sarso", "rai", "mohri", "सरसों", "सरसो", "मोहरी", "ਸਰ੍ਹੋਂ")),
        CropEntry("Gram", listOf("gram", "chickpea", "chana", "harbhara", "चना", "हरभरा", "ਛੋਲੇ")),
        CropEntry("Groundnut", listOf("groundnut", "peanut", "moongfali", "mungfali", "shengdana", "मूंगफली", "शेंगदाणा", "ਮੂੰਗਫਲੀ")),
        CropEntry("Millet", listOf("millet", "bajra", "bajri", "jowar", "jawari", "ragi", "nachni", "बाजरा", "बाजरी", "ज्वार", "ज्वारी", "रागी", "ਬਾਜਰਾ")),
        CropEntry("Pigeon Pea", listOf("pigeon pea", "tur", "toor", "arhar", "तूर", "अरहर")),
        CropEntry("Chilli", listOf("chilli", "chili", "mirch", "mirchi", "मिर्च", "मिर्ची", "ਮਿਰਚ")),
        CropEntry("Brinjal", listOf("brinjal", "eggplant", "baingan", "baigan", "vangi", "बैंगन", "वांगी", "ਬੈਂਗਣ")),
        CropEntry("Garlic", listOf("garlic", "lahsun", "lasan", "लहसुन", "लसूण", "ਲਸਣ")),
        CropEntry("Apple", listOf("apple", "seb", "safarchand", "सेब", "सफरचंद", "ਸੇਬ")),
        CropEntry("Banana", listOf("banana", "kela", "kele", "केला", "केळ", "केळी", "ਕੇਲਾ")),
        CropEntry("Mango", listOf("mango", "aam", "amba", "आम", "आंबा", "ਅੰਬ")),
        CropEntry("Orange", listOf("orange", "santra", "santre", "संतरा", "संत्रे", "ਸੰਤਰਾ")),
        CropEntry("Barley", listOf("barley", "jau", "jav", "जौ", "जव")),
    )

    private val SOIL_DICTIONARY = listOf(
        "Black" to listOf("black", "black cotton", "kali", "kaali", "regur", "काली", "काळी", "रेगुर"),
        "Loamy" to listOf("loamy", "loam", "alluvial", "domat", "doomat", "jalodh", "दोमट", "जलोढ़"),
        "Sandy" to listOf("sandy", "retili", "balui", "ret", "रेतीली", "बलुई", "रेताड"),
        "Clay" to listOf("clay", "clayey", "chikni", "चिकनी", "चिकण"),
        "Red Loam" to listOf("red", "red loam", "lal", "laal", "तांबडी", "लाल"),
        "Silty" to listOf("silty", "silt", "gaad", "गाद"),
    )

    private val IRRIGATION_DICTIONARY = listOf(
        "Drip" to listOf("drip", "tapak", "thibak", "टपक", "ठिबक"),
        "Sprinkler" to listOf("sprinkler", "fuhara", "favvara", "tusar", "फव्वारा", "तुषार"),
        "Canal" to listOf("canal", "nahar", "नहर", "कालवा"),
        "Tubewell / Borewell" to listOf("tubewell", "tube well", "borewell", "bore well", "borwell", "nalcoop", "नलकूप", "बोरवेल", "ट्यूबवेल"),
        "Rainfed" to listOf("rainfed", "rain fed", "barani", "barsat", "barish", "बारिश", "बरसाती", "जिरायती"),
    )

    private val ANIMAL_DICTIONARY = listOf(
        "Buffalo" to listOf("buffalo", "bhains", "bhainsa", "mhais", "भैंस", "म्हैस", "ਮੱਝ"),
        "Cattle" to listOf("cattle", "cow", "gai", "gaay", "gaye", "ox", "bull", "calf", "bachda", "गाय", "गौ", "बैल", "ਗਾਂ"),
        "Goat" to listOf("goat", "bakri", "bakra", "sheli", "बकरी", "बकरा", "शेळी", "ਬੱਕਰੀ"),
        "Sheep" to listOf("sheep", "bhed", "mendhi", "भेड़", "मेंढी", "ਭੇਡ"),
        "Poultry" to listOf("poultry", "chicken", "hen", "murgi", "murga", "मुर्गी", "मुर्गा", "ਕੁੱਕੜ"),
    )

    private val STATE_DICTIONARY = listOf(
        "Maharashtra" to listOf("maharashtra", "महाराष्ट्र"),
        "Punjab" to listOf("punjab", "ਪੰਜਾਬ", "पंजाब"),
        "Gujarat" to listOf("gujarat", "गुजरात"),
        "Madhya Pradesh" to listOf("madhya pradesh", "mp", "मध्य प्रदेश"),
        "Uttar Pradesh" to listOf("uttar pradesh", "up", "उत्तर प्रदेश"),
        "Bihar" to listOf("bihar", "बिहार"),
        "Haryana" to listOf("haryana", "हरियाणा"),
        "Rajasthan" to listOf("rajasthan", "राजस्थान"),
        "Karnataka" to listOf("karnataka", "कर्नाटक"),
        "Telangana" to listOf("telangana", "तेलंगाना"),
        "Andhra Pradesh" to listOf("andhra pradesh", "andhra", "आंध्र प्रदेश"),
        "Tamil Nadu" to listOf("tamil nadu", "तमिलनाडु"),
        "West Bengal" to listOf("west bengal", "bengal", "पश्चिम बंगाल"),
        "Delhi" to listOf("delhi", "new delhi", "दिल्ली", "नई दिल्ली"),
        "Chhattisgarh" to listOf("chhattisgarh", "छत्तीसगढ़"),
        "Odisha" to listOf("odisha", "orissa", "ओडिशा"),
    )

    private val NUMBER_WORDS = mapOf(
        "aadha" to "0.5", "adha" to "0.5", "half" to "0.5", "आधा" to "0.5", "अर्धा" to "0.5",
        "dedh" to "1.5", "डेढ़" to "1.5",
        "dhai" to "2.5", "ढाई" to "2.5", "अडीच" to "2.5",
        "ek" to "1", "one" to "1", "ik" to "1", "एक" to "1", "ਇੱਕ" to "1",
        "do" to "2", "two" to "2", "don" to "2", "दो" to "2", "दोन" to "2", "ਦੋ" to "2",
        "teen" to "3", "tin" to "3", "three" to "3", "तीन" to "3", "ਤਿੰਨ" to "3",
        "char" to "4", "chaar" to "4", "four" to "4", "चार" to "4", "ਚਾਰ" to "4",
        "paanch" to "5", "panch" to "5", "five" to "5", "पांच" to "5", "पाँच" to "5", "ਪੰਜ" to "5",
        "chhah" to "6", "chhe" to "6", "six" to "6", "छह" to "6", "सहा" to "6",
        "saat" to "7", "seven" to "7", "सात" to "7",
        "aath" to "8", "eight" to "8", "आठ" to "8",
        "nau" to "9", "nine" to "9", "नौ" to "9",
        "das" to "10", "ten" to "10", "दस" to "10",
        "barah" to "12", "twelve" to "12", "बारह" to "12",
        "pandrah" to "15", "fifteen" to "15", "पंद्रह" to "15",
        "bees" to "20", "twenty" to "20", "बीस" to "20",
    )

    private val MONTHS = mapOf(
        "january" to 1, "jan" to 1, "जनवरी" to 1,
        "february" to 2, "feb" to 2, "फरवरी" to 2,
        "march" to 3, "mar" to 3, "मार्च" to 3,
        "april" to 4, "apr" to 4, "अप्रैल" to 4,
        "may" to 5, "मई" to 5,
        "june" to 6, "jun" to 6, "जून" to 6,
        "july" to 7, "jul" to 7, "जुलाई" to 7,
        "august" to 8, "aug" to 8, "अगस्त" to 8,
        "september" to 9, "sep" to 9, "sept" to 9, "सितंबर" to 9,
        "october" to 10, "oct" to 10, "अक्टूबर" to 10,
        "november" to 11, "nov" to 11, "नवंबर" to 11, "नवम्बर" to 11,
        "december" to 12, "dec" to 12, "दिसंबर" to 12, "दिसम्बर" to 12,
    )

    fun normalizeDigitsAndWords(raw: String): String {
        val devanagariDigits = mapOf(
            '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
            '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9',
            '੦' to '0', '੧' to '1', '੨' to '2', '੩' to '3', '੪' to '4',
            '੫' to '5', '੬' to '6', '੭' to '7', '੮' to '8', '੯' to '9',
        )
        val asciiDigits = raw.map { devanagariDigits[it] ?: it }.joinToString("")
        var out = asciiDigits
        NUMBER_WORDS.forEach { (word, digit) ->
            val regex = Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])")
            out = regex.replace(out, digit)
        }
        return out
    }

    fun normalizeCropName(raw: String): String? {
        val cleaned = raw.lowercase(Locale.ROOT).trim()
        if (cleaned.isBlank()) return null
        for (entry in CROP_DICTIONARY) {
            for (syn in entry.synonyms) {
                val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(syn.lowercase(Locale.ROOT))}(?![\\p{L}\\p{N}])")
                if (pattern.containsMatchIn(cleaned)) {
                    return entry.canonical
                }
            }
        }
        return null
    }

    fun findAllCrops(raw: String): List<String> {
        val cleaned = raw.lowercase(Locale.ROOT)
        val hits = linkedSetOf<String>()
        for (entry in CROP_DICTIONARY) {
            for (syn in entry.synonyms) {
                val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(syn.lowercase(Locale.ROOT))}(?![\\p{L}\\p{N}])")
                if (pattern.containsMatchIn(cleaned)) {
                    hits += entry.canonical
                    break
                }
            }
        }
        return hits.toList()
    }

    fun normalizeTranscriptCropTerms(raw: String): String {
        var result = normalizeDigitsAndWords(raw)
        for (entry in CROP_DICTIONARY) {
            for (syn in entry.synonyms) {
                if (syn.equals(entry.canonical, ignoreCase = true)) continue
                val regex = Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(syn)}(?![\\p{L}\\p{N}])")
                if (regex.containsMatchIn(result)) {
                    result = regex.replace(result, entry.canonical)
                }
            }
        }
        return result
    }

    fun parse(
        transcript: String,
        targetFields: List<String>,
        language: String = "en",
        referenceDate: LocalDate = LocalDate.now(),
    ): VoiceExtractionOutcome {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) {
            return VoiceExtractionOutcome(trimmed, "", emptyMap(), emptyMap(), listOf("Empty transcript"))
        }

        val normalizedDigits = normalizeDigitsAndWords(trimmed)
        val lower = normalizedDigits.lowercase(Locale.ROOT)
        val extracted = linkedMapOf<String, String>()
        val ambiguous = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        fun wants(vararg keys: String): Boolean = wantsField(targetFields, *keys)

        // 1. Crops
        if (wants("crop", "cropName", "cropSearch", "primaryCrops")) {
            val crops = findAllCrops(normalizedDigits)
            if (crops.isNotEmpty()) {
                val primary = crops.first()
                if (wants("cropName")) extracted["cropName"] = primary
                if (wants("crop")) extracted["crop"] = primary
                if (wants("cropSearch")) extracted["cropSearch"] = primary
                if (wants("primaryCrops")) extracted["primaryCrops"] = crops.joinToString(", ")
            }
        }

        // 2. Sowing date (strictly unambiguous)
        if (wants("sowingDate", "startDate")) {
            val dateRes = parseSowingDate(normalizedDigits, referenceDate)
            if (dateRes.isoDate != null) {
                extracted["sowingDate"] = dateRes.isoDate
            } else if (dateRes.ambiguousReason != null) {
                ambiguous["sowingDate"] = dateRes.ambiguousReason
                warnings += dateRes.ambiguousReason
            }
        }

        // 3. Field area / size
        if (wants("farmSize", "farmSizeAcres", "landSize", "fieldArea")) {
            val areaRes = parseAreaAcres(normalizedDigits, singleFieldMode = targetFields.size == 1)
            if (areaRes.acresText != null) {
                val key = targetFields.firstOrNull { it in listOf("farmSize", "farmSizeAcres", "landSize", "fieldArea") } ?: "farmSize"
                extracted[key] = areaRes.acresText
                if (wants("farmSize")) extracted["farmSize"] = areaRes.acresText
                if (wants("farmSizeAcres")) extracted["farmSizeAcres"] = areaRes.acresText
            } else if (areaRes.ambiguousReason != null) {
                val key = targetFields.firstOrNull { it in listOf("farmSize", "farmSizeAcres", "landSize", "fieldArea") } ?: "farmSize"
                ambiguous[key] = areaRes.ambiguousReason
                warnings += areaRes.ambiguousReason
            }
        }

        // 4. Soil type & Irrigation
        if (wants("soilType", "soil")) {
            val soil = matchDictionary(lower, SOIL_DICTIONARY)
            if (soil != null) {
                extracted["soilType"] = soil
            }
        }
        if (wants("irrigationType", "irrigation", "landType")) {
            val irrig = matchDictionary(lower, IRRIGATION_DICTIONARY)
            if (irrig != null) {
                if (wants("irrigationType")) extracted["irrigationType"] = irrig
                if (wants("irrigation")) extracted["irrigation"] = irrig
                if (wants("landType") && !extracted.containsKey("landType")) extracted["landType"] = irrig
            } else if (wants("landType")) {
                when {
                    listOf("leased", "lease", "batai", "theka", "किराए", "बटाई").any { lower.contains(it) } -> extracted["landType"] = "Leased"
                    listOf("owned", "apni", "khud", "swayam", "मालिकाना", "खुद").any { lower.contains(it) } -> extracted["landType"] = "Owned"
                }
            }
        }

        // 5. Livestock intake
        if (wants("animalType", "animalAge", "symptoms", "symptomsNotes", "symptomDuration", "duration", "feverStatus", "appetiteStatus", "milkYieldChange", "lesionsOrUlcers")) {
            parseLivestock(targetFields, lower, extracted, ambiguous, warnings)
        }

        // 6. Crop disease symptoms & custom task
        if (wants("symptomDescription", "symptomsDescription")) {
            val symp = parseCropSymptoms(trimmed)
            if (symp != null) {
                val k = targetFields.firstOrNull { it.equals("symptomDescription", ignoreCase = true) } ?: "symptomDescription"
                extracted[k] = symp
            }
        }
        if (wants("customTask")) {
            val task = parseCustomTask(trimmed)
            if (task != null) extracted["customTask"] = task
        }

        // 7. Geographic & Profile fields (State, District, Village, Farmer Name, Farm Name)
        if (wants("state", "district", "location", "marketSearch", "village", "farmerName", "name", "farmName")) {
            parseProfileAndLocationFields(trimmed, lower, targetFields, extracted)
        }

        // 8. Schemes query or Chat query
        if (wants("searchQuery", "query")) {
            val crops = findAllCrops(normalizedDigits)
            val cleaned = if (crops.isNotEmpty() && lower.split(Regex("\\s+")).size <= 3) crops.first() else trimmed
            if (wants("searchQuery")) extracted["searchQuery"] = cleaned
            if (wants("query")) extracted["query"] = trimmed
        }

        // Single-field fallback if farmer dictated a direct short value for a single text field without hitting keywords
        if (targetFields.size == 1 && extracted.isEmpty() && ambiguous.isEmpty()) {
            val onlyField = targetFields.first()
            if (onlyField in listOf("farmerName", "name", "farmName", "village", "district", "location", "marketSearch", "searchQuery", "customTask", "symptomsNotes", "symptomDescription", "animalAge", "cropName", "cropSearch")) {
                extracted[onlyField] = normalizeCropName(trimmed) ?: trimmed
            }
        }

        // Guard against uncertain medical dosage or temperature claims
        if (Regex("(?i)\\b(mg|ml|tablet|capsule|dose|dosage|खिला|दवा|इंजेक्शन)\\b").containsMatchIn(lower) && wants("symptomsNotes", "animalAge", "dosage")) {
            warnings += "Dosage values are never auto-filled from speech; please confirm treatment only with a veterinarian."
        }
        if (Regex("(?i)\\b(\\d{2,3}(\\.\\d)?)\\s*(degree|fahrenheit|celsius|f\\b|c\\b|डिग्री)").containsMatchIn(lower) && wants("symptomsNotes")) {
            ambiguous["feverStatus"] = "Spoken body temperature should be verified with a thermometer; marked fever for manual review."
        }

        return VoiceExtractionOutcome(
            rawTranscript = trimmed,
            normalizedTranscript = normalizeTranscriptCropTerms(trimmed),
            extractedFields = extracted,
            ambiguousFields = ambiguous,
            warnings = warnings.distinct(),
            usedAiFallback = false,
        )
    }

    private data class DateParseResult(val isoDate: String? = null, val ambiguousReason: String? = null)

    private fun parseSowingDate(text: String, referenceDate: LocalDate): DateParseResult {
        val lower = text.lowercase(Locale.ROOT)
        val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // Explicit ISO YYYY-MM-DD
        Regex("""\b(20\d{2})-(0?[1-9]|1[0-2])-(0?[1-9]|[12]\d|3[01])\b""").find(text)?.let { m ->
            return safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), isoFormatter)
        }

        // Explicit DD/MM/YYYY or DD-MM-YYYY
        Regex("""\b(0?[1-9]|[12]\d|3[01])[/-](0?[1-9]|1[0-2])[/-](20\d{2})\b""").find(text)?.let { m ->
            return safeDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt(), isoFormatter)
        }

        // Explicit "10 November 2026" or "10 नवंबर 2026"
        val monthPattern = MONTHS.keys.joinToString("|") { Regex.escape(it) }
        val dayMonthYearRegex = Regex("""(?i)\b(0?[1-9]|[12]\d|3[01])(?:st|nd|rd|th)?\s+($monthPattern),?\s+(20\d{2})\b""")
        dayMonthYearRegex.find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS[m.groupValues[2]] ?: return DateParseResult(ambiguousReason = "Unrecognized month in sowing date.")
            val year = m.groupValues[3].toInt()
            return safeDate(year, month, day, isoFormatter)
        }

        // Day + Month without year: resolve ONLY when unambiguously in current recent past/today window (0..90 days before referenceDate)
        val dayMonthOnlyRegex = Regex("""(?i)\b(0?[1-9]|[12]\d|3[01])(?:st|nd|rd|th)?\s+($monthPattern)\b""")
        dayMonthOnlyRegex.find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS[m.groupValues[2]] ?: return DateParseResult(ambiguousReason = "Unrecognized month in sowing date.")
            val candidate = runCatching { LocalDate.of(referenceDate.year, month, day) }.getOrNull()
                ?: return DateParseResult(ambiguousReason = "Invalid day/month combination in speech; please confirm sowing date.")
            val daysAgo = ChronoUnit.DAYS.between(candidate, referenceDate)
            return if (daysAgo in 0..90) {
                DateParseResult(isoDate = candidate.format(isoFormatter))
            } else {
                DateParseResult(
                    ambiguousReason = "Sowing date '${m.value}' has no explicit year and is outside current season; please confirm exact date."
                )
            }
        }

        // Month only without day ("November me" / "in October") or relative ambiguous ("kal" / "pichle hafte")
        val vagueMonthRegex = Regex("""(?i)\b($monthPattern)\b""")
        if (vagueMonthRegex.containsMatchIn(lower) || Regex("""(?i)\b(kal|pichle|last week|few days ago|परसों|कल|पिछले)\b""").containsMatchIn(lower)) {
            return DateParseResult(ambiguousReason = "Relative or incomplete sowing date mentioned; please confirm exact sowing date.")
        }

        // "aaj / today" with explicit sowing intent
        if (Regex("""(?i)\b(today|aaj|आज)\b""").containsMatchIn(lower) &&
            Regex("""(?i)\b(sow|sowed|sowing|boya|boyai|buvai|बुवाई|बोया)\b""").containsMatchIn(lower)
        ) {
            return DateParseResult(isoDate = referenceDate.format(isoFormatter))
        }

        return DateParseResult()
    }

    private fun safeDate(year: Int, month: Int, day: Int, fmt: DateTimeFormatter): DateParseResult {
        val d = runCatching { LocalDate.of(year, month, day) }.getOrNull()
            ?: return DateParseResult(ambiguousReason = "Invalid calendar date $day/$month/$year; please confirm.")
        return DateParseResult(isoDate = d.format(fmt))
    }

    private data class AreaParseResult(val acresText: String? = null, val ambiguousReason: String? = null)

    private fun parseAreaAcres(text: String, singleFieldMode: Boolean): AreaParseResult {
        val lower = text.lowercase(Locale.ROOT)
        // Regional non-standardized area units -> ask farmer to confirm equivalent acres!
        Regex("""(\d+(?:\.\d+)?)\s*(bigha|beegha|katha|kattha|guntha|kanal|marla|cent|बीघा|कट्ठा|गुंठा)""").find(lower)?.let { m ->
            return AreaParseResult(
                ambiguousReason = "Regional area '${m.value}' varies by state — please confirm equivalent acres."
            )
        }

        // Acres (explicit)
        Regex("""(\d+(?:\.\d+)?)\s*(acre|acres|ac|ekad|ekar|एकड़|एकड|ਏਕੜ)""").find(lower)?.let { m ->
            return AreaParseResult(acresText = cleanDecimal(m.groupValues[1].toDouble()))
        }

        // Hectares -> convert deterministically (1 ha = 2.471 acres)
        Regex("""(\d+(?:\.\d+)?)\s*(hectare|hectares|ha|हेक्टेयर|हेक्टर)""").find(lower)?.let { m ->
            val acres = m.groupValues[1].toDouble() * 2.471
            return AreaParseResult(acresText = cleanDecimal(acres))
        }

        if (singleFieldMode) {
            Regex("""^\s*(\d+(?:\.\d+)?)\s*$""").find(lower)?.let { m ->
                return AreaParseResult(acresText = cleanDecimal(m.groupValues[1].toDouble()))
            }
        }

        // Ambiguous mention of land without number/unit
        if (Regex("""(?i)\b(zameen|khet|land|area|ज़मीन|जमीन|खेत)\b""").containsMatchIn(lower)) {
            Regex("""(\d+(?:\.\d+)?)\s*(?:zameen|khet|land)""").find(lower)?.let { m ->
                return AreaParseResult(ambiguousReason = "Area '${m.groupValues[1]}' has no unit (acre/hectare); please confirm.")
            }
        }

        return AreaParseResult()
    }

    private fun cleanDecimal(v: Double): String {
        val rounded = Math.round(v * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    private fun parseLivestock(
        targetFields: List<String>,
        lower: String,
        extracted: MutableMap<String, String>,
        ambiguous: MutableMap<String, String>,
        warnings: MutableList<String>,
    ) {
        fun wants(vararg keys: String): Boolean = wantsField(targetFields, *keys)

        if (wants("animalType")) {
            val animal = matchDictionary(lower, ANIMAL_DICTIONARY)
            if (animal != null) extracted["animalType"] = animal
        }

        if (wants("symptomDuration", "duration")) {
            val daysMatch = Regex("""(\d+)\s*(day|days|din|dino|दिन|ਦਿਨ)""").find(lower)
            val weeksMatch = Regex("""(\d+)\s*(week|weeks|hafta|hafte|सप्ताह|हफ्ते)""").find(lower)
            when {
                daysMatch != null -> {
                    val n = daysMatch.groupValues[1].toIntOrNull()
                    if (n != null) {
                        extracted["symptomDuration"] = if (n == 1) "1 day" else "$n days"
                        extracted["durationChip"] = when {
                            n < 1 -> "< 24h"
                            n <= 3 -> "1-3 days"
                            else -> "3+ days"
                        }
                    }
                }
                weeksMatch != null -> {
                    val n = weeksMatch.groupValues[1].toIntOrNull()
                    if (n != null) {
                        extracted["symptomDuration"] = if (n == 1) "1 week" else "$n weeks"
                        extracted["durationChip"] = "3+ days"
                    }
                }
                Regex("""(?i)\b(hours|ghante|aaj subah|subah se|today|घंटे|सुबह से)\b""").containsMatchIn(lower) -> {
                    extracted["symptomDuration"] = "< 24h"
                    extracted["durationChip"] = "< 24h"
                }
                Regex("""(?i)\b(kuch din|many days|kaafi din|कुछ दिन|कई दिन)\b""").containsMatchIn(lower) -> {
                    val reason = "Vague duration ('kuch/kai din'); please confirm how many days."
                    ambiguous["symptomDuration"] = reason
                    warnings += reason
                }
            }
        }

        if (wants("animalAge")) {
            val yearMatch = Regex("""(\d+(?:\.\d+)?)\s*(year|years|yr|yrs|saal|sal|baras|साल|वर्ष)""").find(lower)
            val monthMatch = Regex("""(\d+)\s*(month|months|mahine|mahina|महीने|माह)""").find(lower)
            when {
                yearMatch != null -> extracted["animalAge"] = "${yearMatch.groupValues[1]} years"
                monthMatch != null -> extracted["animalAge"] = "${monthMatch.groupValues[1]} months"
                listOf("calf", "bachda", "bachhiya", "katia", "बछड़ा", "बछिया").any { lower.contains(it) } -> extracted["animalAge"] = "Calf"
                listOf("adult", "badi", "jawan", "वयस्क").any { lower.contains(it) } -> extracted["animalAge"] = "Adult"
            }
        }

        val symptomsList = mutableListOf<String>()
        if (listOf("bukhar", "bukhaar", "fever", "tez tap", "taap", "गरम", "बुखार", "ताप", "ਬੁਖਾਰ").any { lower.contains(it) }) {
            extracted["feverStatus"] = "yes"
            symptomsList += "Fever"
        } else if (listOf("no fever", "bukhar nahi", "बुखार नहीं").any { lower.contains(it) }) {
            extracted["feverStatus"] = "no"
        }

        if (listOf("doodh band", "dudh band", "milk stopped", "no milk", "दूध बंद").any { lower.contains(it) }) {
            extracted["milkYieldChange"] = "stopped"
            symptomsList += "milk production stopped"
        } else if (listOf("doodh kam", "dudh kam", "milk kam", "milk drop", "milk dropped", "reduced milk", "milk less", "दूध कम", "ਦੁੱਧ ਘੱਟ").any { lower.contains(it) }) {
            extracted["milkYieldChange"] = "dropped"
            symptomsList += "reduced milk production"
        }

        if (listOf("kuch nahi kha", "not eating", "chara chhod", "no appetite", "खाना बंद", "चारा नहीं").any { lower.contains(it) }) {
            extracted["appetiteStatus"] = "none"
            symptomsList += "no appetite"
        } else if (listOf("bhookh kam", "bhukh kam", "chara kam", "kam kha", "reduced appetite", "less feed", "भूख कम", "चारा कम").any { lower.contains(it) }) {
            extracted["appetiteStatus"] = "reduced"
            symptomsList += "reduced appetite"
        }

        if (listOf("chhale", "chhala", "blister", "blisters", "lesion", "lesions", "ulcer", "ulcers", "muh me chhale", "khur", "छाले", "घाव").any { lower.contains(it) }) {
            extracted["lesionsOrUlcers"] = "true"
            symptomsList += "visible lesions/blisters"
        }

        val extraSymptomPhrases = listOf(
            "cough" to listOf("cough", "khansi", "खांसी"),
            "lameness" to listOf("lame", "langda", "लंगड़ा"),
            "diarrhea" to listOf("diarrhea", "dast", "दस्त"),
            "weakness" to listOf("weak", "kamzori", "सुस्त", "कमजोरी"),
            "salivation" to listOf("drool", "lar", "लार"),
            "mastitis / udder swelling" to listOf("thanela", "udder", "थनैला"),
        )
        for ((label, keys) in extraSymptomPhrases) {
            if (keys.any { lower.contains(it) }) symptomsList += label
        }

        if (wants("symptoms", "symptomsNotes")) {
            val formatted = if (symptomsList.isNotEmpty()) {
                symptomsList.distinct().joinToString(", ").replaceFirstChar { it.uppercase() }
            } else {
                ""
            }
            if (formatted.isNotBlank()) {
                if (wants("symptoms")) extracted["symptoms"] = formatted
                if (wants("symptomsNotes")) extracted["symptomsNotes"] = formatted
            }
        }
    }

    private fun parseCropSymptoms(raw: String): String? {
        val lower = raw.lowercase(Locale.ROOT)
        val matched = mutableListOf<String>()
        val catalog = listOf(
            "Yellowing leaves" to listOf("yellow", "peela", "pila", "पीला", "पीले"),
            "Brown/black spots" to listOf("spot", "spots", "dhabbe", "daag", "धब्बे"),
            "Rust pustules" to listOf("rust", "ratua", "gerui", "रतुआ"),
            "Leaf curl" to listOf("curl", "curling", "mudna", "सिकुड़", "मुड़"),
            "Wilting" to listOf("wilt", "wilting", "murjha", "sukh", "सूख", "मुरझा"),
            "Insect holes / pests" to listOf("hole", "worm", "pest", "keeda", "kida", "सुंडी", "कीड़ा"),
            "White powdery coating" to listOf("powder", "powdery", "safed", "सफेद"),
        )
        for ((label, terms) in catalog) {
            if (terms.any { lower.contains(it) }) matched += label
        }
        return if (matched.isNotEmpty()) matched.joinToString(", ") else null
    }

    private fun parseCustomTask(raw: String): String? {
        val patterns = listOf(
            Regex("""(?i)(?:task|kaam|work|remind(?:er)?|add)\s*(?:hai|is|:)?\s*(.+)"""),
        )
        for (p in patterns) {
            p.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun parseProfileAndLocationFields(
        raw: String,
        lower: String,
        targetFields: List<String>,
        extracted: MutableMap<String, String>,
    ) {
        fun wants(vararg keys: String): Boolean = wantsField(targetFields, *keys)

        if (wants("state")) {
            matchDictionary(lower, STATE_DICTIONARY)?.let { extracted["state"] = it }
        }
        if (wants("village")) {
            Regex("""(?i)(?:village|gaon|gram|गांव|गाँव|ग्राम)\s+(?:hai\s+)?([A-Za-z\p{Devanagari}]{2,30})""").find(raw)?.let { m ->
                extracted["village"] = m.groupValues[1].trim()
            }
        }
        if (wants("district", "location", "marketSearch")) {
            Regex("""(?i)(?:district|jila|zila|mandi|city|शहर|जिला|मंडी)\s+(?:hai\s+)?([A-Za-z\p{Devanagari}]+(?:\s+[A-Za-z\p{Devanagari}]+)?)""").find(raw)?.let { m ->
                val value = m.groupValues[1].trim()
                if (wants("district")) extracted["district"] = value
                if (wants("location")) extracted["location"] = value
                if (wants("marketSearch")) extracted["marketSearch"] = value
            }
        }
        if (wants("farmerName", "name")) {
            Regex("""(?i)(?:mera naam|my name is|naam|name)\s+([A-Za-z\p{Devanagari}]+(?:\s+[A-Za-z\p{Devanagari}]+)?)""").find(raw)?.let { m ->
                val cleanName = m.groupValues[1].replace(Regex("""(?i)\s+(hai|h|is)$"""), "").trim()
                if (cleanName.isNotBlank()) {
                    if (wants("farmerName")) extracted["farmerName"] = cleanName
                    if (wants("name")) extracted["name"] = cleanName
                }
            }
        }
        if (wants("farmName")) {
            Regex("""(?i)(?:farm name|khet ka naam|plot name)\s*(?:hai|is|:)?\s*([A-Za-z0-9\p{Devanagari}\s]{2,35})""").find(raw)?.let { m ->
                val clean = m.groupValues[1].replace(Regex("""(?i)\s+(hai|is)$"""), "").trim()
                if (clean.isNotBlank()) extracted["farmName"] = clean
            }
        }
    }

    private fun wantsField(targetFields: List<String>, vararg keys: String): Boolean =
        targetFields.isEmpty() || keys.any { k -> targetFields.any { it.equals(k, ignoreCase = true) } }

    private fun matchDictionary(lower: String, entries: List<Pair<String, List<String>>>): String? {
        for ((canonical, keywords) in entries) {
            for (kw in keywords) {
                val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(kw.lowercase(Locale.ROOT))}(?![\\p{L}\\p{N}])")
                if (pattern.containsMatchIn(lower)) return canonical
            }
        }
        return null
    }
}
