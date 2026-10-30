#pragma once
#include <stdlib.h>

// Android exposes aligned_alloc only from API 28; the app also supports API 26.
static inline void* phonon_aligned_alloc(size_t alignment, size_t size) {
    void* result = NULL;
    return posix_memalign(&result, alignment, size) == 0 ? result : NULL;
}
#define aligned_alloc phonon_aligned_alloc
