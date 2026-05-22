mod assets;
mod engine;

use jni::objects::{JClass, JFloatArray, JObject};
use jni::sys::{jboolean, jfloatArray, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_org_futo_voiceinput_parakeet_ParakeetNative_init(
    mut env: JNIEnv,
    _class: JClass,
    context: JObject,
) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );

    if let Err(err) = engine::ensure_loaded(&mut env, &context) {
        let _ = env.throw_new("java/lang/IllegalStateException", err);
    }
}

#[no_mangle]
pub extern "system" fn Java_org_futo_voiceinput_parakeet_ParakeetNative_isLoaded(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if engine::is_loaded() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_org_futo_voiceinput_parakeet_ParakeetNative_transcribe(
    mut env: JNIEnv,
    _class: JClass,
    samples: jfloatArray,
) -> jstring {
    let samples_array = unsafe { JFloatArray::from_raw(samples) };
    let samples = match env.get_array_length(&samples_array) {
        Ok(len) => {
            let mut samples = vec![0.0f32; len as usize];
            if let Err(err) = env.get_float_array_region(&samples_array, 0, &mut samples) {
                let _ = env.throw_new("java/lang/IllegalArgumentException", err.to_string());
                return std::ptr::null_mut();
            }
            samples
        }
        Err(err) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", err.to_string());
            return std::ptr::null_mut();
        }
    };

    match engine::transcribe(samples) {
        Ok(text) => env
            .new_string(text)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(err) => {
            let _ = env.throw_new("java/lang/IllegalStateException", err);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_futo_voiceinput_parakeet_ParakeetNative_close(
    _env: JNIEnv,
    _class: JClass,
) {
    engine::close();
}

#[no_mangle]
pub extern "system" fn Java_org_futo_voiceinput_parakeet_ParakeetNative_markIdle(
    _env: JNIEnv,
    _class: JClass,
) {
    engine::mark_idle();
}

#[no_mangle]
pub extern "system" fn Java_org_futo_voiceinput_parakeet_ParakeetNative_unloadIfIdle(
    _env: JNIEnv,
    _class: JClass,
    timeout_ms: jlong,
) -> jboolean {
    if timeout_ms < 0 {
        return JNI_FALSE;
    }

    if engine::unload_if_idle(timeout_ms as u64) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}
