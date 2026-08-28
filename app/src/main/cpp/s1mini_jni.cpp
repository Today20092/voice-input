#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cctype>
#include <climits>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"

namespace {
constexpr const char * TAG = "S1MiniNative";
std::mutex g_mutex;
std::atomic_bool g_cancelled{false};
llama_model * g_model = nullptr;
std::string g_model_path;
std::string g_runtime;
bool g_initialized = false;
std::string g_backend_path;
std::vector<std::string> g_backend_load_errors;

using Clock = std::chrono::steady_clock;
long elapsed_ms(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - start).count();
}

std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars != nullptr ? chars : "");
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string lower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

std::string json_escape(const std::string & value) {
    std::ostringstream out;
    for (unsigned char c : value) {
        switch (c) {
            case '\\': out << "\\\\"; break;
            case '"': out << "\\\""; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (c < 0x20) {
                    out << "?";
                } else {
                    out << static_cast<char>(c);
                }
        }
    }
    return out.str();
}

bool abort_callback(void *) { return g_cancelled.load(std::memory_order_relaxed); }

const char * safe_string(const char * value, const char * fallback = "") {
    return value != nullptr ? value : fallback;
}

void log_callback(enum ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, text);
        if (text != nullptr && g_backend_load_errors.size() < 8) {
            std::string detail(text);
            detail.erase(std::remove(detail.begin(), detail.end(), '\n'), detail.end());
            detail.erase(std::remove(detail.begin(), detail.end(), '\r'), detail.end());
            g_backend_load_errors.push_back(detail.substr(0, 512));
        }
    }
}

void ensure_initialized(const std::string & backend_path) {
    if (backend_path.empty()) throw std::runtime_error("invalid_native_library_dir");
    if (g_initialized) {
        if (g_backend_path != backend_path) throw std::runtime_error("native_library_dir_changed");
        return;
    }
    llama_log_set(log_callback, nullptr);
    g_backend_load_errors.clear();
    ggml_backend_load_all_from_path(backend_path.c_str());
    llama_backend_init();
    g_backend_path = backend_path;
    g_initialized = true;
}

ggml_backend_dev_t find_device(const std::string & runtime) {
    const auto requested = lower(runtime);
    ggml_backend_dev_t cpu = nullptr;
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        auto device = ggml_backend_dev_get(i);
        if (device == nullptr) continue;
        const auto type = ggml_backend_dev_type(device);
        const std::string name = lower(safe_string(ggml_backend_dev_name(device)));
        const std::string description = lower(safe_string(ggml_backend_dev_description(device)));
        const auto reg = ggml_backend_dev_backend_reg(device);
        const std::string backend = lower(reg != nullptr ? ggml_backend_reg_name(reg) : "");
        if (type == GGML_BACKEND_DEVICE_TYPE_CPU && cpu == nullptr) cpu = device;
        if (requested == "opencl" &&
            (backend.find("opencl") != std::string::npos ||
             name.find("opencl") != std::string::npos ||
             description.find("adreno") != std::string::npos)) {
            return device;
        }
    }
    if (requested == "opencl") throw std::runtime_error("opencl_unavailable");
    if (cpu == nullptr) cpu = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
    if (cpu == nullptr) throw std::runtime_error("cpu_unavailable");
    return cpu;
}

void free_model() {
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_model_path.clear();
    g_runtime.clear();
}

struct ModelLoad {
    llama_model * model;
    ggml_backend_dev_t device;
    bool warm;
    long load_ms;
    std::string device_name;
};

ModelLoad load_model(
        const std::string & backend_path,
        const std::string & path,
        const std::string & runtime) {
    ensure_initialized(backend_path);
    auto device = find_device(runtime);
    const std::string normalized_runtime = lower(runtime);
    if (g_model != nullptr && g_model_path == path && g_runtime == normalized_runtime) {
        return {g_model, device, true, 0L, safe_string(ggml_backend_dev_description(device), "unknown")};
    }
    free_model();
    const auto started = Clock::now();
    auto params = llama_model_default_params();
    ggml_backend_dev_t devices[] = {device, nullptr};
    params.devices = devices;
    params.n_gpu_layers = normalized_runtime == "opencl" ? -1 : 0;
    params.check_tensors = false;
    g_model = llama_model_load_from_file(path.c_str(), params);
    if (g_model == nullptr) throw std::runtime_error("model_load_failed");
    g_model_path = path;
    g_runtime = normalized_runtime;
    return {g_model, device, false, elapsed_ms(started), safe_string(ggml_backend_dev_description(device), "unknown")};
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & prompt) {
    int32_t count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                   nullptr, 0, false, true);
    if (count == INT32_MIN) throw std::runtime_error("tokenize_overflow");
    if (count < 0) count = -count;
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    const int32_t actual = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                          tokens.data(), count, false, true);
    if (actual < 0) throw std::runtime_error("tokenize_failed");
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

std::string token_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(32);
    int32_t size = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    if (size < 0) {
        buffer.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    }
    return size > 0 ? std::string(buffer.data(), static_cast<size_t>(size)) : std::string();
}

jobjectArray string_array(JNIEnv * env, const std::vector<std::string> & values) {
    auto string_class = env->FindClass("java/lang/String");
    auto result = env->NewObjectArray(static_cast<jsize>(values.size()), string_class, nullptr);
    for (size_t i = 0; i < values.size(); ++i) {
        auto value = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

void throw_runtime(JNIEnv * env, const char * category) {
    auto cls = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(cls, category);
}
} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_futo_voiceinput_s1_S1MiniNative_normalize(
        JNIEnv * env, jobject, jstring backend_path_value, jstring model_path_value, jstring prompt_value,
        jint context_size, jint max_new_tokens, jint threads, jstring runtime_value) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancelled.store(false, std::memory_order_relaxed);
    const auto total_started = Clock::now();
    try {
        const auto backend_path = jstring_to_string(env, backend_path_value);
        const auto model_path = jstring_to_string(env, model_path_value);
        const auto prompt = jstring_to_string(env, prompt_value);
        const auto runtime = jstring_to_string(env, runtime_value);
        const auto loaded = load_model(backend_path, model_path, runtime);
        const auto vocab = llama_model_get_vocab(loaded.model);

        const auto tokenize_started = Clock::now();
        auto tokens = tokenize(vocab, prompt);
        const long tokenize_ms = elapsed_ms(tokenize_started);
        if (tokens.empty() || tokens.size() >= static_cast<size_t>(context_size)) {
            throw std::runtime_error("input_too_long");
        }

        auto context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_size);
        context_params.n_batch = static_cast<uint32_t>(context_size);
        context_params.n_ubatch = std::min<uint32_t>(512, context_params.n_batch);
        context_params.n_threads = std::max(1, static_cast<int>(threads));
        context_params.n_threads_batch = context_params.n_threads;
        context_params.abort_callback = abort_callback;
        context_params.abort_callback_data = nullptr;
        context_params.no_perf = false;
        using ContextPtr = std::unique_ptr<llama_context, decltype(&llama_free)>;
        ContextPtr ctx(llama_init_from_model(loaded.model, context_params), llama_free);
        if (ctx == nullptr) throw std::runtime_error("context_init_failed");

        using SamplerPtr = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
        SamplerPtr sampler(llama_sampler_init_greedy(), llama_sampler_free);
        if (sampler == nullptr) {
            throw std::runtime_error("sampler_init_failed");
        }

        const auto prefill_started = Clock::now();
        auto batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
        if (llama_decode(ctx.get(), batch) != 0 || g_cancelled.load()) {
            throw std::runtime_error(g_cancelled.load() ? "cancelled" : "prefill_failed");
        }
        const long prefill_ms = elapsed_ms(prefill_started);

        const auto decode_started = Clock::now();
        std::string output;
        int output_tokens = 0;
        for (; output_tokens < max_new_tokens && !g_cancelled.load(); ++output_tokens) {
            llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            output += token_piece(vocab, token);
            auto next = llama_batch_get_one(&token, 1);
            if (llama_decode(ctx.get(), next) != 0) throw std::runtime_error("decode_failed");
        }
        const long decode_ms = elapsed_ms(decode_started);
        if (g_cancelled.load()) throw std::runtime_error("cancelled");

        std::ostringstream metrics;
        metrics << "{\"backend\":\"" << json_escape(runtime)
                << "\",\"device\":\"" << json_escape(loaded.device_name)
                << "\",\"warm\":" << (loaded.warm ? "true" : "false")
                << ",\"loadMs\":" << loaded.load_ms
                << ",\"tokenizeMs\":" << tokenize_ms
                << ",\"prefillMs\":" << prefill_ms
                << ",\"decodeMs\":" << decode_ms
                << ",\"totalMs\":" << elapsed_ms(total_started)
                << ",\"inputTokens\":" << tokens.size()
                << ",\"outputTokens\":" << output_tokens
                << ",\"threads\":" << threads << "}";
        return string_array(env, {output, metrics.str()});
    } catch (const std::bad_alloc &) {
        throw_runtime(env, "out_of_memory");
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "normalize failed: %s", error.what());
        throw_runtime(env, error.what());
    } catch (...) {
        throw_runtime(env, "native_failure");
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_org_futo_voiceinput_s1_S1MiniNative_cancel(JNIEnv *, jobject) {
    g_cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_org_futo_voiceinput_s1_S1MiniNative_unload(JNIEnv *, jobject) {
    g_cancelled.store(true, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(g_mutex);
    free_model();
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_futo_voiceinput_s1_S1MiniNative_availableBackends(
        JNIEnv * env, jobject, jstring backend_path_value) {
    std::lock_guard<std::mutex> lock(g_mutex);
    try {
        ensure_initialized(jstring_to_string(env, backend_path_value));
        std::vector<std::string> result;
        for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
            auto device = ggml_backend_dev_get(i);
            if (device == nullptr) continue;
            const std::string description = safe_string(ggml_backend_dev_description(device), "unknown");
            const auto type = ggml_backend_dev_type(device);
            const char * kind = type == GGML_BACKEND_DEVICE_TYPE_CPU ? "cpu" :
                    type == GGML_BACKEND_DEVICE_TYPE_GPU ? "gpu" : "other";
            result.push_back(std::string(kind) + ":" + description);
        }
        for (const auto & error : g_backend_load_errors) {
            result.push_back("loader_error:" + error);
        }
        if (result.empty()) result.push_back("loader_error:no_backend_devices_discovered");
        return string_array(env, result);
    } catch (const std::exception & error) {
        throw_runtime(env, error.what());
        return nullptr;
    }
}
