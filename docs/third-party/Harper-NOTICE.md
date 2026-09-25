# Harper native integration

Harper 2.11.0 by the Harper contributors and Automattic is used under Apache-2.0.

- Source: https://github.com/Automattic/harper
- Pinned revision: `c821f511f83c24401993e207f7e5cdb51ae872e6`
- License: https://github.com/Automattic/harper/blob/v2.11.0/LICENSE
- Local wrapper: `app/src/main/rust/harper_android`

The wrapper uses only explicitly enabled punctuation, spacing, and capitalization rules. It disables Harper's optional thesaurus and concurrency features. Dependencies are resolved in the committed Cargo.lock. The release workflow collects the exact locked dependency license texts with `tools/harper_notices.py` and bundles them in `assets/HARPER-NOTICES.txt` in the APK. The source dependencies are not modified.

Development prerequisites are Rust 1.98.1 and the `aarch64-linux-android` target in addition to the existing Android NDK. Before packaging outside GitHub Actions, run `python3 tools/harper_notices.py` to bundle the dependency licenses. CMake invokes Cargo using the NDK compiler; native intermediates remain in the Android build directory. No JavaScript or WebAssembly host is added.
