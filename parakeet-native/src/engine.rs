use once_cell::sync::Lazy;
use std::sync::{Arc, Mutex};
use transcribe_rs::engines::parakeet::{ParakeetEngine, ParakeetModelParams};
use transcribe_rs::TranscriptionEngine;

use jni::objects::JObject;
use jni::JNIEnv;

use crate::assets;

static GLOBAL_ENGINE: Lazy<Mutex<Option<Arc<Mutex<ParakeetEngine>>>>> =
    Lazy::new(|| Mutex::new(None));

pub fn is_loaded() -> bool {
    GLOBAL_ENGINE.lock().unwrap().is_some()
}

pub fn ensure_loaded(env: &mut JNIEnv, context: &JObject) -> Result<(), String> {
    if is_loaded() {
        return Ok(());
    }

    let model_dir = assets::model_dir(env, context).map_err(|e| e.to_string())?;
    let mut engine = ParakeetEngine::new();
    engine
        .load_model_with_params(&model_dir, ParakeetModelParams::int8())
        .map_err(|e| e.to_string())?;

    *GLOBAL_ENGINE.lock().unwrap() = Some(Arc::new(Mutex::new(engine)));
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
}
