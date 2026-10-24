#include "s1mini_ngram.h"
#include <stdexcept>

S1NgramResult s1_ngram_generate(
    llama_context * ctx, const llama_vocab * vocab, llama_sampler * sampler,
    const std::vector<llama_token> & prompt, int max_new_tokens,
    const std::atomic_bool & cancelled) {
    if (prompt.empty() || prompt.size() >= llama_n_ctx(ctx)) throw std::runtime_error("input_too_long");
    S1NgramResult result;
    for (int i = 0; i < max_new_tokens && !cancelled.load(); ++i) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            result.ended = true;
            break;
        }
        auto batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) throw std::runtime_error("decode_failed");
        result.tokens.push_back(token);
        ++result.decode_calls;
    }
    if (cancelled.load()) throw std::runtime_error("cancelled");
    return result;
}
