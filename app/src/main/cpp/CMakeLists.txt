
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("voiceinput")

set(CMAKE_CXX_FLAGS "-O3 -Wall -Wextra -g3")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    voiceinput.cpp
        ggml/whisper.cpp
        ggml/ggml.c
        ggml/ggml-alloc.c
        ggml/ggml-backend.c
        ggml/ggml-quants.c
        jni_common.cpp)

if(ANDROID_ABI STREQUAL armeabi-v7a)
    set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES COMPILE_FLAGS -mfpu=neon)
endif()


# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
    android
    log)

# S1-mini by Superwhisper: pinned llama.cpp with Android CPU variants and Adreno OpenCL.
set(S1_THIRD_PARTY "${CMAKE_CURRENT_SOURCE_DIR}/third_party")
set(S1_OPENCL_HEADERS_DIR "${S1_THIRD_PARTY}/OpenCL-Headers" CACHE PATH "" FORCE)
list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

set(OPENCL_ICD_LOADER_HEADERS_DIR "${S1_OPENCL_HEADERS_DIR}" CACHE PATH "" FORCE)
set(OPENCL_ICD_LOADER_BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(ENABLE_OPENCL_LAYERS OFF CACHE BOOL "" FORCE)
set(ENABLE_OPENCL_LAYERINFO OFF CACHE BOOL "" FORCE)
add_subdirectory("${S1_THIRD_PARTY}/OpenCL-ICD-Loader" "${CMAKE_CURRENT_BINARY_DIR}/opencl-loader")

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_OPENCL ON CACHE BOOL "" FORCE)
set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
add_subdirectory("${S1_THIRD_PARTY}/llama.cpp" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")

add_library(s1mini SHARED s1mini_jni.cpp)
target_include_directories(s1mini PRIVATE
    "${S1_THIRD_PARTY}/llama.cpp/include"
    "${S1_THIRD_PARTY}/llama.cpp/ggml/include")
target_link_libraries(s1mini PRIVATE llama ggml android log dl)
