#include <jni.h>
#include <dlfcn.h>
#include <sys/auxv.h>
#include <asm/hwcap.h>
#include <cmath>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
extern "C" {
#include "phonon2.h"
#include "threadpool.h"
}

namespace {
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

struct UtfChars {
    JNIEnv* env;
    jstring value;
    const char* text;
    UtfChars(JNIEnv* e, jstring v) : env(e), value(v), text(e->GetStringUTFChars(v, nullptr)) {}
    ~UtfChars() { if (text) env->ReleaseStringUTFChars(value, text); }
};

template<class T> T symbol(void* library, const char* name) {
    auto result = reinterpret_cast<T>(dlsym(library, name));
    if (!result) throw std::runtime_error("Phonon runtime is missing a required function");
    return result;
}

struct Api {
    decltype(&p2_engine_open) open;
    decltype(&p2_engine_close) close;
    decltype(&p2_transcribe) transcribe;
    decltype(&p2_result_free) freeResult;
    decltype(&p2_pool_create) createPool;
    decltype(&p2_pool_destroy) destroyPool;
    decltype(&p2_set_pool) setPool;

    explicit Api(const char* directory) {
        const bool dotprod = (getauxval(AT_HWCAP) & HWCAP_ASIMDDP) != 0;
        const std::string path = std::string(directory) +
            (dotprod ? "/libphonon_dotprod.so" : "/libphonon_baseline.so");
        void* library = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!library) throw std::runtime_error("Could not load the Phonon CPU runtime");
        try {
            open = symbol<decltype(open)>(library, "p2_engine_open");
            close = symbol<decltype(close)>(library, "p2_engine_close");
            transcribe = symbol<decltype(transcribe)>(library, "p2_transcribe");
            freeResult = symbol<decltype(freeResult)>(library, "p2_result_free");
            createPool = symbol<decltype(createPool)>(library, "p2_pool_create");
            destroyPool = symbol<decltype(destroyPool)>(library, "p2_pool_destroy");
            setPool = symbol<decltype(setPool)>(library, "p2_set_pool");
            *symbol<int*>(library, "p2_verbose") = 0;
        } catch (...) {
            dlclose(library);
            throw;
        }
        // Keep code loaded: upstream registers thread-local scratch-buffer destructors.
    }
};

struct PoolScope {
    Api& api;
    PoolScope(Api& a, ThreadPool* pool) : api(a) { api.setPool(pool); }
    ~PoolScope() { api.setPool(nullptr); }
};

struct Session {
    Api& api;
    ThreadPool* pool = nullptr;
    P2Engine* engine = nullptr;
    explicit Session(Api& a) : api(a) {}
    ~Session() {
        if (engine) api.close(engine);
        if (pool) api.destroyPool(pool);
    }
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_futo_voiceinput_phonon_PhononNative_load(
    JNIEnv* env, jobject, jstring directory, jstring libraryDirectory) {
    if (!directory || !libraryDirectory) { fail(env, "Missing Phonon directory"); return 0; }
    UtfChars model(env, directory);
    if (!model.text) return 0;
    UtfChars libraries(env, libraryDirectory);
    if (!libraries.text) return 0;
    try {
        static Api api(libraries.text);
        auto session = std::make_unique<Session>(api);
        session->pool = api.createPool(4);
        if (!session->pool) throw std::runtime_error("Could not allocate Phonon workers");
        PoolScope pool(api, session->pool);
        session->engine = api.open(model.text);
        if (!session->engine) throw std::runtime_error("Could not load Phonon 2 model files");
        return reinterpret_cast<jlong>(session.release());
    } catch (const std::exception&) {
        fail(env, "Could not load Phonon 2");
        return 0;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_futo_voiceinput_phonon_PhononNative_transcribe(
    JNIEnv* env, jobject, jlong handle, jfloatArray samples) {
    auto* session = reinterpret_cast<Session*>(handle);
    if (!session || !samples) { fail(env, "Phonon 2 is not loaded or audio is missing"); return nullptr; }
    try {
        std::vector<float> pcm(env->GetArrayLength(samples));
        if (pcm.empty()) return env->NewByteArray(0);
        env->GetFloatArrayRegion(samples, 0, static_cast<jsize>(pcm.size()), pcm.data());
        if (env->ExceptionCheck()) return nullptr;
        for (float value : pcm) {
            if (!std::isfinite(value) || value < -1.0f || value > 1.0f) {
                env->ThrowNew(env->FindClass("org/futo/voiceinput/phonon/PhononAudioException"),
                              "Phonon received invalid audio samples");
                return nullptr;
            }
        }
        PoolScope pool(session->api, session->pool);
        P2Result result{};
        auto release = [&session](P2Result* r) { session->api.freeResult(r); };
        std::unique_ptr<P2Result, decltype(release)> cleanup(&result, release);
        if (session->api.transcribe(session->engine, pcm.data(), static_cast<int>(pcm.size()),
                                   16000, &result) != 0 || !result.text) {
            env->ThrowNew(env->FindClass("org/futo/voiceinput/phonon/PhononInferenceException"),
                          "Phonon transcription failed");
            return nullptr;
        }
        const auto length = static_cast<jsize>(std::strlen(result.text));
        auto bytes = env->NewByteArray(length);
        if (bytes) env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte*>(result.text));
        return bytes;
    } catch (const std::bad_alloc&) {
        env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "Phonon audio allocation failed");
        return nullptr;
    } catch (const std::exception&) {
        fail(env, "Phonon 2 could not transcribe this recording");
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_futo_voiceinput_phonon_PhononNative_free(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Session*>(handle);
}
