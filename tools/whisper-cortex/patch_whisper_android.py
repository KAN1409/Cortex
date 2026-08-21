#!/usr/bin/env python3
"""Patch whisper-android 1.0.0 with Cortex prompt + beam-search support.

The patch is intentionally tiny and reproducible. It adds two decoder options:
an initial prompt and non-speech-token suppression. Decoding uses beam search
with five beams; greedy decoding can collapse a clear multi-switch short note
to a tiny fragment. The JNI bridge also drops
only low-probability ASCII number/punctuation tokens that appear before the
first spoken word; this removes prompt leakage such as ``2.2`` without touching
high-confidence spoken numbers or any Arabic/Latin word.
"""

from pathlib import Path
import sys


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one match in {path}, found {count}")
    path.write_text(text.replace(old, new, 1))


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_whisper_android.py <upstream-whisper-dir>")
    root = Path(sys.argv[1]).resolve()

    config = root / "library/src/main/kotlin/dev/ffmpegkit/whisper/WhisperConfig.kt"
    replace_once(
        config,
        "    val printTimestamps: Boolean = true,\n)",
        "    val printTimestamps: Boolean = true,\n"
        "    val initialPrompt: String = \"\",\n"
        "    val suppressNonSpeechTokens: Boolean = true,\n"
        ")",
    )

    jni = root / "library/src/main/kotlin/dev/ffmpegkit/whisper/WhisperJNI.kt"
    replace_once(
        jni,
        "        translate: Boolean,\n        threads: Int,\n    ): String",
        "        translate: Boolean,\n"
        "        threads: Int,\n"
        "        initialPrompt: String,\n"
        "        suppressNonSpeechTokens: Boolean,\n"
        "    ): String",
    )

    whisper = root / "library/src/main/kotlin/dev/ffmpegkit/whisper/Whisper.kt"
    replace_once(
        whisper,
        "            config.translate,\n            config.threads,\n        )",
        "            config.translate,\n"
        "            config.threads,\n"
        "            config.initialPrompt,\n"
        "            config.suppressNonSpeechTokens,\n"
        "        )",
    )

    cpp = root / "library/src/main/jni/whisper_jni.cpp"
    replace_once(
        cpp,
        "#include <cstdint>\n#include <cstdio>\n#include <cstring>",
        "#include <cstdint>\n#include <cstdio>\n#include <cstring>\n#include <cctype>",
    )
    replace_once(
        cpp,
        "} // namespace\n\nextern \"C\" {",
        r'''// True only for a low-confidence ASCII number/punctuation token before
// the first spoken word. Arabic and Latin letters are never removed.
bool prompt_leak_prefix_token(const std::string &token, float probability) {
    if (token.empty() || probability >= 0.65f) return false;
    bool visible = false;
    for (unsigned char c : token) {
        if (c >= 0x80 || std::isalpha(c)) return false;
        if (!std::isspace(c)) visible = true;
    }
    return visible;
}

bool token_starts_speech(const std::string &token) {
    for (unsigned char c : token) {
        if (c >= 0x80 || std::isalpha(c) || std::isdigit(c)) return true;
    }
    return false;
}

} // namespace

extern "C" {''',
    )
    replace_once(
        cpp,
        "        JNIEnv *env, jobject, jlong handle, jstring jaudio,\n"
        "        jstring jlang, jboolean translate, jint threads) {",
        "        JNIEnv *env, jobject, jlong handle, jstring jaudio,\n"
        "        jstring jlang, jboolean translate, jint threads,\n"
        "        jstring jprompt, jboolean suppressNonSpeechTokens) {",
    )
    replace_once(
        cpp,
        "    const std::string lang = jstr(env, jlang);\n"
        "    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);",
        "    const std::string lang = jstr(env, jlang);\n"
        "    const std::string prompt = jstr(env, jprompt);\n"
        "    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);\n"
        "    wparams.beam_search.beam_size = 5;\n"
        "    wparams.beam_search.patience = -1.0f;",
    )
    replace_once(
        cpp,
        "    wparams.n_threads      = threads > 0 ? threads : 4;\n"
        "    wparams.print_progress = false;",
        "    wparams.n_threads      = threads > 0 ? threads : 4;\n"
        "    wparams.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();\n"
        "    wparams.suppress_nst   = suppressNonSpeechTokens == JNI_TRUE;\n"
        "    wparams.print_progress = false;",
    )
    replace_once(
        cpp,
        "    const int n = whisper_full_n_segments(ctx);\n"
        "    std::string full, segments;\n"
        "    for (int i = 0; i < n; ++i) {\n"
        "        const char *seg = whisper_full_get_segment_text(ctx, i);",
        "    const int n = whisper_full_n_segments(ctx);\n"
        "    std::string full, segments;\n"
        "    bool speechStarted = false;\n"
        "    for (int i = 0; i < n; ++i) {\n"
        "        std::string filtered;\n"
        "        const int tokenCount = whisper_full_n_tokens(ctx, i);\n"
        "        for (int j = 0; j < tokenCount; ++j) {\n"
        "            const char *rawToken = whisper_full_get_token_text(ctx, i, j);\n"
        "            const std::string token = rawToken ? rawToken : \"\";\n"
        "            if (token.rfind(\"[_\", 0) == 0 || token.rfind(\"<|\", 0) == 0) continue;\n"
        "            const float probability = whisper_full_get_token_p(ctx, i, j);\n"
        "            if (!speechStarted && prompt_leak_prefix_token(token, probability)) continue;\n"
        "            filtered += token;\n"
        "            if (token_starts_speech(token)) speechStarted = true;\n"
        "        }\n"
        "        const char *rawSegment = whisper_full_get_segment_text(ctx, i);\n"
        "        const std::string seg = filtered.empty() ? (rawSegment ? rawSegment : \"\") : filtered;",
    )
    replace_once(
        cpp,
        "        full += seg ? seg : \"\";\n"
        "        if (i) segments += \",\";\n"
        "        segments += \"{\\\"startMs\\\":\" + std::to_string(t0) +\n"
        "                    \",\\\"endMs\\\":\"   + std::to_string(t1) +\n"
        "                    \",\\\"text\\\":\\\"\"  + json_escape(seg ? seg : \"\") + \"\\\"}\";",
        "        full += seg;\n"
        "        if (i) segments += \",\";\n"
        "        segments += \"{\\\"startMs\\\":\" + std::to_string(t0) +\n"
        "                    \",\\\"endMs\\\":\"   + std::to_string(t1) +\n"
        "                    \",\\\"text\\\":\\\"\"  + json_escape(seg) + \"\\\"}\";",
    )

    patched_cpp = cpp.read_text()
    required = (
        "whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH)",
        "wparams.beam_search.beam_size = 5",
        "wparams.initial_prompt = prompt.empty() ? nullptr : prompt.c_str()",
    )
    if any(marker not in patched_cpp for marker in required):
        raise RuntimeError("Cortex beam-5 decoder patch verification failed")
    if "whisper_full_default_params(WHISPER_SAMPLING_GREEDY)" in patched_cpp:
        raise RuntimeError("Greedy decoder survived Cortex beam-5 patch")

    print("CORTEX_WHISPER_PROMPT_BEAM5_PATCH=PASS")


if __name__ == "__main__":
    main()
