#include "s1mini_ngram.h"
#include "ngram-mod.h"
#include <algorithm>
#include <array>
#include <new>
#include <stdexcept>

S1NgramResult s1_ngram_generate(
    llama_context * ctx, const llama_vocab * vocab, llama_sampler * sampler,
    const std::vector<llama_token> & prompt, int max_new_tokens,
    const std::atomic_bool & cancelled) {
    if (prompt.empty() || prompt.size() >= llama_n_ctx(ctx)) throw std::runtime_error("input_too_long");
    if (cancelled.load()) throw std::runtime_error("cancelled");
    S1NgramResult result;
    if (max_new_tokens <= 0) return result;
    constexpr int n_match = 24, n_min = 2, n_max = 16;
    // One request owns 64 KiB; the server's shared 16 MiB pool is unnecessary here.
    common_ngram_mod mod(n_match, 16 * 1024);
    result.pool_bytes = static_cast<int>(mod.size_bytes());
    auto history = prompt;
    size_t next_ngram = 0;
    int n_past = static_cast<int>(prompt.size());
    llama_token pending = llama_sampler_sample(sampler, ctx, -1);
    struct Batch {
        llama_batch value = llama_batch_init(n_max + 1, 0, 1);
        ~Batch() { llama_batch_free(value); }
    } batch;
    if (!batch.value.token || !batch.value.pos || !batch.value.n_seq_id || !batch.value.seq_id || !batch.value.logits) {
        throw std::bad_alloc();
    }

    while (result.tokens.size() < static_cast<size_t>(max_new_tokens)) {
        if (cancelled.load()) throw std::runtime_error("cancelled");
        if (llama_vocab_is_eog(vocab, pending)) {
            result.ended = true;
            break;
        }
        const int room = static_cast<int>(llama_n_ctx(ctx)) - n_past;
        if (room <= 0) throw std::runtime_error("decode_failed");
        history.push_back(pending);
        while (next_ngram + n_match < history.size()) mod.add(history.data() + next_ngram++);

        const int cap = std::min({n_max, room - 1, max_new_tokens - static_cast<int>(result.tokens.size()) - 1});
        std::array<llama_token, n_match + n_max> window{};
        int drafted = 0;
        if (history.size() >= n_match) {
            std::copy(history.end() - n_match, history.end(), window.begin());
            while (drafted < cap) {
                auto token = mod.get(window.data() + drafted);
                if (token == common_ngram_mod::EMPTY) break;
                window[n_match + drafted++] = token;
            }
        }
        if (drafted < n_min) drafted = 0;
        batch.value.n_tokens = drafted + 1;
        result.peak_batch_tokens = std::max(result.peak_batch_tokens, batch.value.n_tokens);
        for (int i = 0; i <= drafted; ++i) {
            batch.value.token[i] = i == 0 ? pending : window[n_match + i - 1];
            batch.value.pos[i] = n_past + i;
            batch.value.n_seq_id[i] = 1;
            batch.value.seq_id[i][0] = 0;
            batch.value.logits[i] = true;
        }
        const int status = llama_decode(ctx, batch.value);
        if (cancelled.load()) throw std::runtime_error("cancelled");
        if (status != 0) throw std::runtime_error("decode_failed");
        ++result.decode_calls;
        result.drafted += drafted;
        result.tokens.push_back(pending);
        ++n_past;

        for (int i = 0; i <= drafted; ++i) {
            if (cancelled.load()) throw std::runtime_error("cancelled");
            pending = llama_sampler_sample(sampler, ctx, i);
            if (i == drafted || pending != window[n_match + i] || llama_vocab_is_eog(vocab, pending)) break;
            result.tokens.push_back(pending);
            history.push_back(pending);
            ++n_past;
            ++result.accepted;
        }
        // The first mismatching target token is pending; only verified tokens stay cached.
        if (!llama_memory_seq_rm(llama_get_memory(ctx), 0, n_past, -1)) {
            throw std::runtime_error("decode_failed");
        }
    }
    if (cancelled.load()) throw std::runtime_error("cancelled");
    return result;
}
