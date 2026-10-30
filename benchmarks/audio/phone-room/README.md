# Phone room benchmark recordings

Three original phone microphone recordings supplied by the repository maintainer on October 10, 2026. The maintainer explicitly authorized repository storage, public reuse, and future benchmarking. This permission applies to these recordings.

Recording conditions reported by the speaker: normal phone microphone, indoors, with air conditioning and background room noise. Recorder processing settings and exact microphone distance were not independently verified.

The matching reference passages are recording one, two, and three in [reference scripts](reference-scripts.md). These are the scripts supplied for this handoff, rather than the separate four-passage suite in `docs/benchmark-recording-scripts.md`. References currently assume the scripts were read verbatim; listening to check spoken deviations is pending.

| Clip | Original filename | Duration in seconds | Bytes | SHA-256 |
| --- | --- | ---: | ---: | --- |
| `benchmark-01.m4a` | Everyday dictation.m4a | 25.279604 | 818244 | `f1d1c0e2ed870f83adb433eb5df0828acf876ed9625d0fc5240e7c3578b5565d` |
| `benchmark-02.m4a` | Names numbers and similar sounds.m4a | 38.847396 | 1256677 | `b124316ed9716cd7614d3be45c6e96d912c679b10c272f9ebbf0fcbbc7229ec5` |
| `benchmark-03.m4a` | Longer technical dictation.m4a | 75.796146 | 2450650 | `3d61235d6ac4382da52282efb15979822fdbc9fefc2a3c712c27377b0eccc638` |

Metadata inspected with ffprobe: all files contain AAC audio, 48,000 Hz, two channels. Audio stream bitrates are 256,021, 256,016, and 256,009 bits per second respectively. Originals were copied without conversion, trimming, or noise removal. Copy integrity was verified against the source files using SHA-256.

Canonical 16 kHz mono inputs, conversion settings, sample counts, and machine-readable suite manifest remain pending. Generate them once outside measured model timings and reuse identical samples across models, as specified in [the benchmark plan](../../../docs/plans/recognition-model-benchmark.md).

No recognition accuracy, cold or warm timings, model unload timings, or device performance results have been measured from this handoff. This small suite describes one speaker in one room.
