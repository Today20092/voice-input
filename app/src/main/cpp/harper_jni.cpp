#include <jni.h>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <vector>

extern "C" char *harper_clean(const uint8_t *, size_t, const uint8_t *, size_t);
extern "C" void harper_free(char *);

// Byte arrays use real UTF-8, unlike JNI's modified UTF-8 string functions.
static jbyteArray clean(JNIEnv *env, jobject, jbyteArray text, jbyteArray vocabulary) {
    if (!text || !vocabulary) return nullptr;
    const jsize textSize = env->GetArrayLength(text);
    const jsize vocabularySize = env->GetArrayLength(vocabulary);
    if (textSize > 40000 || vocabularySize > 100000) return nullptr;
    try {
        std::vector<uint8_t> input(static_cast<size_t>(textSize) + 1);
        std::vector<uint8_t> dictionary(static_cast<size_t>(vocabularySize) + 1);
        env->GetByteArrayRegion(text, 0, textSize, reinterpret_cast<jbyte *>(input.data()));
        if (env->ExceptionCheck()) return nullptr;
        env->GetByteArrayRegion(vocabulary, 0, vocabularySize, reinterpret_cast<jbyte *>(dictionary.data()));
        if (env->ExceptionCheck()) return nullptr;
        std::unique_ptr<char, decltype(&harper_free)> result(
            harper_clean(input.data(), textSize, dictionary.data(), vocabularySize), harper_free);
        if (!result) return nullptr;
        const size_t size = std::strlen(result.get());
        if (size > 200000) return nullptr;
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(size));
        if (!bytes) return nullptr;
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte *>(result.get()));
        return bytes;
    } catch (...) {
        return nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass type = env->FindClass("org/futo/voiceinput/harper/HarperNative");
    if (!type) return JNI_ERR;
    JNINativeMethod methods[] = {{const_cast<char *>("clean"), const_cast<char *>("([B[B)[B"), reinterpret_cast<void *>(clean)}};
    const auto status = env->RegisterNatives(type, methods, 1);
    env->DeleteLocalRef(type);
    return status == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
