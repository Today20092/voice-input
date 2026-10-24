#include "s1mini_ngram.h"
#include <chrono>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

using Clock = std::chrono::steady_clock;
static void require(bool value, const char * reason) {
    if (!value) throw std::runtime_error(reason);
}

static std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text) {
    int count = -llama_tokenize(vocab, text.data(), text.size(), nullptr, 0, false, true);
    require(count > 0, "tokenize_size_failed");
    std::vector<llama_token> tokens(count);
    require(llama_tokenize(vocab, text.data(), text.size(), tokens.data(), count, false, true) == count,
        "tokenize_failed");
    return tokens;
}

static std::string prompt(const std::string & text) {
    return "<|im_start|>system\nYou are a text normalizer for speech-to-text transcripts. The input begins "
        "with a control line specifying the styling, structure, and context settings; clean the "
        "transcript to match those settings and output only the cleaned text.<|im_end|>\n<|im_start|>user\n"
        "[Styling: casual] [Structure: prose] [Context: general]\n" + text +
        "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
}

static S1NgramResult generate(llama_model * model, const std::vector<llama_token> & input,
        int limit, bool speculative, std::atomic_bool & cancelled) {
    auto params = llama_context_default_params();
    params.n_ctx = 2048;
    params.n_batch = 2048;
    params.n_ubatch = 512;
    params.n_threads = 2;
    params.n_threads_batch = 2;
    params.abort_callback = [](void * value) { return static_cast<std::atomic_bool *>(value)->load(); };
    params.abort_callback_data = &cancelled;
    using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
    Context ctx(llama_init_from_model(model, params), llama_free);
    require(ctx != nullptr, "context_init_failed");
    using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
    Sampler sampler(llama_sampler_init_greedy(), llama_sampler_free);
    require(sampler != nullptr, "sampler_init_failed");
    auto mutable_input = input;
    require(llama_decode(ctx.get(), llama_batch_get_one(mutable_input.data(), mutable_input.size())) == 0,
        "prefill_failed");
    const auto * vocab = llama_model_get_vocab(model);
    if (speculative) return s1_ngram_generate(ctx.get(), vocab, sampler.get(), input, limit, cancelled);
    S1NgramResult result;
    for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) {
            result.ended = true;
            break;
        }
        require(llama_decode(ctx.get(), llama_batch_get_one(&token, 1)) == 0, "decode_failed");
        result.tokens.push_back(token);
        ++result.decode_calls;
    }
    return result;
}

static void failure_case(llama_model * model, const std::vector<llama_token> & input,
        const std::string & expected) {
    std::atomic_bool cancelled{false};
    struct AbortState {
        bool armed = false;
        bool cancel = false;
        std::atomic_bool * cancelled;
    } abort{false, false, &cancelled};
    auto params = llama_context_default_params();
    params.n_ctx = params.n_batch = 2048;
    params.n_threads = params.n_threads_batch = 2;
    params.abort_callback = [](void * value) {
        auto & state = *static_cast<AbortState *>(value);
        if (state.armed && state.cancel) state.cancelled->store(true);
        return state.armed;
    };
    params.abort_callback_data = &abort;
    using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
    Context ctx(llama_init_from_model(model, params), llama_free);
    require(ctx != nullptr, "failure_context_init_failed");
    using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
    Sampler sampler(llama_sampler_init_greedy(), llama_sampler_free);
    require(sampler != nullptr, "failure_sampler_init_failed");
    auto tokens = input;
    require(llama_decode(ctx.get(), llama_batch_get_one(tokens.data(), tokens.size())) == 0, "failure_prefill_failed");
    if (expected == "cancelled") cancelled = true;
    if (expected == "decode_failed" || expected == "cancelled_during_decode") abort.armed = true;
    if (expected == "cancelled_during_decode") abort.cancel = true;
    if (expected == "input_too_long") tokens.resize(llama_n_ctx(ctx.get()), tokens.front());
    bool caught = false;
    try {
        s1_ngram_generate(ctx.get(), llama_model_get_vocab(model), sampler.get(), tokens, 256, cancelled);
    } catch (const std::runtime_error & error) {
        caught = (expected == "cancelled_during_decode" ? "cancelled" : expected) == error.what();
    }
    require(caught, "failure_category_not_preserved");
}

int main(int argc, char ** argv) {
    try {
        require(argc == 2, "Usage: s1_speculation_proof PINNED_S1_MODEL");
        llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        llama_backend_init();
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
        Model model(llama_model_load_from_file(argv[1], params), llama_model_free);
        require(model != nullptr, "model_load_failed");
        const std::vector<std::string> fixtures = {
            "sounds good see you then",
            "um can you send the the report tomorrow no actually send it on friday please",
            "please keep this text exactly as spoken and preserve the project names numbers and ordinary words "
            "because this is a longer dictation about recording notes and reviewing the same text carefully "
            "before sending the final report to the team tomorrow morning after the meeting has finished",
            "first review the report second check the numbers third send the corrected summary after lunch",
            "the quick brown fox jumps over the lazy dog the quick brown fox jumps over the lazy dog "
            "the quick brown fox jumps over the lazy dog"
        };
        std::atomic_bool cancelled{false};
        int drafted = 0, accepted = 0;
        for (size_t i = 0; i < fixtures.size(); ++i) {
            auto input = tokenize(llama_model_get_vocab(model.get()), prompt(fixtures[i]));
            const auto started = Clock::now();
            const auto plain = generate(model.get(), input, 256, false, cancelled);
            const auto middle = Clock::now();
            const auto candidate = generate(model.get(), input, 256, true, cancelled);
            const auto finished = Clock::now();
            require(plain.tokens == candidate.tokens && plain.ended == candidate.ended,
                "candidate_differs_from_greedy");
            require(candidate.ended, "fixture_did_not_reach_eos");
            require(!plain.tokens.empty(), "fixture_has_no_output_tokens");
            require(candidate.pool_bytes <= 64 * 1024 && candidate.peak_batch_tokens <= 17,
                "speculation_memory_bound_exceeded");
            drafted += candidate.drafted;
            accepted += candidate.accepted;
            std::cout << "case=" << i << " tokens=" << plain.tokens.size()
                << " plainTotalMs=" << std::chrono::duration_cast<std::chrono::milliseconds>(middle-started).count()
                << " candidateTotalMs=" << std::chrono::duration_cast<std::chrono::milliseconds>(finished-middle).count()
                << " drafted=" << candidate.drafted << " accepted=" << candidate.accepted << '\n';
            const auto truncated = generate(model.get(), input, 1, true, cancelled);
            require(truncated.tokens.size() == 1 && truncated.tokens[0] == plain.tokens[0], "token_limit_failed");
            if (i == 0) require(generate(model.get(), input, 0, true, cancelled).tokens.empty(), "zero_limit_failed");
        }
        require(drafted > 0 && accepted > 0, "no_speculative_tokens_were_verified");
        require(drafted > accepted, "fixtures_do_not_exercise_draft_rejection");
        auto input = tokenize(llama_model_get_vocab(model.get()), prompt(fixtures[0]));
        for (const auto * category : {"cancelled", "cancelled_during_decode", "input_too_long", "decode_failed"}) {
            failure_case(model.get(), input, category);
        }
        require(generate(model.get(), input, 256, false, cancelled).tokens ==
            generate(model.get(), input, 256, true, cancelled).tokens, "recovery_parity_failed");
        std::cout << "PASS: exact tokens/EOS, limits, draft accept/reject, bounded pool/batch, cancellation, failure, recovery\n";
    } catch (const std::exception & error) {
        std::cerr << "FAIL: " << error.what() << '\n';
        return 1;
    }
}
