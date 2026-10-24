#!/usr/bin/env bash
# CI-only experiment for #41; does not change the released runtime dependency.
set -euo pipefail

workspace=${1:?Usage: build-moonshine-ort-candidate.sh EMPTY_WORKSPACE}
test ! -e "$workspace"
mkdir -p "$workspace/output"
workspace=$(cd "$workspace" && pwd)
ndk="${ANDROID_HOME:?Android SDK required}/ndk/28.2.13676358"
llvm_bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
ort_revision=be835efc56aca19b8e810538ec93c8e150e0fc61
original="$workspace/moonshine-voice-0.0.68.aar"
candidate="$workspace/output/moonshine-voice-0.0.68-ort-no-kleidiai.aar"

curl --fail --location --retry 3 \
    https://repo.maven.apache.org/maven2/ai/moonshine/moonshine-voice/0.0.68/moonshine-voice-0.0.68.aar \
    -o "$original"
echo "21a8ab5f7b2e0b962b7ef6f0101a7e19fc716ae82d97c163bbbaa247c3ef4a80  $original" | sha256sum --check

# Establish the failing packaging baseline before spending time compiling.
if bash tools/verify-moonshine-ort-candidate.sh "$original" "$llvm_bin" > "$workspace/output/baseline.log" 2>&1; then
    echo 'Baseline unexpectedly passed; review the pinned input before continuing.' >&2
    exit 1
fi
grep -q 'Rejected runtime: SME/SME2 instructions remain' "$workspace/output/baseline.log"

git init "$workspace/onnxruntime"
git -C "$workspace/onnxruntime" remote add origin https://github.com/microsoft/onnxruntime.git
git -C "$workspace/onnxruntime" fetch --depth 1 origin "$ort_revision"
git -C "$workspace/onnxruntime" checkout --detach FETCH_HEAD
git -C "$workspace/onnxruntime" submodule update --init --recursive --depth 1
(
    cd "$workspace/onnxruntime"
    ./build.sh --config Release --build_dir "$workspace/build" \
        --build_shared_lib --skip_tests --parallel 2 \
        --android --android_sdk_path "$ANDROID_HOME" --android_ndk_path "$ndk" \
        --android_abi arm64-v8a --android_api 26 --use_nnapi --no_kleidiai \
        --cmake_extra_defines onnxruntime_USE_KLEIDIAI=OFF onnxruntime_BUILD_UNIT_TESTS=OFF \
        'CMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384'
)
grep -q 'onnxruntime_USE_KLEIDIAI:BOOL=OFF' "$workspace/build/Release/CMakeCache.txt"
mkdir -p "$workspace/replacement/jni/arm64-v8a"
"$llvm_bin/llvm-strip" --strip-debug "$workspace/build/Release/libonnxruntime.so" \
    -o "$workspace/replacement/jni/arm64-v8a/libonnxruntime.so"
cp "$original" "$candidate"
(
    cd "$workspace/replacement"
    zip -q "$candidate" jni/arm64-v8a/libonnxruntime.so
)
bash tools/verify-moonshine-ort-candidate.sh "$candidate" "$llvm_bin" \
    | tee "$workspace/output/verification.log"
cp "$workspace/onnxruntime/LICENSE" "$workspace/output/ONNX-Runtime-LICENSE"
cp "$workspace/onnxruntime/ThirdPartyNotices.txt" "$workspace/output/ONNX-Runtime-ThirdPartyNotices.txt"
cp "$workspace/build/Release/CMakeCache.txt" "$workspace/output/CMakeCache.txt"
(
    cd "$workspace/output"
    sha256sum moonshine-voice-0.0.68-ort-no-kleidiai.aar > SHA256SUMS
    printf 'ORT source: %s\nMoonshine: 0.0.68\nNDK: 28.2.13676358\nABI: arm64-v8a\nAPI: 26\nKleidiAI: OFF\nNNAPI exports: ON\nApp revision: %s\n' \
        "$ort_revision" "${GITHUB_SHA:-unknown}" > provenance.txt
)
