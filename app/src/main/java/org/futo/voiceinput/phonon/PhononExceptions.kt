package org.futo.voiceinput.phonon

// JNI throws these types so standard diagnostics never need native exception messages.
class PhononAudioException(message: String) : IllegalArgumentException(message)
class PhononInferenceException(message: String) : IllegalStateException(message)
