#!/usr/bin/env bash
# Verify a test-only Moonshine AAR before packaging it in an affected-device build.
set -euo pipefail

aar=${1:?Usage: verify-moonshine-ort-candidate.sh AAR LLVM_BIN}
llvm_bin=${2:?LLVM_BIN must contain llvm-readelf and llvm-objdump}
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT

for library in libmoonshine.so libonnxruntime.so; do
    unzip -p "$aar" "jni/arm64-v8a/$library" > "$temporary/$library"
done
readelf="$llvm_bin/llvm-readelf"
objdump="$llvm_bin/llvm-objdump"
runtime="$temporary/libonnxruntime.so"

"$readelf" --file-header "$runtime" > "$temporary/header"
grep -q 'Class:.*ELF64' "$temporary/header"
grep -q 'Machine:.*AArch64' "$temporary/header"
"$readelf" --dynamic "$runtime" > "$temporary/dynamic"
grep -q 'SONAME.*\[libonnxruntime.so\]' "$temporary/dynamic"
"$readelf" --version-info "$runtime" > "$temporary/versions"
grep -q 'Name: VERS_1.23.0' "$temporary/versions"

"$readelf" --dyn-syms --wide "$temporary/libmoonshine.so" > "$temporary/imports"
"$readelf" --dyn-syms --wide "$runtime" > "$temporary/exports"
awk '$7 == "UND" && $8 ~ /@VERS_1\.23\.0/ {sub(/@.*/, "", $8); print $8}' \
    "$temporary/imports" | sort -u > "$temporary/required"
test -s "$temporary/required"
while IFS= read -r symbol; do
    if ! awk -v symbol="$symbol" '$7 != "UND" {sub(/@.*/, "", $8); if ($8 == symbol) found=1} END {exit !found}' \
        "$temporary/exports"; then
        echo "Missing Moonshine runtime import: $symbol" >&2
        exit 1
    fi
done < "$temporary/required"

# The candidate disables KleidiAI entirely; SME/SME2 code must not survive linking.
"$objdump" -d --mattr=+sme2 "$runtime" > "$temporary/disassembly"
if grep -Eq '[[:space:]]smstart([[:space:]]|$)|[[:space:]]ptrue[[:space:]]+pn[0-9]' "$temporary/disassembly"; then
    echo 'Rejected runtime: SME/SME2 instructions remain (issue #41)' >&2
    exit 1
fi
echo 'Candidate ARM64 runtime has the required 1.23.0 ABI and no SME/SME2 entry instructions.'
echo 'This verifies packaging, not inference or resolution of the affected-phone crash.'
