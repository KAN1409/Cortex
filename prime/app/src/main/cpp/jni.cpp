// JNI bridge to whisper.cpp for on-device transcription (Cortex Prime, milestone 2).
#include <jni.h>
#include <string>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_kareem_cortex_prime_voice_WhisperEngine_nativeInit(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

// Returns "<lang>\n<text>". Language is auto-detected so Arabic, English and mixed notes all work.
extern "C" JNIEXPORT jstring JNICALL
Java_com_kareem_cortex_prime_voice_WhisperEngine_nativeTranscribe(JNIEnv *env, jobject, jlong handle, jfloatArray pcm, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");
    jsize n = env->GetArrayLength(pcm);
    jfloat *data = env->GetFloatArrayElements(pcm, nullptr);
    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads;
    wp.language = "auto";
    wp.translate = false;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_timestamps = false;
    wp.no_timestamps = true;
    int rc = whisper_full(ctx, wp, data, n);
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
    if (rc != 0) return env->NewStringUTF("");
    std::string out;
    int lang = whisper_full_lang_id(ctx);
    out += (lang >= 0 ? whisper_lang_str(lang) : "");
    out += "\n";
    for (int i = 0; i < whisper_full_n_segments(ctx); ++i) out += whisper_full_get_segment_text(ctx, i);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_kareem_cortex_prime_voice_WhisperEngine_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}
