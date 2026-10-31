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

struct Timing {
    long prefill_ms = 0, decode_ms = 0, total_ms = 0;
};

static long elapsed(Clock::time_point started) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - started).count();
}

static S1NgramResult generate(llama_model * model, const std::vector<llama_token> & input,
        int limit, bool speculative, std::atomic_bool & cancelled, int threads = 2,
        Timing * timing = nullptr) {
    const auto started = Clock::now();
    auto params = llama_context_default_params();
    params.n_ctx = 2048;
    params.n_batch = 2048;
    params.n_ubatch = 512;
    params.n_threads = threads;
    params.n_threads_batch = threads;
    params.abort_callback = [](void * value) { return static_cast<std::atomic_bool *>(value)->load(); };
    params.abort_callback_data = &cancelled;
    using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
    Context ctx(llama_init_from_model(model, params), llama_free);
    require(ctx != nullptr, "context_init_failed");
    using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
    Sampler sampler(llama_sampler_init_greedy(), llama_sampler_free);
    require(sampler != nullptr, "sampler_init_failed");
    auto mutable_input = input;
    const auto prefill_started = Clock::now();
    require(llama_decode(ctx.get(), llama_batch_get_one(mutable_input.data(), mutable_input.size())) == 0,
        "prefill_failed");
    if (timing) timing->prefill_ms = elapsed(prefill_started);
    const auto decode_started = Clock::now();
    const auto * vocab = llama_model_get_vocab(model);
    S1NgramResult result;
    if (speculative) result = s1_ngram_generate(ctx.get(), vocab, sampler.get(), input, limit, cancelled);
    else for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) {
            result.ended = true;
            break;
        }
        require(llama_decode(ctx.get(), llama_batch_get_one(&token, 1)) == 0, "decode_failed");
        result.tokens.push_back(token);
        ++result.decode_calls;
    }
    if (timing) {
        timing->decode_ms = elapsed(decode_started);
        timing->total_ms = elapsed(started);
    }
    return result;
}

static void benchmark(llama_model * model, std::vector<std::string> fixtures) {
#if defined(__ANDROID__)
    constexpr int threads = 6;
#else
    constexpr int threads = 2;
#endif
    fixtures.push_back(
        "so for the next release we need to review the recording flow and make sure that people can "
        "cancel a dictation and try again without losing the previous result please check the model "
        "download screen as well because the connection sometimes drops before the last file finishes "
        "after that we should compare the cleanup timings on the same phone using the same text each "
        "time and keep the short examples separate from the longer notes if the new method changes "
        "any punctuation record that as a mismatch rather than quietly ignoring it then send the "
        "summary after everyone has reviewed the results tomorrow morning");
    fixtures.push_back(
        "okay here are my notes from the planning meeting for the community library open day next "
        "month we agreed that the morning should focus on families while the afternoon gives adults "
        "time to explore the local history collection without having to compete with the storytelling "
        "sessions at the entrance we need a clear welcome desk where volunteers can explain the "
        "schedule point out the quiet room and help visitors find the accessible route to the upstairs "
        "gallery please check that the lift is working before we print the final signs "
        "the craft workshop will use recycled paper and washable paint but we should keep the glue "
        "and scissors on a separate table so younger children can ask for help rather than reaching "
        "across the materials someone suggested making bookmarks with pictures of favorite places in "
        "the neighborhood which sounds more practical than the original plan for large cardboard models "
        "for refreshments we can offer water tea coffee and a small selection of packaged snacks "
        "we still need to confirm the ingredient labels and arrange separate bins for recycling "
        "during the lunch break one volunteer should stay near the entrance while the others take "
        "turns eating nobody should be expected to work the entire day without a proper rest "
        "after the afternoon talk we want a short question session followed by a chance to look "
        "at the photographs on the display boards please ask the speaker whether we can provide "
        "printed captions for people who have difficulty hearing and whether the slides can be shared "
        "on the library website afterward finally we need a simple closing checklist for returning "
        "chairs checking borrowed equipment collecting feedback cards and making sure that visitors "
        "have taken their coats and bags before the building closes");
    constexpr int max_new_tokens = 768;
    std::atomic_bool cancelled{false};
    int drafted = 0, accepted = 0;
    for (size_t i = 0; i < fixtures.size(); ++i) {
        auto input = tokenize(llama_model_get_vocab(model), prompt(fixtures[i]));
        require(input.size() + max_new_tokens < 2048, "benchmark_context_budget_exceeded");
        // Each call creates a fresh context/table; warm-up cannot seed a later request's pool.
        for (int pair = -1; pair < 4; ++pair) {
            Timing timing[2];
            S1NgramResult results[2];
            const int first = pair >= 0 ? pair % 2 : 0;
            for (int turn = 0; turn < 2; ++turn) {
                const int mode = (first + turn) % 2;
                results[mode] = generate(model, input, max_new_tokens, mode == 1, cancelled, threads, &timing[mode]);
            }
            const bool parity = results[0].tokens == results[1].tokens && results[0].ended == results[1].ended;
            std::cout << "case=" << i << " pair=" << pair << " warmup=" << (pair == -1)
                << " first=" << (first ? "candidate" : "plain") << " threads=" << threads << " parity=" << parity
                << " tokens=" << results[0].tokens.size()
                << " plainPrefillMs=" << timing[0].prefill_ms << " candidatePrefillMs=" << timing[1].prefill_ms
                << " plainDecodeMs=" << timing[0].decode_ms << " candidateDecodeMs=" << timing[1].decode_ms
                << " plainTotalMs=" << timing[0].total_ms << " candidateTotalMs=" << timing[1].total_ms
                << " plainDecodeCalls=" << results[0].decode_calls << " candidateDecodeCalls=" << results[1].decode_calls
                << " drafted=" << results[1].drafted << " accepted=" << results[1].accepted << std::endl;
            require(parity, "candidate_differs_from_greedy");
            require(results[0].ended && !results[0].tokens.empty(), "benchmark_output_incomplete");
            require(results[1].pool_bytes <= 64 * 1024 && results[1].peak_batch_tokens <= 17,
                "speculation_memory_bound_exceeded");
            if (pair >= 0) {
                drafted += results[1].drafted;
                accepted += results[1].accepted;
            }
        }
    }
    require(drafted > 0 && accepted > 0, "no_speculative_tokens_were_verified");
    std::cout << "PASS: paired CPU tokens/EOS, four balanced-order pairs, fresh contexts/pools, threads=" << threads << '\n';
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
        const bool benchmark_mode = argc == 3 && std::string(argv[2]) == "--benchmark";
        require(argc == 2 || benchmark_mode, "Usage: s1_speculation_proof PINNED_S1_MODEL [--benchmark]");
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
        if (benchmark_mode) {
            benchmark(model.get(), fixtures);
            return 0;
        }
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
