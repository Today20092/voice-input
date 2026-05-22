use jni::objects::{JObject, JObjectArray};
use jni::JNIEnv;
use std::path::PathBuf;

const ASSET_DIR: &str = "parakeet-tdt-0.6b-v3-int8";
const DOWNLOAD_COMPLETE_MARKER: &str = ".download_complete";

pub fn model_dir(env: &mut JNIEnv, context: &JObject) -> anyhow::Result<PathBuf> {
    let files_dir_obj = env
        .call_method(context, "getFilesDir", "()Ljava/io/File;", &[])?
        .l()?;
    let path_str_obj = env
        .call_method(
            &files_dir_obj,
            "getAbsolutePath",
            "()Ljava/lang/String;",
            &[],
        )?
        .l()?;
    let path_string: String = env.get_string(&path_str_obj.into())?.into();

    let model_dir = PathBuf::from(path_string).join(ASSET_DIR);
    let marker_file = model_dir.join(DOWNLOAD_COMPLETE_MARKER);

    if marker_file.exists() {
        return Ok(model_dir);
    }

    if try_extract_packaged_assets(env, context, &model_dir).is_ok() && marker_file.exists() {
        return Ok(model_dir);
    }

    anyhow::bail!("Parakeet model is not downloaded")
}

fn try_extract_packaged_assets(
    env: &mut JNIEnv,
    context: &JObject,
    model_dir: &PathBuf,
) -> anyhow::Result<()> {
    let asset_manager_obj = env
        .call_method(
            context,
            "getAssets",
            "()Landroid/content/res/AssetManager;",
            &[],
        )?
        .l()?;

    let names = list_assets(env, &asset_manager_obj, ASSET_DIR)?;
    if names.is_empty() {
        anyhow::bail!("Packaged Parakeet assets are not present");
    }

    if model_dir.exists() {
        let _ = std::fs::remove_dir_all(model_dir);
    }
    std::fs::create_dir_all(model_dir)?;

    let target_root = model_dir
        .parent()
        .ok_or_else(|| anyhow::anyhow!("Parakeet model directory has no parent"))?
        .to_path_buf();

    copy_assets_recursively(env, &asset_manager_obj, ASSET_DIR, &target_root)?;
    std::fs::write(model_dir.join(DOWNLOAD_COMPLETE_MARKER), "ok")?;

    Ok(())
}

fn list_assets(
    env: &mut JNIEnv,
    asset_manager: &JObject,
    path: &str,
) -> anyhow::Result<Vec<String>> {
    let path_jstring = env.new_string(path)?;
    let list_array_obj = env
        .call_method(
            asset_manager,
            "list",
            "(Ljava/lang/String;)[Ljava/lang/String;",
            &[(&path_jstring).into()],
        )?
        .l()?;

    let list_array: JObjectArray = list_array_obj.into();
    let len = env.get_array_length(&list_array)?;
    let mut names = Vec::with_capacity(len as usize);

    for i in 0..len {
        let file_name_obj = env.get_object_array_element(&list_array, i)?;
        let file_name: String = env.get_string(&file_name_obj.into())?.into();
        names.push(file_name);
    }

    Ok(names)
}

fn copy_assets_recursively(
    env: &mut JNIEnv,
    asset_manager: &JObject,
    path: &str,
    target_root: &PathBuf,
) -> anyhow::Result<()> {
    let names = list_assets(env, asset_manager, path)?;

    if names.is_empty() {
        return copy_asset_file(env, asset_manager, path, target_root);
    }

    std::fs::create_dir_all(target_root.join(path))?;

    for file_name in names {
        copy_assets_recursively(
            env,
            asset_manager,
            &format!("{}/{}", path, file_name),
            target_root,
        )?;
    }

    Ok(())
}

fn copy_asset_file(
    env: &mut JNIEnv,
    asset_manager: &JObject,
    asset_path: &str,
    target_root: &PathBuf,
) -> anyhow::Result<()> {
    let path_jstring = env.new_string(asset_path)?;
    let stream_obj = env
        .call_method(
            asset_manager,
            "open",
            "(Ljava/lang/String;)Ljava/io/InputStream;",
            &[(&path_jstring).into()],
        )?
        .l()?;
    let target_file_path = target_root.join(asset_path);

    let mut file = std::fs::File::create(&target_file_path)?;
    let mut buffer = [0u8; 8192];
    let buffer_j = env.new_byte_array(8192)?;

    loop {
        let bytes_read = env
            .call_method(&stream_obj, "read", "([B)I", &[(&buffer_j).into()])?
            .i()?;

        if bytes_read == -1 {
            break;
        }

        let bytes_read_usize = bytes_read as usize;
        let buffer_slice = unsafe {
            std::slice::from_raw_parts_mut(buffer.as_mut_ptr() as *mut i8, bytes_read_usize)
        };
        env.get_byte_array_region(&buffer_j, 0, buffer_slice)?;

        use std::io::Write;
        file.write_all(&buffer[..bytes_read_usize])?;
    }

    env.call_method(&stream_obj, "close", "()V", &[])?;
    Ok(())
}
