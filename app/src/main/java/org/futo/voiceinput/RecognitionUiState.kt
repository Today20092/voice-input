package org.futo.voiceinput

enum class MagnitudeState {
    NOT_TALKED_YET,
    MIC_MAY_BE_BLOCKED,
    TALKING,
    ENDING_SOON_VAD,
    ENDING_SOON_30S
}

enum class RecognitionUiPhase { Inactive, Loading, Recording, Processing, Cleaning }

/** Presentation only: provisional text never becomes a delivered result here. */
data class RecognitionUiState(
    val phase: RecognitionUiPhase = RecognitionUiPhase.Inactive,
    val modelName: String? = null,
    val bars: List<Pair<Float, Float>> = emptyList(),
    val magnitude: MagnitudeState = MagnitudeState.NOT_TALKED_YET,
    val partialText: String = "",
    val statusText: String? = null
) {
    val isRecording: Boolean get() = phase == RecognitionUiPhase.Recording

    fun start(modelName: String?) = RecognitionUiState(RecognitionUiPhase.Loading, modelName)
    fun end() = RecognitionUiState()
    fun recording() = if (phase == RecognitionUiPhase.Loading) {
        copy(phase = RecognitionUiPhase.Recording, statusText = null)
    } else this
    fun waveform(bars: List<Pair<Float, Float>>, magnitude: MagnitudeState) =
        if (isRecording) copy(bars = bars, magnitude = magnitude) else this

    fun partial(text: String) =
        if (phase == RecognitionUiPhase.Recording || phase == RecognitionUiPhase.Processing) {
            copy(partialText = text)
        } else this

    fun status(text: String?, streaming: Boolean = false) =
        if (phase == RecognitionUiPhase.Inactive || phase == RecognitionUiPhase.Cleaning ||
            (streaming && !isRecording)) this else copy(statusText = text)

    fun processing(text: String) = if (isRecording) {
        copy(phase = RecognitionUiPhase.Processing, statusText = text)
    } else this

    fun cleaning(text: String) = if (phase == RecognitionUiPhase.Processing) {
        copy(phase = RecognitionUiPhase.Cleaning, statusText = text)
    } else this
}
