# Vietnamese OCR

The app bundles Google's Latin Text Recognition v2 model using the pinned Android dependency
`com.google.mlkit:text-recognition:16.0.1`. Gradle merges the model assets and native libraries
from its AAR into the APK; no separate ONNX file or runtime download belongs in this directory.

- Vietnamese is an officially supported Latin-script language:
  https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
- Bundled integration and dependency version:
  https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Google's estimated app-size increase is about 4 MB per script per architecture. This is
  not the size of this project's universal APK, which also contains CLIP and face models.
- Inspection of this debug APK: OCR model assets total 1,486,803 bytes; the arm64 OCR
  native runtime is 11,064,544 bytes uncompressed. The universal debug APK includes four
  architecture runtimes, so its size increase exceeds Google's per-architecture estimate.
- Model identifier in the local OCR cache: `mlkit-latin-16.0.1-vi-v1`.

Recognition works offline from first launch. The manifest explicitly removes transitive
network permissions. Inputs are full-frame, EXIF-oriented software bitmaps downscaled to
at most 2048 pixels on the longest edge. Bills and message screenshots should be sharp
enough to resolve each letter; tiny lettering, blur and handwriting can reduce accuracy.
This reads text; it does not infer payment fields or validate bill totals.

Original Unicode text is cached in Room alongside an accent-folded FTS4 index. Searches
can omit Vietnamese accents, including Đ/đ. Successful empty scans are cached; errors are
retried on a later indexing pass. Model-version or image-modification changes invalidate
the cache. At most 100,000 characters per photo are stored, with truncation shown in the reader.

ML Kit is distributed under Google's SDK terms; see the linked official documentation.
