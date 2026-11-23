package com.kareem.cortex.prime.voice

/** Rule-based clean-up of raw Whisper output (spec §6.3): artefacts, fillers, whitespace. */
object TranscriptCleaner {
    private val nonSpeech = setOf(
        "music", "موسيقى", "applause", "تصفيق", "silence", "صمت", "blank_audio", "laughter", "ضحك", "noise", "inaudible",
    )
    private val bracket = Regex("""[\[(]([^\])]*)[\])]""")
    private val fillers = setOf("يعني", "امم", "اممم", "اه", "آه", "ممم", "um", "uh", "erm", "hmm", "umm")

    fun clean(raw: String): String {
        val noArtefacts = bracket.replace(raw) { m ->
            val words = m.groupValues[1].lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (words.isNotEmpty() && words.all { it in nonSpeech }) " " else m.value
        }
        return noArtefacts.split(Regex("""\s+"""))
            .filter { it.isNotBlank() && it.lowercase().trim(',', '.', '،') !in fillers }
            .joinToString(" ").trim()
    }
}
