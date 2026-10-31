"""Verify release APK native payload and AGP FULL symbol archive (standard library only)."""

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


# Library set shipped by signed v1.4.8, including every CPU variant and runtime.
REQUIRED = {
    "libOpenCL.so",
    "libandroidx.graphics.path.so",
    "libc++_shared.so",
    "libexecutorch.so",
    "libfbjni.so",
    "libggml-base.so",
    "libggml-cpu-android_armv8.0_1.so",
    "libggml-cpu-android_armv8.2_1.so",
    "libggml-cpu-android_armv8.2_2.so",
    "libggml-cpu-android_armv8.6_1.so",
    "libggml-cpu-android_armv9.0_1.so",
    "libggml-cpu-android_armv9.2_1.so",
    "libggml-cpu-android_armv9.2_2.so",
    "libggml-opencl.so",
    "libggml.so",
    "libharper.so",
    "libllama.so",
    "libmoonshine-jni.so",
    "libmoonshine.so",
    "libonnxruntime.so",
    "libparakeet_redux.so",
    "libphonon_baseline.so",
    "libphonon_dotprod.so",
    "libphonon_jni.so",
    "libs1mini.so",
    "libsherpa-onnx-jni.so",
    "libvad_jni.so",
    "libvoiceinput.so",
    "libwhistle_runner.so",
}
WHISTLE_SHA256 = "02a044e15489e19fa9f4f1cd1d5943d39dc0eefc08e0ba99d36279daf2351d46"
PINNED_PAYLOADS = {
    "assets/asr4all-medium/asr_encoder.pte": "f50318acb5de7bfc75229f73edcc9276b3553efc9c3e34a7f3f1cf66f76067b4",
    "assets/asr4all-medium/metadata.json": "321501d89e6249a76b8c0f896f9c3e90089f0f9d6135ab82802e5752990cab41",
    "libwhistle_runner.so": WHISTLE_SHA256,
    "libonnxruntime.so": "cbe4d71990f1032bcc7b4b7baf3e0b18327af6c25ba4bf106b5ecd42ddf22d30",
    "assets/MOONSHINE-LICENSE.txt": "6148d7574a6554b7379b633cfd4c4fe5840c3f548d13bc83e00b52dc6fa00abd",
    "assets/ONNX-RUNTIME-LICENSE.txt": "2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c",
    "assets/ONNX-RUNTIME-THIRD-PARTY-NOTICES.txt": "e9e90971a8e75a9a8ac0c6412e29c1202d079998389915aa485f46c816c3b4cc",
}
# These project-built boundaries must remain diagnosable, even if prebuilt vendors
# supplied stripped libraries and therefore cannot contribute FULL symbols.
PROJECT_SYMBOLS = {
    "libvoiceinput.so",
    "libs1mini.so",
    "libharper.so",
    "libphonon_jni.so",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify_pinned_payload(name, data):
    if name in PINNED_PAYLOADS:
        require(
            hashlib.sha256(data).hexdigest() == PINNED_PAYLOADS[name],
            f"Pinned payload bytes changed: {name}",
        )
    if name == "libs1mini.so":
        require(
            b"ngramPoolBytes" not in data,
            "Experimental S1 speculation must remain disabled in normal releases",
        )


def inspect(data, readelf, directory, allow_missing_build_id=False):
    target = directory / "library.so"
    target.write_bytes(data)
    output = subprocess.check_output(
        [str(readelf), "-SW", "-lW", "-n", str(target)], text=True
    )
    sections = set(re.findall(r"\[\s*\d+\]\s+(\S+)", output))
    alignment = [
        int(line.split()[-1], 16)
        for line in output.splitlines()
        if line.strip().startswith("LOAD ")
    ]
    build_id = re.search(r"Build ID: (\w+)", output)
    if not alignment or (build_id is None and not allow_missing_build_id):
        raise ValueError("ELF missing build ID or LOAD segments")
    return sections, alignment, build_id.group(1) if build_id else None


def native_entries(archive):
    entries = [
        entry
        for entry in archive.infolist()
        if entry.filename.startswith("lib/") and not entry.is_dir()
    ]
    expected = {f"lib/arm64-v8a/{name}" for name in REQUIRED}
    actual = {entry.filename for entry in entries}
    require(
        actual == expected and len(entries) == len(expected),
        f"Native library set changed: missing={sorted(expected - actual)}, "
        f"unexpected={sorted(actual - expected)}",
    )
    return entries


def verify(apk_path, symbols_path, readelf, baseline_path=None):
    with (
        zipfile.ZipFile(apk_path) as apk,
        zipfile.ZipFile(symbols_path) as symbols,
        tempfile.TemporaryDirectory() as temporary,
    ):
        directory = Path(temporary)
        require(
            "assets/tiny_en_acft_q8_0.bin.not.tflite" not in apk.namelist(),
            "Legacy English Whisper weights must not be bundled",
        )
        for name in PINNED_PAYLOADS:
            if name.startswith("assets/"):
                verify_pinned_payload(name, apk.read(name))
        entries = native_entries(apk)
        for entry in entries:
            name = Path(entry.filename).name
            require(
                entry.compress_type == zipfile.ZIP_DEFLATED,
                f"Legacy compressed/extracted packaging lost: {name}",
            )
            data = apk.read(entry)
            verify_pinned_payload(name, data)
            if name == "libwhistle_runner.so":
                continue
            # The exact hash-pinned ORT prebuilt has no GNU build ID.
            sections, alignment, build_id = inspect(
                data,
                readelf,
                directory,
                allow_missing_build_id=name == "libonnxruntime.so",
            )
            require(
                not any(
                    section.startswith((".debug_", ".zdebug_")) for section in sections
                ),
                f"Debug sections still shipped in {name}",
            )
            require(min(alignment) >= 0x4000, f"16 KiB LOAD alignment lost: {name}")
            matches = [
                item
                for item in symbols.namelist()
                if item
                in (
                    f"arm64-v8a/{name}",
                    f"arm64-v8a/{name}.dbg",
                    f"arm64-v8a/{name}.sym",
                )
            ]
            require(len(matches) <= 1, f"Duplicate symbols for {name}")
            require(
                matches or name not in PROJECT_SYMBOLS,
                f"Missing project symbols: {name}",
            )
            if matches:
                require(
                    build_id is not None,
                    f"Cannot match symbols without build ID: {name}",
                )
                debug_sections, _, debug_id = inspect(
                    symbols.read(matches[0]), readelf, directory
                )
                require(
                    debug_id == build_id,
                    f"Symbols do not match shipped build ID: {name}",
                )
                require(
                    ".symtab" in debug_sections, f"Missing native symbol table: {name}"
                )
                if name in PROJECT_SYMBOLS:
                    require(
                        {".debug_info", ".debug_line"} <= debug_sections,
                        f"Missing FULL source/line debug information: {name}",
                    )
        compressed = sum(entry.compress_size for entry in entries)
        extracted = sum(entry.file_size for entry in entries)
        print(
            f"Verified {len(entries)} ARM64 libraries, pinned runtimes/notices, S1 speculation off, stripping and matching symbols"
        )
        print(
            f"APK={apk_path.stat().st_size}; compressed native={compressed}; extracted native={extracted} bytes"
        )
        if baseline_path:
            with zipfile.ZipFile(baseline_path) as baseline:
                old_entries = native_entries(baseline)
                print(
                    f"Baseline APK={baseline_path.stat().st_size}; "
                    f"compressed native={sum(entry.compress_size for entry in old_entries)}; "
                    f"extracted native={sum(entry.file_size for entry in old_entries)} bytes"
                )
                print(
                    f"Change: APK={apk_path.stat().st_size - baseline_path.stat().st_size}; "
                    f"compressed native={compressed - sum(entry.compress_size for entry in old_entries)}; "
                    f"extracted native={extracted - sum(entry.file_size for entry in old_entries)} bytes"
                )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("symbols", type=Path)
    parser.add_argument("--readelf", type=Path, required=True)
    parser.add_argument("--baseline", type=Path)
    args = parser.parse_args()
    verify(args.apk, args.symbols, args.readelf, args.baseline)
