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
