use once_cell::sync::Lazy;
use std::sync::{Arc, Mutex};
use std::time::Instant;
use transcribe_rs::engines::parakeet::{
    ParakeetArchitecture, ParakeetEngine, ParakeetModelParams, QuantizationType,
};
use transcribe_rs::TranscriptionEngine;

use jni::objects::JObject;
use jni::JNIEnv;

use crate::assets;

static GLOBAL_ENGINE: Lazy<Mutex<Option<Arc<Mutex<ParakeetEngine>>>>> =
    Lazy::new(|| Mutex::new(None));
static LAST_IDLE: Lazy<Mutex<Option<Instant>>> = Lazy::new(|| Mutex::new(None));

pub fn is_loaded() -> bool {
    GLOBAL_ENGINE.lock().unwrap().is_some()
}

pub fn ensure_loaded(env: &mut JNIEnv, context: &JObject) -> Result<(), String> {
    if is_loaded() {
        *LAST_IDLE.lock().unwrap() = None;
        return Ok(());
    }

    let model_dir = assets::model_dir(env, context).map_err(|e| e.to_string())?;
    let mut engine = ParakeetEngine::new();
    engine
        .load_model_with_params(
            &model_dir,
            ParakeetModelParams {
                quantization: QuantizationType::Int8,
                architecture: ParakeetArchitecture::TdtFrameStep,
            },
        )
        .map_err(|e| e.to_string())?;

    *GLOBAL_ENGINE.lock().unwrap() = Some(Arc::new(Mutex::new(engine)));
    *LAST_IDLE.lock().unwrap() = None;
    Ok(())
}

pub fn transcribe(samples: Vec<f32>) -> Result<String, String> {
    let engine = GLOBAL_ENGINE
        .lock()
        .unwrap()
        .clone()
        .ok_or_else(|| "Parakeet model is not loaded".to_string())?;

    let mut engine = engine.lock().unwrap();
    let result = engine
        .transcribe_samples(samples, None)
        .map_err(|e| e.to_string())?;

    Ok(result.text.trim().to_string())
}

pub fn close() {
    *GLOBAL_ENGINE.lock().unwrap() = None;
    *LAST_IDLE.lock().unwrap() = None;
}

pub fn mark_idle() {
    *LAST_IDLE.lock().unwrap() = Some(Instant::now());
}

pub fn unload_if_idle(timeout_ms: u64) -> bool {
    let should_unload = LAST_IDLE
        .lock()
        .unwrap()
        .map(|idle_at| idle_at.elapsed().as_millis() >= timeout_ms as u128)
        .unwrap_or(false);

    if should_unload {
        close();
    }

    should_unload
}
