cmake_minimum_required(VERSION 3.22.1)
project(s1_speculation_proof LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(LLAMA_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp/third_party/llama.cpp")
foreach(flag BUILD_SHARED_LIBS LLAMA_BUILD_COMMON LLAMA_BUILD_TESTS LLAMA_BUILD_TOOLS
        LLAMA_BUILD_EXAMPLES LLAMA_BUILD_SERVER GGML_OPENMP GGML_NATIVE
        GGML_BACKEND_DL GGML_CPU_ALL_VARIANTS GGML_OPENCL)
    set(${flag} OFF CACHE BOOL "" FORCE)
endforeach()
add_subdirectory("${LLAMA_SOURCE}" "${CMAKE_CURRENT_BINARY_DIR}/llama")
add_executable(s1_speculation_proof proof.cpp ../../app/src/main/cpp/s1mini_ngram.cpp
    "${LLAMA_SOURCE}/common/ngram-mod.cpp")
target_include_directories(s1_speculation_proof PRIVATE ../../app/src/main/cpp "${LLAMA_SOURCE}/common")
target_link_libraries(s1_speculation_proof PRIVATE llama)
