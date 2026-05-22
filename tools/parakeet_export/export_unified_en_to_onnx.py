#!/usr/bin/env python
import argparse
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

from huggingface_hub import HfApi, hf_hub_download


MODEL_REPO = "nvidia/parakeet-unified-en-0.6b"
ARCHITECTURE = "rnnt_unified"
DEFAULT_OPSET = 17


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_checksums(output_dir: Path) -> dict[str, str]:
    hashes = {}
    for path in sorted(output_dir.iterdir()):
        if path.is_file() and path.name not in {"manifest.json", "checksums.sha256"}:
            hashes[path.name] = sha256(path)

    checksum_lines = [f"{value}  {name}\n" for name, value in hashes.items()]
    (output_dir / "checksums.sha256").write_text("".join(checksum_lines), encoding="utf-8")
    return hashes


def export_with_nemo(nemo_path: Path, output_dir: Path, opset: int) -> None:
    import nemo.collections.asr as nemo_asr

    model = nemo_asr.models.ASRModel.restore_from(str(nemo_path), map_location="cpu")
    model.eval()

    config_path = output_dir / "config.json"
    config_path.write_text(model.cfg.to_json(), encoding="utf-8")

    tokenizer = getattr(model, "tokenizer", None)
    if tokenizer is None:
        raise RuntimeError("Model does not expose a tokenizer; cannot write vocab.txt")

    vocab = getattr(tokenizer, "vocab", None)
    if vocab is None and hasattr(tokenizer, "tokenizer"):
        vocab = getattr(tokenizer.tokenizer, "vocab", None)
    if callable(vocab):
        vocab = vocab()
    if not vocab:
        raise RuntimeError("Could not read tokenizer vocabulary")

    if isinstance(vocab, dict):
        tokens = sorted(vocab.items(), key=lambda item: item[1])
    else:
        tokens = [(token, idx) for idx, token in enumerate(vocab)]

    blank_id = getattr(model.decoding, "blank_id", None)
    with (output_dir / "vocab.txt").open("w", encoding="utf-8") as handle:
        for token, idx in tokens:
            handle.write(f"{token} {idx}\n")
        if blank_id is not None and all(token != "<blk>" for token, _ in tokens):
            handle.write(f"<blk> {blank_id}\n")

    # NeMo export APIs differ across ASR model classes. Prefer component exports
    # when present; otherwise fail before producing Android-facing partial files.
    component_exports = [
        ("encoder", "encoder-model.onnx"),
        ("decoder", "decoder_joint-model.onnx"),
        ("joint", "decoder_joint-model.onnx"),
        ("preprocessor", "preprocessor.onnx"),
    ]
    exported_any = False
    for attr, filename in component_exports:
        component = getattr(model, attr, None)
        if component is None or not hasattr(component, "export"):
            continue
        target = output_dir / filename
        if target.exists() and attr == "joint":
            continue
        component.export(str(target), onnx_opset_version=opset)
        exported_any = True

    if not exported_any and hasattr(model, "export"):
        model.export(str(output_dir / "parakeet-unified-en-0.6b.onnx"), onnx_opset_version=opset)
        raise RuntimeError(
            "NeMo produced a monolithic ONNX export. Split encoder, decoder_joint, "
            "and preprocessor exports are still required for the Android runtime."
        )

    required = ["encoder-model.onnx", "decoder_joint-model.onnx", "preprocessor.onnx"]
    missing = [name for name in required if not (output_dir / name).exists()]
    if missing:
        raise RuntimeError(f"Missing required ONNX exports: {', '.join(missing)}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-repo", default=MODEL_REPO)
    parser.add_argument("--output-dir", type=Path, default=Path("../../artifacts/parakeet-unified-en-0.6b-onnx"))
    parser.add_argument("--opset", type=int, default=DEFAULT_OPSET)
    parser.add_argument("--clean", action="store_true")
    args = parser.parse_args()

    output_dir = args.output_dir.resolve()
    if args.clean and output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    api = HfApi()
    info = api.model_info(args.model_repo)
    revision = info.sha
    nemo_path = Path(
        hf_hub_download(
            repo_id=args.model_repo,
            filename="parakeet-unified-en-0.6b.nemo",
            revision=revision,
        )
    )

    export_with_nemo(nemo_path, output_dir, args.opset)
    hashes = write_checksums(output_dir)

    import nemo

    manifest = {
        "source_model_repo": args.model_repo,
        "source_revision": revision,
        "nemo_version": getattr(nemo, "__version__", "unknown"),
        "onnx_opset": args.opset,
        "export_timestamp": datetime.now(timezone.utc).isoformat(),
        "sample_rate": 16000,
        "vocabulary_size": sum(1 for _ in (output_dir / "vocab.txt").open("r", encoding="utf-8")),
        "architecture": ARCHITECTURE,
        "files": hashes,
    }
    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
