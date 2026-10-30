#include <jni.h>
#include <transcribe.h>
#include <atomic>
#include <cmath>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace {
struct Session {
    transcribe_session *engine = nullptr;
    std::atomic<bool> aborted{false};
    ~Session() { transcribe_session_free(engine); }
};
void fail(JNIEnv *env, const char *type, const char *message) {
    const auto cls = env->FindClass(type);
    if (cls) env->ThrowNew(cls, message);
}
bool status_ok(JNIEnv *env, transcribe_status status) {
    if (status == TRANSCRIBE_OK) return true;
    const char *type = status == TRANSCRIBE_ERR_ABORTED ? "java/util/concurrent/CancellationException"
        : status == TRANSCRIBE_ERR_OOM ? "java/lang/OutOfMemoryError" : "java/lang/IllegalStateException";
    fail(env, type, transcribe_status_string(status));
    return false;
}
Session *session(JNIEnv *env, jlong handle) {
    if (!handle) fail(env, "java/lang/IllegalStateException", "Missing native session");
    return reinterpret_cast<Session *>(handle);
}
bool abort_run(void *data) { return static_cast<Session *>(data)->aborted.load(); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_futo_voiceinput_experiment_NativeParakeet_nativeOpen(JNIEnv *env, jobject, jbyteArray path) {
    try {
        if (!path) { fail(env, "java/lang/IllegalArgumentException", "Missing model path"); return 0; }
        std::string filename(env->GetArrayLength(path), '\0');
        env->GetByteArrayRegion(path, 0, static_cast<jsize>(filename.size()), reinterpret_cast<jbyte *>(filename.data()));
        if (env->ExceptionCheck()) return 0;
        if (filename.empty() || filename.find('\0') != std::string::npos) {
            fail(env, "java/lang/IllegalArgumentException", "Invalid model path"); return 0;
        }
        static std::once_flag logging;
        std::call_once(logging, [] { transcribe_log_set(nullptr, nullptr); });
        auto result = std::make_unique<Session>();
        transcribe_model_load_params load;
        transcribe_model_load_params_init(&load);
        load.backend = TRANSCRIBE_BACKEND_CPU;
        transcribe_session_params params;
        transcribe_session_params_init(&params);
        params.n_threads = 2;
        if (!status_ok(env, transcribe_open(filename.c_str(), &load, &params, &result->engine))) return 0;
        transcribe_set_abort_callback(result->engine, abort_run, result.get());
        return reinterpret_cast<jlong>(result.release());
    } catch (const std::bad_alloc &) {
        fail(env, "java/lang/OutOfMemoryError", "Native allocation failed");
    } catch (...) {
        fail(env, "java/lang/IllegalStateException", "Native model load failed");
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_org_futo_voiceinput_experiment_NativeParakeet_nativeReset(JNIEnv *env, jobject, jlong handle) {
    if (auto *s = session(env, handle)) s->aborted.store(false);
}
extern "C" JNIEXPORT void JNICALL
Java_org_futo_voiceinput_experiment_NativeParakeet_nativeCancel(JNIEnv *env, jobject, jlong handle) {
    if (auto *s = session(env, handle)) s->aborted.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_org_futo_voiceinput_experiment_NativeParakeet_nativeClose(JNIEnv *env, jobject, jlong handle) {
    // Kotlin holds the lifecycle lock and has joined all inference before this call.
    delete session(env, handle);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_futo_voiceinput_experiment_NativeParakeet_nativeTranscribe(JNIEnv *env, jobject, jlong handle, jfloatArray audio) {
    try {
        auto *s = session(env, handle);
        if (!s) return nullptr;
        if (!audio || env->GetArrayLength(audio) == 0) {
            fail(env, "java/lang/IllegalArgumentException", "Missing PCM"); return nullptr;
        }
        std::vector<float> pcm(env->GetArrayLength(audio));
        env->GetFloatArrayRegion(audio, 0, static_cast<jsize>(pcm.size()), pcm.data());
        if (env->ExceptionCheck()) return nullptr;
        for (float value : pcm) {
            if (!std::isfinite(value) || value < -1 || value > 1) {
                fail(env, "java/lang/IllegalArgumentException", "Invalid PCM"); return nullptr;
            }
        }
        transcribe_run_params params;
        transcribe_run_params_init(&params);
        params.language = "en";
        // Do not reset abort here: cancellation may arrive before the worker starts.
        if (s->aborted.load()) { status_ok(env, TRANSCRIBE_ERR_ABORTED); return nullptr; }
        if (!status_ok(env, transcribe_run(s->engine, pcm.data(), static_cast<int>(pcm.size()), &params))) return nullptr;
        if (s->aborted.load()) { status_ok(env, TRANSCRIBE_ERR_ABORTED); return nullptr; }
        const char *text = transcribe_full_text(s->engine);
        const size_t size = std::strlen(text);
        if (size > static_cast<size_t>(std::numeric_limits<jsize>::max())) throw std::bad_alloc();
        auto result = env->NewByteArray(static_cast<jsize>(size));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte *>(text));
        return result;
    } catch (const std::bad_alloc &) {
        fail(env, "java/lang/OutOfMemoryError", "Native allocation failed");
    } catch (...) {
        fail(env, "java/lang/IllegalStateException", "Native transcription failed");
    }
    return nullptr;
}
