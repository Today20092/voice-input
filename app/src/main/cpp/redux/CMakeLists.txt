cmake_minimum_required(VERSION 3.22.1)
project(redux LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(PARAKEET_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/parakeet.cpp")
add_subdirectory("${PARAKEET_SOURCE}/third_party/ggml" ggml)
file(GLOB REDUX_SOURCES CONFIGURE_DEPENDS "${PARAKEET_SOURCE}/src/*.cpp")
add_library(parakeet_redux SHARED ${REDUX_SOURCES} ../redux_jni.cpp)
target_include_directories(parakeet_redux PRIVATE
    "${PARAKEET_SOURCE}/include" "${PARAKEET_SOURCE}/src" "${PARAKEET_SOURCE}/third_party")
target_compile_definitions(parakeet_redux PRIVATE PARAKEET_VERSION="781a973")
set_source_files_properties("${PARAKEET_SOURCE}/src/bundle_extract.cpp" PROPERTIES
    COMPILE_OPTIONS "-include;${CMAKE_CURRENT_SOURCE_DIR}/android_compat.h")
target_link_libraries(parakeet_redux PRIVATE ggml android log)
# Keep this pinned ggml private, separate from S1-mini and legacy Whisper.
target_link_options(parakeet_redux PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,-Bsymbolic" "-Wl,-z,max-page-size=16384")
set_source_files_properties("${PARAKEET_SOURCE}/src/ternary.cpp"
    "${PARAKEET_SOURCE}/src/ternary_kernels_x86.cpp"
    "${PARAKEET_SOURCE}/src/ternary_kernels_neon.cpp"
    PROPERTIES COMPILE_OPTIONS "-ffp-contract=off")
if(ANDROID_ABI STREQUAL "arm64-v8a")
    # Upstream checks HWCAP before dispatching to dot-product instructions.
    set_property(SOURCE "${PARAKEET_SOURCE}/src/ternary_kernels_neon.cpp"
        APPEND PROPERTY COMPILE_OPTIONS "-march=armv8.2-a+dotprod")
endif()
