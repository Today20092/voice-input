package org.futo.voiceinput.s1

internal object S1MiniProtocol {
    const val MSG_NORMALIZE = 1
    const val MSG_CANCEL = 2
    const val MSG_UNLOAD = 3
    const val MSG_RESULT = 4
    const val MSG_ERROR = 5
    const val MSG_BACKENDS = 6

    const val KEY_REQUEST_ID = "request_id"
    const val KEY_MODEL_PATH = "model_path"
    const val KEY_PROMPT = "prompt"
    const val KEY_CONTEXT_SIZE = "context_size"
    const val KEY_MAX_NEW_TOKENS = "max_new_tokens"
    const val KEY_THREADS = "threads"
    const val KEY_RUNTIME = "runtime"
    const val KEY_WARM_TIMEOUT_MS = "warm_timeout_ms"
    const val KEY_TEXT = "text"
    const val KEY_METRICS = "metrics"
    const val KEY_ERROR_CATEGORY = "error_category"
    const val KEY_BACKENDS = "backends"
}
