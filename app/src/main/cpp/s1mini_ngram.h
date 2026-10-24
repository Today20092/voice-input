#pragma once

#include "llama.h"
#include <atomic>
#include <vector>

struct S1NgramResult {
    std::vector<llama_token> tokens;
    int drafted = 0;
    int accepted = 0;
    int decode_calls = 0;
    int pool_bytes = 0;
    int peak_batch_tokens = 0;
    bool ended = false;
};

// The caller has decoded the prompt and owns the context and greedy sampler.
S1NgramResult s1_ngram_generate(
    llama_context * ctx, const llama_vocab * vocab, llama_sampler * sampler,
    const std::vector<llama_token> & prompt, int max_new_tokens,
    const std::atomic_bool & cancelled);
