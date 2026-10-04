#include <jni.h>
#include <vector>
#include <cstring>
#include <exception>
#include "parakeet_capi.h"

static void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_futo_voiceinput_redux_ReduxNative_load(JNIEnv* env, jobject, jstring path) {
    const char* value = env->GetStringUTFChars(path, nullptr);
    if (!value) return 0;
    auto* ctx = parakeet_capi_load(value);
    env->ReleaseStringUTFChars(path, value);
    if (!ctx) fail(env, parakeet_capi_load_error());
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_futo_voiceinput_redux_ReduxNative_transcribe(JNIEnv* env, jobject, jlong handle, jfloatArray samples) {
    auto* ctx = reinterpret_cast<parakeet_ctx*>(handle);
    if (!ctx) { fail(env, "Parakeet Redux is not loaded"); return nullptr; }
    try {
        std::vector<float> pcm(env->GetArrayLength(samples));
        env->GetFloatArrayRegion(samples, 0, pcm.size(), pcm.data());
        if (env->ExceptionCheck()) return nullptr;
        char* text = parakeet_capi_transcribe_pcm(ctx, pcm.data(), pcm.size(), 16000, 0);
        if (!text) { fail(env, parakeet_capi_last_error(ctx)); return nullptr; }
        // JNI NewStringUTF uses modified UTF-8. Decode ordinary UTF-8 through Java
        // so multilingual text, including supplementary characters, stays intact.
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(strlen(text)));
        if (!bytes) { parakeet_capi_free_string(text); return nullptr; }
        env->SetByteArrayRegion(bytes, 0, env->GetArrayLength(bytes), reinterpret_cast<jbyte*>(text));
        parakeet_capi_free_string(text);
        jclass cls = env->FindClass("java/lang/String");
        jmethodID constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
        return static_cast<jstring>(env->NewObject(cls, constructor, bytes, env->NewStringUTF("UTF-8")));
    } catch (const std::exception& error) {
        fail(env, error.what()); return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_futo_voiceinput_redux_ReduxNative_free(JNIEnv*, jobject, jlong handle) {
    parakeet_capi_free(reinterpret_cast<parakeet_ctx*>(handle));
}
