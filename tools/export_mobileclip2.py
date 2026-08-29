from __future__ import annotations

import argparse
import hashlib
import inspect
import json
import sys
from pathlib import Path
from typing import Any

import numpy as np
import onnx
import onnxruntime as ort
import open_clip
import torch

from mobileclip.modules.common.mobileone import reparameterize_model


# ============================================================
# MobileCLIP2-S0 expected architecture
# ============================================================

MODEL_NAME = "MobileCLIP2-S0"

EXPECTED_IMAGE_SIZE = 256
EXPECTED_CONTEXT_LENGTH = 77
EXPECTED_EMBED_DIM = 512
EXPECTED_VOCAB_SIZE = 49408

IMAGE_ONNX_NAME = "mobileclip2_s0_image.onnx"
TEXT_ONNX_NAME = "mobileclip2_s0_text.onnx"
CONFIG_NAME = "mobileclip2_s0_config.json"
VOCAB_NAME = "mobileclip2_s0_vocab.json"
MERGES_NAME = "mobileclip2_s0_merges.txt"

DEFAULT_OPSET = 18

# Validation tolerances.
MIN_COSINE_SIMILARITY = 0.9999
MAX_ABSOLUTE_ERROR = 1e-3


# ============================================================
# ONNX wrappers
# ============================================================

class ImageEncoder(torch.nn.Module):
    """
    Export only MobileCLIP2's image embedding path.

    Output is already L2-normalized so Android can directly
    compare it against normalized text/image embeddings.
    """

    def __init__(self, model: torch.nn.Module):
        super().__init__()
        self.model = model

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        features = self.model.encode_image(pixel_values)
        features = features / features.norm(
            p=2,
            dim=-1,
            keepdim=True,
        )
        return features


class TextEncoder(torch.nn.Module):
    """
    Export only MobileCLIP2's text embedding path.

    input_ids:
        int64 [batch, 77]

    output:
        float32 [batch, 512]
    """

    def __init__(self, model: torch.nn.Module):
        super().__init__()
        self.model = model

    def forward(self, input_ids: torch.Tensor) -> torch.Tensor:
        features = self.model.encode_text(input_ids)
        features = features / features.norm(
            p=2,
            dim=-1,
            keepdim=True,
        )
        return features


# ============================================================
# Utilities
# ============================================================

def sha256_file(path: Path) -> str:
    h = hashlib.sha256()

    with path.open("rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)

    return h.hexdigest()


def json_serializable(value: Any) -> Any:
    """Convert tuples / torch objects into JSON-compatible values."""

    if isinstance(value, dict):
        return {
            str(k): json_serializable(v)
            for k, v in value.items()
        }

    if isinstance(value, (list, tuple)):
        return [json_serializable(v) for v in value]

    if isinstance(value, Path):
        return str(value)

    if isinstance(value, torch.Tensor):
        return value.detach().cpu().tolist()

    if isinstance(value, np.ndarray):
        return value.tolist()

    if isinstance(value, np.generic):
        return value.item()

    if isinstance(value, (str, int, float, bool)) or value is None:
        return value

    return str(value)


def row_cosine_similarity(
    a: np.ndarray,
    b: np.ndarray,
) -> np.ndarray:
    numerator = np.sum(a * b, axis=-1)

    denominator = (
        np.linalg.norm(a, axis=-1)
        * np.linalg.norm(b, axis=-1)
    )

    return numerator / np.maximum(denominator, 1e-12)


def validate_embedding_match(
    name: str,
    pytorch_output: np.ndarray,
    onnx_output: np.ndarray,
) -> dict:
    if pytorch_output.shape != onnx_output.shape:
        raise RuntimeError(
            f"{name}: output shape mismatch: "
            f"PyTorch={pytorch_output.shape}, "
            f"ONNX={onnx_output.shape}"
        )

    absolute_error = np.abs(
        pytorch_output.astype(np.float64)
        - onnx_output.astype(np.float64)
    )

    max_abs_error = float(absolute_error.max())
    mean_abs_error = float(absolute_error.mean())

    cosine = row_cosine_similarity(
        pytorch_output.astype(np.float64),
        onnx_output.astype(np.float64),
    )

    min_cosine = float(cosine.min())
    mean_cosine = float(cosine.mean())

    print()
    print(f"{name} validation")
    print("-" * 60)
    print(f"Min cosine similarity : {min_cosine:.10f}")
    print(f"Mean cosine similarity: {mean_cosine:.10f}")
    print(f"Max absolute error    : {max_abs_error:.10e}")
    print(f"Mean absolute error   : {mean_abs_error:.10e}")

    passed = (
        min_cosine >= MIN_COSINE_SIMILARITY
        and max_abs_error <= MAX_ABSOLUTE_ERROR
    )

    if not passed:
        raise RuntimeError(
            f"{name} ONNX validation FAILED.\n"
            f"Required cosine >= {MIN_COSINE_SIMILARITY}, "
            f"got {min_cosine}\n"
            f"Required max abs error <= {MAX_ABSOLUTE_ERROR}, "
            f"got {max_abs_error}"
        )

    print("PASS")

    return {
        "min_cosine_similarity": min_cosine,
        "mean_cosine_similarity": mean_cosine,
        "max_absolute_error": max_abs_error,
        "mean_absolute_error": mean_abs_error,
        "passed": True,
    }


# ============================================================
# Tokenizer export
# ============================================================

def export_tokenizer(
    tokenizer,
    vocab_path: Path,
    merges_path: Path,
) -> dict:
    """
    Export the exact SimpleTokenizer vocabulary and BPE merge ranking
    currently being used by OpenCLIP.

    OpenCLIP's SimpleTokenizer exposes:
        tokenizer.encoder
        tokenizer.bpe_ranks

    Therefore we do NOT need to guess or use another CLIP tokenizer.
    """

    if not hasattr(tokenizer, "encoder"):
        raise RuntimeError(
            "Tokenizer does not expose `encoder`. "
            f"Actual type: {type(tokenizer)}"
        )

    if not hasattr(tokenizer, "bpe_ranks"):
        raise RuntimeError(
            "Tokenizer does not expose `bpe_ranks`. "
            f"Actual type: {type(tokenizer)}"
        )

    encoder = tokenizer.encoder
    bpe_ranks = tokenizer.bpe_ranks

    # Sort vocabulary by token ID for deterministic output.
    ordered_vocab = dict(
        sorted(
            encoder.items(),
            key=lambda item: item[1],
        )
    )

    with vocab_path.open(
        "w",
        encoding="utf-8",
        newline="\n",
    ) as f:
        json.dump(
            ordered_vocab,
            f,
            ensure_ascii=False,
            indent=2,
        )
        f.write("\n")

    # bpe_ranks maps:
    #
    #   ("first", "second") -> rank
    #
    # Sort by rank to reconstruct the exact merge order.
    ordered_merges = sorted(
        bpe_ranks.items(),
        key=lambda item: item[1],
    )

    with merges_path.open(
        "w",
        encoding="utf-8",
        newline="\n",
    ) as f:
        # Standard CLIP BPE header.
        f.write("#version: 0.2\n")

        for (first, second), _rank in ordered_merges:
            f.write(f"{first} {second}\n")

    vocab_size = len(encoder)

    if vocab_size != EXPECTED_VOCAB_SIZE:
        raise RuntimeError(
            f"Unexpected vocabulary size: {vocab_size}. "
            f"Expected {EXPECTED_VOCAB_SIZE}."
        )

    context_length = int(tokenizer.context_length)
    sot_id = int(tokenizer.sot_token_id)
    eot_id = int(tokenizer.eot_token_id)

    clean_fn = getattr(tokenizer, "clean_fn", None)

    if clean_fn is not None:
        clean_function_name = getattr(
            clean_fn,
            "__name__",
            str(clean_fn),
        )
    else:
        clean_function_name = None

    print()
    print("Tokenizer")
    print("-" * 60)
    print(f"Type          : {type(tokenizer).__name__}")
    print(f"Vocabulary    : {vocab_size}")
    print(f"BPE merges    : {len(ordered_merges)}")
    print(f"Context length: {context_length}")
    print(f"SOT token ID  : {sot_id}")
    print(f"EOT token ID  : {eot_id}")
    print(f"Clean function: {clean_function_name}")
    print(f"Saved vocab   : {vocab_path}")
    print(f"Saved merges  : {merges_path}")

    return {
        "type": "open_clip_simple_tokenizer",
        "vocab_size": vocab_size,
        "context_length": context_length,
        "sot_token": "<start_of_text>",
        "eot_token": "<end_of_text>",
        "sot_token_id": sot_id,
        "eot_token_id": eot_id,

        # OpenCLIP pads its 77-element tensor with zeros.
        #
        # Important: token 0 is a REAL BPE vocabulary token.
        # It is not a special reserved PAD token.
        "padding_value": 0,
        "padding_is_reserved_token": False,

        "truncate": True,
        "clean_function": clean_function_name,

        "vocab_file": VOCAB_NAME,
        "merges_file": MERGES_NAME,

        # Android implementation must implement OpenAI CLIP's
        # byte-to-unicode mapping before BPE.
        "byte_encoding": "openai_clip_bytes_to_unicode",

        # Current OpenCLIP SimpleTokenizer token pattern.
        "regex": (
            r"<start_of_text>|<end_of_text>"
            r"|'s|'t|'re|'ve|'m|'ll|'d"
            r"|[\p{L}]+"
            r"|[\p{N}]"
            r"|[^\s\p{L}\p{N}]+"
        ),

        "regex_ignore_case": True,

        # OpenCLIP's lower cleaner:
        #
        # ftfy.fix_text
        # -> HTML unescape twice
        # -> trim
        # -> collapse whitespace
        # -> lowercase
        "text_cleaning": [
            "ftfy_fix_text",
            "html_unescape_twice",
            "trim",
            "collapse_whitespace",
            "lowercase",
        ],

        "merge_count": len(ordered_merges),
    }


# ============================================================
# ONNX export
# ============================================================

def torch_onnx_export(
    module: torch.nn.Module,
    dummy_input: torch.Tensor,
    output_path: Path,
    input_name: str,
    output_name: str,
    opset: int,
):
    module.eval()

    kwargs = {
        "export_params": True,
        "opset_version": opset,
        "do_constant_folding": True,
        "input_names": [input_name],
        "output_names": [output_name],
        "dynamic_axes": {
            input_name: {
                0: "batch_size",
            },
            output_name: {
                0: "batch_size",
            },
        },
    }

    # Current PyTorch defaults toward the new dynamo exporter.
    # MobileCLIP/TIMM is generally easier to export through the
    # mature tracing exporter.
    export_signature = inspect.signature(torch.onnx.export)

    if "dynamo" in export_signature.parameters:
        kwargs["dynamo"] = False

    if "external_data" in export_signature.parameters:
        # Both S0 submodels are far below ONNX's 2 GB single-file limit.
        # Force each output to remain one .onnx file.
        kwargs["external_data"] = False

    print()
    print(f"Exporting {output_path.name} ...")

    with torch.no_grad():
        torch.onnx.export(
            module,
            dummy_input,
            str(output_path),
            **kwargs,
        )

    print(f"Checking {output_path.name} ...")

    model = onnx.load(str(output_path))
    onnx.checker.check_model(model)

    print(f"ONNX checker PASS: {output_path}")


# ============================================================
# ONNX Runtime validation
# ============================================================

def run_ort(
    model_path: Path,
    input_name: str,
    data: np.ndarray,
) -> np.ndarray:
    session_options = ort.SessionOptions()

    session = ort.InferenceSession(
        str(model_path),
        sess_options=session_options,
        providers=["CPUExecutionProvider"],
    )

    actual_inputs = [
        x.name
        for x in session.get_inputs()
    ]

    if input_name not in actual_inputs:
        raise RuntimeError(
            f"{model_path.name}: expected input "
            f"`{input_name}`, found {actual_inputs}"
        )

    outputs = session.run(
        None,
        {
            input_name: data,
        },
    )

    if len(outputs) != 1:
        raise RuntimeError(
            f"Expected one output from {model_path.name}, "
            f"got {len(outputs)}"
        )

    return outputs[0]


# ============================================================
# Main exporter
# ============================================================

def main():
    script_path = Path(__file__).resolve()

    # export_mobileclip2.py should be:
    #
    #   D:/MobileImageRetrieval/tools/export_mobileclip2.py
    #
    project_root = script_path.parent.parent

    default_checkpoint = (
        project_root
        / "MobileCLIP2-S0"
        / "mobileclip2_s0.pt"
    )

    default_output_dir = (
        project_root
        / "app"
        / "src"
        / "main"
        / "assets"
        / "models"
    )

    parser = argparse.ArgumentParser(
        description=(
            "Export MobileCLIP2-S0 PyTorch checkpoint "
            "to Android-compatible ONNX assets."
        )
    )

    parser.add_argument(
        "--checkpoint",
        type=Path,
        default=default_checkpoint,
    )

    parser.add_argument(
        "--output-dir",
        type=Path,
        default=default_output_dir,
    )

    parser.add_argument(
        "--opset",
        type=int,
        default=DEFAULT_OPSET,
    )

    args = parser.parse_args()

    checkpoint_path = args.checkpoint.resolve()
    output_dir = args.output_dir.resolve()
    opset = args.opset

    if not checkpoint_path.exists():
        raise FileNotFoundError(
            f"Checkpoint not found:\n{checkpoint_path}"
        )

    output_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    image_onnx_path = output_dir / IMAGE_ONNX_NAME
    text_onnx_path = output_dir / TEXT_ONNX_NAME
    config_path = output_dir / CONFIG_NAME
    vocab_path = output_dir / VOCAB_NAME
    merges_path = output_dir / MERGES_NAME

    print("=" * 70)
    print("MobileCLIP2-S0 ONNX exporter")
    print("=" * 70)

    print()
    print(f"Project root : {project_root}")
    print(f"Checkpoint   : {checkpoint_path}")
    print(f"Output       : {output_dir}")
    print(f"ONNX opset   : {opset}")

    print()
    print("Versions")
    print("-" * 60)
    print(f"Python      : {sys.version.split()[0]}")
    print(f"PyTorch     : {torch.__version__}")
    print(f"OpenCLIP    : {getattr(open_clip, '__version__', 'unknown')}")
    print(f"ONNX        : {onnx.__version__}")
    print(f"ONNX Runtime: {ort.__version__}")

    # --------------------------------------------------------
    # Load model exactly like Apple's MobileCLIP2 example.
    # --------------------------------------------------------

    print()
    print("Loading MobileCLIP2-S0 checkpoint ...")

    model, _, preprocess = open_clip.create_model_and_transforms(
        MODEL_NAME,
        pretrained=str(checkpoint_path),
        image_mean=(0, 0, 0),
        image_std=(1, 1, 1),
    )

    tokenizer = open_clip.get_tokenizer(
        MODEL_NAME
    )

    print("Checkpoint loaded.")

    # Capture preprocessing metadata before reparameterization.
    preprocess_cfg = getattr(
        getattr(model, "visual", None),
        "preprocess_cfg",
        {},
    )

    preprocess_cfg = json_serializable(
        preprocess_cfg
    )

    # --------------------------------------------------------
    # Eval + Apple's mandatory reparameterization step.
    # --------------------------------------------------------

    model.eval()

    print("model.eval() applied.")

    print("Reparameterizing MobileCLIP2 ...")

    model = reparameterize_model(model)

    model.eval()
    model.cpu()

    print("Reparameterization complete.")

    # --------------------------------------------------------
    # Verify tokenizer/model architecture.
    # --------------------------------------------------------

    context_length = int(
        tokenizer.context_length
    )

    if context_length != EXPECTED_CONTEXT_LENGTH:
        raise RuntimeError(
            f"Context length is {context_length}, "
            f"expected {EXPECTED_CONTEXT_LENGTH}"
        )

    # --------------------------------------------------------
    # Export tokenizer.
    # --------------------------------------------------------

    tokenizer_config = export_tokenizer(
        tokenizer,
        vocab_path,
        merges_path,
    )

    # --------------------------------------------------------
    # Build wrappers.
    # --------------------------------------------------------

    image_encoder = ImageEncoder(model)
    text_encoder = TextEncoder(model)

    image_encoder.eval()
    text_encoder.eval()

    # --------------------------------------------------------
    # Dummy export inputs.
    # --------------------------------------------------------

    torch.manual_seed(12345)

    dummy_image = torch.rand(
        1,
        3,
        EXPECTED_IMAGE_SIZE,
        EXPECTED_IMAGE_SIZE,
        dtype=torch.float32,
    )

    dummy_text = tokenizer([
        "a photo of a cat",
    ])

    if dummy_text.dtype != torch.int64:
        dummy_text = dummy_text.to(torch.int64)

    if tuple(dummy_text.shape) != (
        1,
        EXPECTED_CONTEXT_LENGTH,
    ):
        raise RuntimeError(
            f"Unexpected token shape: {tuple(dummy_text.shape)}"
        )

    # --------------------------------------------------------
    # Check output dimensions BEFORE ONNX export.
    # --------------------------------------------------------

    with torch.no_grad():
        image_test = image_encoder(
            dummy_image
        )

        text_test = text_encoder(
            dummy_text
        )

    print()
    print("PyTorch output shapes")
    print("-" * 60)
    print(
        f"Image: {tuple(image_test.shape)}"
    )
    print(
        f"Text : {tuple(text_test.shape)}"
    )

    if image_test.shape[-1] != EXPECTED_EMBED_DIM:
        raise RuntimeError(
            "Unexpected image embedding dimension: "
            f"{image_test.shape[-1]}"
        )

    if text_test.shape[-1] != EXPECTED_EMBED_DIM:
        raise RuntimeError(
            "Unexpected text embedding dimension: "
            f"{text_test.shape[-1]}"
        )

    # --------------------------------------------------------
    # Export image model.
    # --------------------------------------------------------

    torch_onnx_export(
        module=image_encoder,
        dummy_input=dummy_image,
        output_path=image_onnx_path,
        input_name="pixel_values",
        output_name="image_embedding",
        opset=opset,
    )

    # --------------------------------------------------------
    # Export text model.
    # --------------------------------------------------------

    torch_onnx_export(
        module=text_encoder,
        dummy_input=dummy_text,
        output_path=text_onnx_path,
        input_name="input_ids",
        output_name="text_embedding",
        opset=opset,
    )

    # --------------------------------------------------------
    # Validate with batch size 2.
    #
    # This also verifies the exported dynamic batch dimension.
    # --------------------------------------------------------

    print()
    print("=" * 70)
    print("PyTorch vs ONNX Runtime validation")
    print("=" * 70)

    torch.manual_seed(98765)

    validation_images = torch.rand(
        2,
        3,
        EXPECTED_IMAGE_SIZE,
        EXPECTED_IMAGE_SIZE,
        dtype=torch.float32,
    )

    validation_strings = [
        "a photo of a cat",
        "một con mèo đang ngồi trên ghế",
    ]

    validation_tokens = tokenizer(
        validation_strings
    ).to(torch.int64)

    with torch.no_grad():
        pytorch_image_embedding = image_encoder(
            validation_images
        ).cpu().numpy()

        pytorch_text_embedding = text_encoder(
            validation_tokens
        ).cpu().numpy()

    onnx_image_embedding = run_ort(
        image_onnx_path,
        "pixel_values",
        validation_images.cpu().numpy().astype(
            np.float32
        ),
    )

    onnx_text_embedding = run_ort(
        text_onnx_path,
        "input_ids",
        validation_tokens.cpu().numpy().astype(
            np.int64
        ),
    )

    image_validation = validate_embedding_match(
        "IMAGE",
        pytorch_image_embedding,
        onnx_image_embedding,
    )

    text_validation = validate_embedding_match(
        "TEXT",
        pytorch_text_embedding,
        onnx_text_embedding,
    )

    # --------------------------------------------------------
    # Tokenizer test vectors for Android.
    #
    # Your Kotlin tokenizer should reproduce these EXACT IDs.
    # --------------------------------------------------------

    tokenizer_test_strings = [
        "a photo of a cat",
        "một con mèo đang ngồi trên ghế",
        "IMG_2026_08_29.jpg",
    ]

    tokenizer_test_ids = tokenizer(
        tokenizer_test_strings
    ).cpu().tolist()

    tokenizer_test_vectors = []

    for text, token_ids in zip(
        tokenizer_test_strings,
        tokenizer_test_ids,
    ):
        tokenizer_test_vectors.append(
            {
                "text": text,
                "input_ids": token_ids,
            }
        )

    # --------------------------------------------------------
    # File hashes
    # --------------------------------------------------------

    print()
    print("Calculating SHA-256 hashes ...")

    checkpoint_sha = sha256_file(
        checkpoint_path
    )

    image_sha = sha256_file(
        image_onnx_path
    )

    text_sha = sha256_file(
        text_onnx_path
    )

    vocab_sha = sha256_file(
        vocab_path
    )

    merges_sha = sha256_file(
        merges_path
    )

    # --------------------------------------------------------
    # Runtime config
    # --------------------------------------------------------

    runtime_config = {
        "format_version": 1,

        "model": {
            "name": MODEL_NAME,
            "embedding_dimension": EXPECTED_EMBED_DIM,
            "embeddings_l2_normalized": True,
        },

        "image_encoder": {
            "model_file": IMAGE_ONNX_NAME,
            "input_name": "pixel_values",
            "output_name": "image_embedding",

            "input_dtype": "float32",
            "output_dtype": "float32",

            "layout": "NCHW",

            "input_shape": [
                "batch",
                3,
                EXPECTED_IMAGE_SIZE,
                EXPECTED_IMAGE_SIZE,
            ],

            "output_shape": [
                "batch",
                EXPECTED_EMBED_DIM,
            ],

            "image_size": EXPECTED_IMAGE_SIZE,

            # PIL/torchvision ToTensor semantics:
            # uint8 RGB -> float32 [0, 1]
            "input_range": [
                0.0,
                1.0,
            ],

            # Apple specifies these for MobileCLIP2-S0.
            "mean": [
                0.0,
                0.0,
                0.0,
            ],

            "std": [
                1.0,
                1.0,
                1.0,
            ],

            # Actual config resolved by OpenCLIP.
            "open_clip_preprocess": preprocess_cfg,

            "preprocessing": {
                "color_mode": "RGB",
                "resize_mode": preprocess_cfg.get(
                    "resize_mode",
                    "shortest",
                ) if isinstance(
                    preprocess_cfg,
                    dict,
                ) else "shortest",

                "interpolation": preprocess_cfg.get(
                    "interpolation",
                    "bicubic",
                ) if isinstance(
                    preprocess_cfg,
                    dict,
                ) else "bicubic",

                "center_crop": True,
                "divide_uint8_by_255": True,
            },
        },

        "text_encoder": {
            "model_file": TEXT_ONNX_NAME,

            "input_name": "input_ids",
            "output_name": "text_embedding",

            "input_dtype": "int64",
            "output_dtype": "float32",

            "input_shape": [
                "batch",
                EXPECTED_CONTEXT_LENGTH,
            ],

            "output_shape": [
                "batch",
                EXPECTED_EMBED_DIM,
            ],

            "context_length": EXPECTED_CONTEXT_LENGTH,
        },

        "tokenizer": tokenizer_config,

        "tokenizer_test_vectors": tokenizer_test_vectors,

        "onnx": {
            "opset": opset,
            "dynamic_batch": True,
        },

        "validation": {
            "requirements": {
                "minimum_cosine_similarity":
                    MIN_COSINE_SIMILARITY,
                "maximum_absolute_error":
                    MAX_ABSOLUTE_ERROR,
            },

            "image": image_validation,
            "text": text_validation,
        },

        "files": {
            "checkpoint_source": {
                "filename": checkpoint_path.name,
                "sha256": checkpoint_sha,
            },

            "image_onnx": {
                "filename": IMAGE_ONNX_NAME,
                "sha256": image_sha,
            },

            "text_onnx": {
                "filename": TEXT_ONNX_NAME,
                "sha256": text_sha,
            },

            "vocab": {
                "filename": VOCAB_NAME,
                "sha256": vocab_sha,
            },

            "merges": {
                "filename": MERGES_NAME,
                "sha256": merges_sha,
            },
        },

        "export_environment": {
            "python": sys.version.split()[0],
            "torch": torch.__version__,
            "open_clip": getattr(
                open_clip,
                "__version__",
                "unknown",
            ),
            "onnx": onnx.__version__,
            "onnxruntime": ort.__version__,
        },
    }

    with config_path.open(
        "w",
        encoding="utf-8",
        newline="\n",
    ) as f:
        json.dump(
            runtime_config,
            f,
            ensure_ascii=False,
            indent=2,
        )
        f.write("\n")

    # --------------------------------------------------------
    # Final report
    # --------------------------------------------------------

    print()
    print("=" * 70)
    print("EXPORT COMPLETE")
    print("=" * 70)

    generated_files = [
        image_onnx_path,
        text_onnx_path,
        config_path,
        vocab_path,
        merges_path,
    ]

    for path in generated_files:
        size_mb = path.stat().st_size / (
            1024 * 1024
        )

        print(
            f"{path.name:<35} "
            f"{size_mb:>9.2f} MB"
        )

    print()
    print("Output directory:")
    print(output_dir)

    print()
    print("Expected Android assets:")
    print()
    print("app/src/main/assets/models/")
    print(f"├── {IMAGE_ONNX_NAME}")
    print(f"├── {TEXT_ONNX_NAME}")
    print(f"├── {CONFIG_NAME}")
    print(f"├── {VOCAB_NAME}")
    print(f"└── {MERGES_NAME}")

    print()
    print("Both ONNX models passed PyTorch comparison.")
    print("Safe to proceed to Android integration.")


if __name__ == "__main__":
    main()