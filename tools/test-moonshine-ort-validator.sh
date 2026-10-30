#!/usr/bin/env bash
# Small ELF-output fixtures; no Android tools, runtime download or compilation.
set -euo pipefail
cd "$(dirname "$0")/.."
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
mkdir -p "$temporary/bin"
export PATH="$temporary/bin:$PATH"

cat > "$temporary/bin/unzip" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$3"
MOCK
cat > "$temporary/bin/llvm-readelf" <<'MOCK'
#!/usr/bin/env bash
case "$1" in
    --file-header) printf 'Class: ELF64\nMachine: AArch64\n' ;;
    --dynamic) echo 'SONAME [libonnxruntime.so]' ;;
    --version-info) echo 'Name: VERS_1.23.0' ;;
    --program-headers) echo 'LOAD 0x4000' ;;
    --dyn-syms)
        if [[ "$3" == */libmoonshine.so ]]; then
            printf '1: 0 0 FUNC GLOBAL DEFAULT UND %s@VERS_1.23.0\n' \
                OrtGetApiBase OrtSessionOptionsAppendExecutionProvider_CPU OrtSessionOptionsAppendExecutionProvider_Nnapi
        else
            printf '1: 0 0 FUNC GLOBAL DEFAULT 12 %s@@VERS_1.23.0\n' \
                OrtGetApiBase OrtSessionOptionsAppendExecutionProvider_CPU
            printf '1: 0 0 FUNC GLOBAL DEFAULT %s %s%s\n' "${EXPORT_SECTION:-12}" \
                OrtSessionOptionsAppendExecutionProvider_Nnapi "$EXPORT_VERSION"
        fi ;;
    *) exit 1 ;;
esac
MOCK
cat > "$temporary/bin/llvm-objdump" <<'MOCK'
#!/usr/bin/env bash
echo '0: ret'
MOCK
chmod +x "$temporary/bin/"*

for version in '@@VERS_1.23.0' '@VERS_1.23.0'; do
    EXPORT_VERSION="$version" bash tools/verify-moonshine-ort-candidate.sh fixture.aar "$temporary/bin" > "$temporary/result"
done
for version in '@@VERS_1.24.0' '@@VERS_1.23.01' ''; do
    if EXPORT_VERSION="$version" bash tools/verify-moonshine-ort-candidate.sh fixture.aar "$temporary/bin" > "$temporary/result" 2>&1; then
        echo "Validator accepted incompatible export version: $version" >&2
        exit 1
    fi
    grep -q 'Missing Moonshine runtime import: OrtSessionOptionsAppendExecutionProvider_Nnapi@VERS_1.23.0' "$temporary/result"
done
if EXPORT_SECTION=UND EXPORT_VERSION='@@VERS_1.23.0' \
    bash tools/verify-moonshine-ort-candidate.sh fixture.aar "$temporary/bin" > "$temporary/result" 2>&1; then
    echo 'Validator accepted an undefined runtime export' >&2
    exit 1
fi
grep -q 'Missing Moonshine runtime import:' "$temporary/result"
echo 'Moonshine runtime validator fixtures passed (default/non-default versions; wrong, absent and undefined exports).'
