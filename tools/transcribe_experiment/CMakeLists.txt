cmake_minimum_required(VERSION 3.22.1)
project(share09 LANGUAGES C CXX)
if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "This experiment requires Android arm64-v8a")
endif()
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# A separate configure tree keeps S1's GGML targets/cache out of this build.
foreach(flag TRANSCRIBE_BUILD_SHARED TRANSCRIBE_BUILD_TESTS TRANSCRIBE_BUILD_EXAMPLES
        TRANSCRIBE_BUILD_TOOLS TRANSCRIBE_BUILD_REAL_MODEL_TESTS TRANSCRIBE_INSTALL
        TRANSCRIBE_USE_OPENMP TRANSCRIBE_USE_SYSTEM_BLAS TRANSCRIBE_METAL
        TRANSCRIBE_VULKAN TRANSCRIBE_CUDA TRANSCRIBE_HIP TRANSCRIBE_GGML_BACKEND_DL
        GGML_BACKEND_DL GGML_OPENMP GGML_OPENCL GGML_NATIVE GGML_CPU_ALL_VARIANTS
        GGML_CPU_KLEIDIAI)
    set(${flag} OFF CACHE BOOL "" FORCE)
endforeach()
set(TRANSCRIBE_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/build/transcribe.cpp-4807edaf210d0d7e8a6f7fb2a44b65966a2797f0" CACHE PATH "Verified public source")
add_subdirectory("${TRANSCRIBE_SOURCE}" "${CMAKE_CURRENT_BINARY_DIR}/engine")
# Upstream uses CMAKE_SOURCE_DIR/include, which points at this wrapper when embedded.
target_include_directories(transcribe PUBLIC "${TRANSCRIBE_SOURCE}/include")
add_library(share09_parakeet SHARED parakeet_jni.cpp)
target_link_libraries(share09_parakeet PRIVATE transcribe)
target_link_options(share09_parakeet PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map"
    "-Wl,--exclude-libs,ALL" "-Wl,-z,max-page-size=16384")
set_property(TARGET share09_parakeet APPEND PROPERTY LINK_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/exports.map")
