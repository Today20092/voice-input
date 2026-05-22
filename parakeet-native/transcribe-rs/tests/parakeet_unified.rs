use std::path::PathBuf;
use transcribe_rs::engines::parakeet::{
    ParakeetArchitecture, ParakeetEngine, ParakeetModelParams, QuantizationType,
};
use transcribe_rs::TranscriptionEngine;

fn unified_model_path() -> PathBuf {
    std::env::var("PARAKEET_UNIFIED_ONNX_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("models/parakeet-unified-en-0.6b-onnx"))
}

#[test]
#[ignore = "requires exported nvidia/parakeet-unified-en-0.6b ONNX assets"]
fn test_jfk_transcription_unified() {
    let mut engine = ParakeetEngine::new();
    engine
        .load_model_with_params(
            &unified_model_path(),
            ParakeetModelParams {
                quantization: QuantizationType::Int8,
                architecture: ParakeetArchitecture::RnntUnified,
            },
        )
        .expect("Failed to load unified Parakeet model");

    let result = engine
        .transcribe_file(&PathBuf::from("samples/jfk.wav"), None)
        .expect("Failed to transcribe JFK sample");

    assert!(!result.text.trim().is_empty());
    assert!(
        result.text.chars().any(|ch| ch.is_ascii_uppercase()),
        "expected capitalization smoke signal in {:?}",
        result.text
    );
    assert!(
        result.text.contains('.') || result.text.contains(',') || result.text.contains('?'),
        "expected punctuation smoke signal in {:?}",
        result.text
    );
}

#[test]
#[ignore = "requires exported nvidia/parakeet-unified-en-0.6b ONNX assets"]
fn test_empty_input_returns_empty_text_unified() {
    let mut engine = ParakeetEngine::new();
    engine
        .load_model_with_params(
            &unified_model_path(),
            ParakeetModelParams {
                quantization: QuantizationType::Int8,
                architecture: ParakeetArchitecture::RnntUnified,
            },
        )
        .expect("Failed to load unified Parakeet model");

    let result = engine
        .transcribe_samples(Vec::new(), None)
        .expect("Failed to transcribe empty input");

    assert_eq!(result.text, "");
}

#[test]
#[ignore = "requires exported nvidia/parakeet-unified-en-0.6b ONNX assets"]
fn test_long_input_chunking_does_not_crash_unified() {
    let mut engine = ParakeetEngine::new();
    engine
        .load_model_with_params(
            &unified_model_path(),
            ParakeetModelParams {
                quantization: QuantizationType::Int8,
                architecture: ParakeetArchitecture::RnntUnified,
            },
        )
        .expect("Failed to load unified Parakeet model");

    let result = engine
        .transcribe_samples(vec![0.0; 75 * 16_000], None)
        .expect("Failed to transcribe long input");

    assert!(result.text.is_empty() || result.text.is_ascii());
}
