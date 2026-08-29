# MobileCLIP2-S0 assets

This directory intentionally contains no substitute model. Add the image and text ONNX graphs exported from the same MobileCLIP2-S0 checkpoint:

- `mobileclip2_s0_image.onnx`
- `mobileclip2_s0_text.onnx`
- `mobileclip2_s0_config.json`
- the vocabulary and merges files named by that config

The config must be produced/verified alongside the export and contain the exact graph input/output names, input size/layout, resize rule, channel normalization, text context length, token IDs, tokenizer assets, and normalization flags. See the root README for the schema. The app fails visibly when this contract is absent or inconsistent; it never falls back to a different model or fake vectors.
