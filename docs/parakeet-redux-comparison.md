# Redux, Ultra, and the app's Orukeet

Checked October 4, 2026. Integration starts from master after
`v1.4.6-share-beta.5`, including the Nemotron nested-download fix.

## Published accuracy

Word error rate, lower is better. Redux and Ultra below were measured together
by Moondream. Orukeet was evaluated separately by Oruk AI, with different
normalization and aggregation. Its scores cannot establish a head-to-head rank.

| Evaluation | Redux | Ultra | Orukeet |
| --- | ---: | ---: | ---: |
| Seven English sets, mean WER | 6.55% | 5.80% | Not reported on this same suite |
| FLEURS, 25 languages | 10.56% mean | 9.55% mean | 9.85% pooled, separate evaluation |
| Business speech | 6.96% | 5.79% | No matching evaluation |
| Background noise | 9.04% | 5.82% | No matching evaluation |
| Eleven TED-LIUM long talks | 2.51% | 1.94% | No matching evaluation |
| LibriSpeech test-clean | 1.96% | 1.41% | 1.46%, separate evaluation |
| LibriSpeech test-other | 4.34% | 2.98% | 2.86%, separate evaluation |

Moondream's results favor Ultra for accuracy, particularly noisy speech.
Orukeet's LibriSpeech test-other result needs extra care: the model card says
that split was used for final adaptation and checkpoint selection.

Sources: [Moondream release post](https://moondream.ai/blog/introducing-parakeet-redux-and-ultra),
[Redux model card](https://huggingface.co/moondream/parakeet-redux),
[Ultra model card](https://huggingface.co/moondream/parakeet-ultra),
[Orukeet model card](https://huggingface.co/oruk/orukeet).

## CPU speed and storage

| Model | Relevant storage | Published speed | Android evidence |
| --- | --- | --- | --- |
| Redux | Photon weights 178 MB; app's packed GGUF 213.3 MB | Photon: 113x real time on 8 EPYC 9575F cores, 38x on M2 CPU | No phone benchmark yet |
| Ultra | Full-precision weights about 1.2 GB; converted F16 GGUF 1.442 GB, Q8 GGUF 941.5 MB | GPU-focused Photon release; parakeet.cpp reports 43.1x for Ultra Q8 on Ryzen 9950X3D in its own small CPU suite | Not integrated or benchmarked here |
| Orukeet | App download 486.8 MB compressed, installed artifacts 671.7 MB | Sherpa-ONNX: median 390 ms/file on a 160-clip M5 Max sample | Existing Android backend, no matched timing here |

These measurements use different machines, runtimes and recordings. Do not
compare the throughput numbers as if they came from one test.

The selected Redux runtime is parakeet.cpp, not Photon. It reports 75.6x for
packed Redux on a 100-utterance LibriSpeech subset on Ryzen 9950X3D, using its
own scoring. Its ARM NEON dot-product kernel has correctness checks under
emulation, but upstream has not measured its speed on physical ARM hardware.
Phones without dot-product support use a slower scalar fallback. Thus CPU
promise is supported, while Android superiority over Orukeet remains unproven.

Sources: [parakeet.cpp ternary implementation and measurements](https://github.com/mudler/parakeet.cpp/blob/781a973e755bec5562ac2a0b04770ddffa36cad2/docs/ternary.md),
[converted artifact manifest](https://github.com/mudler/parakeet.cpp/blob/781a973e755bec5562ac2a0b04770ddffa36cad2/models/MANIFEST.md),
and the model cards above. App sizes come from its pinned download manifests.

## App choice and validation

Redux is an optional beta entry in Model Options. It uses the existing verified
download, selection, deletion and lifecycle flow. It accepts the app's 16 kHz
mono float PCM and returns a final transcript after recording stops. Orukeet
remains the default. S1-mini does not assume Redux's multilingual output is
English when language metadata is absent.

The runtime is pinned to parakeet.cpp `781a973e755bec5562ac2a0b04770ddffa36cad2`.
Its private static GGML is built separately from S1-mini to prevent symbol
collisions. Runtime license is MIT; Redux weights credit Moondream and NVIDIA
under CC BY 4.0. The GGUF conversion is identified in the model's details.

For a fair device comparison, warm both Redux and Orukeet and use the same
recordings, threads and audio preprocessing. Measure cold load time, warm
transcription time, peak memory and normalized WER separately. Include quiet
dictation, noisy speech and each language actually used. Ultra would require
an additional Android integration before it can join that test.

Local validation: ARM64 dev debug APK built; 129 JVM unit tests passed;
`lintDevDebug` completed with no errors and no Redux findings. Android test APK
built, including native-library loading and installed-model transcription
checks. APK contents include `libparakeet_redux.so`; dynamic-symbol inspection
confirms that Redux does not export GGML or C-API symbols. No device was
connected, so instrumentation tests and Android inference remain unrun.
