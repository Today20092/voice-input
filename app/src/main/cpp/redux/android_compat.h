#pragma once
#if defined(__ANDROID__) && __ANDROID_API__ < 30
#include <sys/syscall.h>
#include <unistd.h>
// Bionic exposes memfd_create only from API 30. The app supports API 26;
// use the Linux syscall and let upstream fall back if the kernel rejects it.
static inline int redux_memfd_create(const char* name, unsigned int flags) {
    return static_cast<int>(syscall(__NR_memfd_create, name, flags));
}
#define memfd_create redux_memfd_create
#endif
