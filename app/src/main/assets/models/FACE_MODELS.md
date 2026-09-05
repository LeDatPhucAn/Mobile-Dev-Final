# On-device face recognition

Downloaded unchanged from the official InsightFace v0.7 `buffalo_sc` release:
https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_sc.zip

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| det_500m.onnx | 2524817 | 5e4447f50245bbd7966bd6c0fa52938c61474a04ec7def48753668a9d8b4ea3a |
| w600k_mbf.onnx | 13616099 | 9cc6e4a75f0e2bf0b1aed94578f144d15175f357bdc05e815e5c4a02b319eb4f |

These **pretrained weights are available for non-commercial research only**. InsightFace's MIT source-code license does not extend to its pretrained weights. Commercial use requires appropriate model rights from the publisher.
Model terms: https://github.com/deepinsight/insightface/tree/master/python-package#license
Model catalog: https://github.com/deepinsight/insightface/tree/master/model_zoo

## Runtime contract

- Version: `buffalo-sc-w600k-mbf-v1`; changing the weights or preprocessing requires a new version and face reindexing/re-enrollment.
- SCRFD detector: Float32 RGB NCHW `[1,3,640,640]`; aspect-preserving bilinear resize, top-left placement on a black canvas; `(pixel - 127.5) / 128`. Nine outputs in model order: scores `443/468/493`, boxes `446/471/496`, five landmarks `449/474/499`; strides 8/16/32 with two anchors per cell. Detection cutoff 0.5; NMS IoU 0.4.
- Recognition: least-squares five-landmark similarity alignment to the ArcFace 112×112 template, bilinear sampling with black borders; Float32 RGB NCHW `[1,3,112,112]`; `(pixel - 127.5) / 127.5`. `input.1` → `516`, 512 output floats, L2-normalized.
- The runtime verifies the above SHA-256 hashes before opening either model. Both sessions persist and inference is serialized with two intra-op CPU threads.
- Original photos are decoded with EXIF orientation and a longest side of at most 1280 pixels. Detector input is 640×640. Up to 64 faces are processed sequentially; no-face photos are marked indexed. Small faces and crowded groups can be missed at this resolution.
- Enrollment requires exactly one detection, score ≥ 0.7 and face dimensions ≥ 60 pixels in the bounded decoded image.
- Identity matching uses cosine ≥ 0.45 as an initial, **uncalibrated** operating point. This is not a probability or a guarantee. Validate false matches/misses on representative device photos before deployment.
- Each mentioned identity must be assigned to a distinct face. Face vectors are never averaged across different people or mixed with CLIP vectors. Remaining text/image context is ranked in CLIP space after the face filter.

Run `python tools/inspect_face_models.py` to independently inspect file hashes, protobuf input/output contracts, and initial graph operators without Python ML dependencies.

Preprocessing references:
https://github.com/deepinsight/insightface/blob/master/python-package/insightface/model_zoo/scrfd.py
https://github.com/deepinsight/insightface/blob/master/python-package/insightface/model_zoo/arcface_onnx.py
https://github.com/deepinsight/insightface/blob/master/python-package/insightface/utils/face_align.py
