# Voice Input

Offline speech recognition for entering dictated text on Android.

## Language

**Live transcription**:
Recognition text that is updated while the speaker is still talking and finalized when the utterance ends.
_Avoid_: Real-time transcription, responsive transcription

**Streaming profile**:
A user-selected balance between how quickly live transcription updates and how accurately it recognizes speech.
_Avoid_: Latency, chunk size

**Final-only transcription**:
Recognition text produced only after the utterance ends, without intermediate text while the speaker is talking.
_Avoid_: Offline model, non-streaming model

**Recognition model**:
A user-selectable set of local speech-recognition assets with its own language, accuracy, latency, and storage characteristics.
_Avoid_: Backend, engine, model format

**Installed model**:
A recognition model whose complete validated assets are retained on the device and can be selected without downloading again.
_Avoid_: Selected model, active model

**Recognition language**:
The language a recognition model is instructed to transcribe, either selected by the user or inferred automatically from speech.
_Avoid_: App language, keyboard language

**Performance class**:
An advisory Light, Balanced, or Demanding rating based on measured recognition speed and memory use, without preventing installation.
_Avoid_: Device compatibility, minimum phone

**Catching up**:
A live-transcription state where recognition is behind recorded speech; audio remains complete while intermediate text updates may pause.
_Avoid_: Frozen, failed, dropping audio

**Model artifact**:
An immutable, hash-checked file belonging to a recognition model, which may be hosted by its original publisher or a community converter.
_Avoid_: Model, unverified download

**Transcript cleanup**:
An optional on-device transformation of a final raw English recognition transcript into clean written text before personal vocabulary corrections and insertion.
_Avoid_: Recognition model, transcription model, speech cleanup

**Transcript-inclusive diagnostics**:
An explicitly enabled diagnostic mode that remains active until the user disables it. It operates only while S1-mini cleanup is enabled and English has been established. For every finalized eligible run, it captures the raw transcript, cleaned transcript, and final delivered transcript as separately labeled stages. If cleanup fails or is bypassed, it records the raw transcript and reason while marking later stages as not produced rather than as empty text. Captures live only in Android private app storage, are limited to the latest ten runs, and expire after seven days. The diagnostics UI can view each stage, delete an individual run, or clear all captures. Disabling capture stops future collection but does not delete unexpired captures. It records actual behavior for troubleshooting; it does not collect an expected rewrite or provide a model-correction workflow.
_Avoid_: Transcript logging, transcription content, empty transcript

**Final delivered transcript**:
The exact text Voice Input commits through the IME or returns to a recognition-activity caller after cleanup and personal vocabulary processing. It does not describe how the receiving application may subsequently render or modify that text.
_Avoid_: Inserted transcript, displayed transcript

**Standard diagnostics**:
Technical cleanup metadata that never includes transcript or audio content. Standard diagnostics remain separately exportable even when transcript-inclusive diagnostics have been captured.
_Avoid_: Redacted diagnostics

**Transcript-inclusive diagnostic export**:
An explicitly confirmed, plainly readable ZIP named to include `WITH-TRANSCRIPTS`, containing transcript-inclusive captures and technical cleanup metadata. Before export, the app reports how many captured runs will be included. The archive and its README visibly distinguish it from standard diagnostics and warn that it contains dictated text. It never includes audio, clipboard contents, surrounding text from another app, or application names. An unreadable capture is skipped and reported rather than aborting the export.
_Avoid_: Standard diagnostics, encrypted backup

**Transcript capture safety boundary**:
Transcript text is written atomically only to dedicated private capture records; it must never enter Logcat, crash messages, or ordinary rolling diagnostics. Capture failures cannot interrupt cleanup or delivery. Each transcript stage is limited to 100,000 characters and records its original length and whether it was truncated. Expired captures are purged opportunistically on app launch, capture, diagnostics access, or export rather than by a background job.
_Avoid_: Best-effort logging, background retention job
