# Model catalog release verification

Run the automated packaging check before collecting physical-device results:

```powershell
.\gradlew.bat :app:verifyDevDebugApk
```

It builds the dev debug APK, requires arm64-only native packaging, requires one ONNX Runtime and one Sherpa-ONNX JNI library, and reports the APK size and complete native-library list.

Compare it with a pre-Sherpa APK when that baseline is available:

```powershell
.\gradlew.bat :app:verifyDevDebugApk -PbaselineApk=C:\path\to\baseline.apk
```

The comparison reports the byte-size delta and added or removed native libraries. No pre-Sherpa baseline is stored in this repository, so that release result remains pending until one is supplied.

Last local dev-debug check (2026-07-21): 140,903,084 bytes; arm64-v8a; six native libraries (`libmoonshine-jni.so`, `libmoonshine.so`, `libonnxruntime.so`, `libsherpa-onnx-jni.so`, `libvad_jni.so`, and `libvoiceinput.so`). Re-run for the release candidate rather than treating this development build as final evidence.

## Physical-device results

Do not replace measurements with emulator results. Test a recent S24/S25-class phone and one meaningfully slower arm64 phone with the same release candidate and utterance set.

| Device / build | Profile | First useful partial (ms) | Final after stop (ms) | Real-time factor | Peak memory (MB) | Sustained thermals | Maximum backlog (ms) | Audio preserved / caught up |
| --- | --- | ---: | ---: | ---: | ---: | --- | ---: | --- |
| Recent S24/S25-class phone — pending | Low latency (80 ms) | | | | | | | |
| Recent S24/S25-class phone — pending | Balanced (160 ms) | | | | | | | |
| Recent S24/S25-class phone — pending | Accuracy (560 ms) | | | | | | | |
| Slower arm64 phone — pending | Low latency (80 ms) | | | | | | | |
| Slower arm64 phone — pending | Balanced (160 ms) | | | | | | | |
| Slower arm64 phone — pending | Accuracy (560 ms) | | | | | | | |

Balanced acceptance on the recent phone is a useful partial within one second, throughput at least as fast as incoming audio, and a caught-up final within one second after stopping. On the slower phone, record every failure mode and confirm audio is preserved until the recognizer catches up or offers a safe installed-model switch.

## Manual regression checklist

- Download confirmation, cellular warning, insufficient space, corruption, retention, inactive deletion, and manual update.
- Moonshine, Whisper, IME, recognition activity, VAD, cancellation, personal vocabulary, and error handling.
- Model-card wording matches observed behavior: live versus final-only, buffered versus cache-aware, English versus multilingual.
- Source and license notices are present for every downloadable model.
- Review Light, Balanced, and Demanding only after both devices have measured memory and real-time-factor results; the labels remain advisory and never gate installation.

## Source and license notices

- Moonshine English models: [Moonshine AI source and MIT license](https://github.com/moonshine-ai/moonshine/blob/main/LICENSE).
- Nemotron English: [NVIDIA model source](https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b) and [NVIDIA Open Model License](https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/).
- Nemotron 3.5 multilingual: [NVIDIA model source](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b) and [OpenMDW 1.1](https://www.nvidia.com/en-us/agreements/enterprise-software/open-model-data-weight-license/).
- Parakeet TDT: [NVIDIA model source](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Parakeet Unified: [NVIDIA model source](https://huggingface.co/nvidia/parakeet-unified-en-0.6b) under the [NVIDIA Open Model License](https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/).
- Sherpa-ONNX runtime and model exports: [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) under [Apache 2.0](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE).
