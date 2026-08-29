# MobileCLIP2-S0 assets

This directory intentionally contains no substitute model. The generated config and tokenizer files are packaged here. Add the two gitignored ONNX graphs exported from the same MobileCLIP2-S0 checkpoint:

- `mobileclip2_s0_image.onnx`
- `mobileclip2_s0_text.onnx`

Regenerate the config, tokenizer files, and graphs together with `tools/export_mobileclip2.py`; do not mix artifacts from different exports. The config records the exact graph, preprocessing, and tokenizer contract plus validation metrics and token-ID vectors. The app fails visibly when this contract is absent or inconsistent; it never falls back to a different model or fake vectors.
