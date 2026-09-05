# Mobile Image Retrieval (CS426 Final Project)

An intelligent, native on-device Android application for privacy-preserving semantic photo search, personalized face retrieval, and Vietnamese receipt/screenshot OCR.

---

## 1. Group Information

**Course:** CS426 - Mobile Device Application Development  
**Project Title:** Mobile Image Retrieval: On-Device Semantic, Face & Text Search for Android  

### Group Members & Detailed Work Allocation (Ascending Student ID Order)

| Student ID | Full Name | Primary Work Packages & Responsibilities | Deliverables & Completed Work | Share (%) |
| :--- | :--- | :--- | :--- | :---: |
| **24125041** | **Pham Nguyen Minh Quan** | • Compose UI & Navigation<br>• Report & Final Presentation | • Implemented 8 Jetpack Compose feature screens (`ui/screens/`), adaptive grids, photo viewer (pan/pinch/zoom), landscape layouts, search state persistence across lifecycle/process recreation.<br>• Authored project report and prepared demo presentation materials. | 25% |
| **24125048** | **Tran Canh Anh Tuan** | • Data & Android Media Integration<br>• Background Indexing Pipelines | • Designed Room SQLite database, schemas, DAOs, Float32 vector embeddings, database migrations (v1 → v4), search history.<br>• MediaStore querying/safe photo copying to `Pictures/Photo Search`, sharing & deletion consent flows.<br>• Implemented `WorkManager` incremental background indexing worker (`PhotoIndexWorker`) with battery constraints & transactional revision guards. | 25% |
| **24125049** | **Vong Chi Van** | • Visual Semantic Retrieval<br>• Face Enrollment & Search | • Implemented ONNX Runtime sessions for **MobileCLIP2-S0** image/text encoders, preprocessors, tokenizers, L2 normalization, exact top-K dot-product heap search.<br>• Integrated **SCRFD** face detection (`det_500m.onnx`), 5-point landmark alignment, and **MobileFaceNet** (`w600k_mbf.onnx`) face embedding for distinct multi-person query matching (`@people`). | 25% |
| **24125052** | **Le Dat Phuc An** | • Vietnamese OCR & Full-Text Search<br>• Product Scope & Requirements | • Bundled Google ML Kit Latin Text Recognition for offline Vietnamese OCR, SQLite `FTS4` text indexing (`photo_text_fts`), diacritics folding (`hoa don` ↔ `hóa đơn`), in-viewer text reader/copying.<br>• Maintained requirement mapping, test specs, and architecture validation. | 25% |
| **All Members** | **All 4 Members** | • Testing & Validation | • 63 JVM unit tests & 40 Android instrumentation tests covering vector math, Room migrations, distinct face assignments, MediaStore operations, OCR pipelines, and UI integrity. | Equal |

---

## 2. Demo Video & Test Account

- **Demo Video Link:**  
  👉 **[Watch Demo Video on Google Drive](https://drive.google.com/file/d/1wnoNpTwf_XIyzmyUS_6LmTDbg9yYgSK_/view?usp=sharing)**  
  *(Also recorded in `video/demo-link.txt`)*
- **Test Account Credentials:**  
  *None required.* The application runs completely on-device and privacy-first. It does not require login, registration, user accounts, authentication tokens, or any cloud backend servers. Network access is completely disabled (no `INTERNET` permission in `AndroidManifest.xml`).

---

## 3. ONNX Model Setup & Pre-build Instructions

> ⚠️ **IMPORTANT (Required before building APK):**  
> To keep the git repository lightweight, the two large ONNX model graph files (`mobileclip2_s0_image.onnx` and `mobileclip2_s0_text.onnx`) are gitignored. You **must download and place them into the assets folder before compiling**.

### Model Download Link
📥 **[Download ONNX Models from Google Drive](https://drive.google.com/drive/folders/1v1laD1uWziGlI5TJI8ACGqaOY4T8pEdc?usp=sharing)**

### Step-by-Step Setup:
1. Open the Google Drive folder link above.
2. Download the two files:
   - `mobileclip2_s0_image.onnx` (~460 MB - MobileCLIP2-S0 image encoder)
   - `mobileclip2_s0_text.onnx` (~120 MB - MobileCLIP2-S0 text encoder)
3. Copy/Move both downloaded `.onnx` files into the directory:
   ```
   Mobile-Dev-Final/app/src/main/assets/models/
   ```
4. Verify that `Mobile-Dev-Final/app/src/main/assets/models/` contains all of the following files:
   ```
   app/src/main/assets/models/
   ├── det_500m.onnx                 # Bundled SCRFD face detector
   ├── w600k_mbf.onnx                # Bundled MobileFaceNet face recognizer
   ├── mobileclip2_s0_config.json    # MobileCLIP2-S0 runtime contract
   ├── mobileclip2_s0_vocab.json     # Tokenizer vocabulary
   ├── mobileclip2_s0_merges.txt     # Tokenizer merges
   ├── mobileclip2_s0_image.onnx     # <--- Placed here
   └── mobileclip2_s0_text.onnx      # <--- Placed here
   ```

*(Note: OCR models are automatically provided offline by the bundled Google ML Kit dependency and do not require manual downloading).*

---

## 4. Build and Installation Instructions

### Prerequisites
- **Android Studio:** Ladybug / Meerkat (or newer)
- **JDK:** Version 17+
- **Android SDK:** Compile SDK 36, Target SDK 36, **Minimum SDK 29 (Android 10+)**
- **Device / Emulator:** Android 10 (API 29) to Android 16 (API 36+).

### Building APK from Command Line

#### On Windows (PowerShell / Command Prompt):
```powershell
# Navigate to project directory
cd Mobile-Dev-Final

# 1. Build installable Debug APK
.\gradlew.bat :app:assembleDebug

# 2. Run JVM Unit Tests
.\gradlew.bat :app:testDebugUnitTest

# 3. Run Lint Checks
.\gradlew.bat :app:lintDebug

# 4. Build Release APK (Optional)
.\gradlew.bat :app:assembleRelease
```

#### On Linux / macOS:
```bash
cd Mobile-Dev-Final
chmod +x gradlew

# 1. Build installable Debug APK
./gradlew :app:assembleDebug

# 2. Run JVM Unit Tests
./gradlew :app:testDebugUnitTest

# 3. Run Lint Checks
./gradlew :app:lintDebug

# 4. Build Release APK (Optional)
./gradlew :app:assembleRelease
```

### Installing and Running via ADB

```bash
# Check connected physical device or emulator
adb devices

# Install APK to device (preserves existing data)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the Application
adb shell am start -n com.example.mobile_image_retrieval/.MainActivity
```

---

## 5. Key Application Features

1. **On-Device Semantic Visual Search:** Natural language search queries (e.g., `sunset at the beach`, `a cup of coffee`, `dog playing in the park`) matching image contents using **MobileCLIP2-S0** ONNX embeddings.
2. **Reverse Image Search & Visual Similarity:** Search for visually similar images using an existing gallery photo or newly captured reference photo.
3. **Face Enrollment & Multi-Person Identity Search:** Enroll people by face photo (`@alex`, `@mai_anh`). Search for photos containing multiple specific people together (`@alex @mai_anh at the beach`), ensuring distinct face match verification.
4. **Vietnamese OCR & Full-Text Search (FTS4):** Offline text extraction from receipts, bills, and screenshots. Search text with or without diacritics (`hoa don` matches `hóa đơn`). View, select, and copy recognized text directly in the photo viewer.
5. **Photo Viewer & Media Management:** Smooth pinch-to-zoom, pan, full-screen view, safe lossless photo copy to `Pictures/Photo Search`, system share sheet, and MediaStore system deletion confirmation flow.
6. **Resumable Background Indexing:** Powered by Android `WorkManager`, incrementally processing newly added, modified, or deleted device photos while respecting battery constraints.

---

## 6. Application Architecture

The application is structured following Clean Architecture and Android MVVM patterns:

```
app/src/main/java/com/example/mobile_image_retrieval/
├── ai/                 # ONNX sessions (CLIP, SCRFD, MobileFaceNet), tokenizers, top-K search
├── data/
│   ├── db/             # Room Database, DAOs, Entities, FTS4 tables, vector codecs, migrations
│   ├── mediastore/     # MediaStore content resolver, pagination, safe photo copying
│   ├── ocr/            # Google ML Kit OCR extraction, Vietnamese text normalization
│   └── repository/     # Search, Person, History, and MediaStore coordination repositories
├── model/              # Domain entities (SearchResult, Person, SearchFilter, IndexState)
├── ui/                 # Jetpack Compose UI (8 connected screens, Navigation, ViewModels, Theme)
└── worker/             # Resumable WorkManager background indexing pipelines
```

---

## 7. Permissions and Privacy

- `READ_MEDIA_IMAGES` (Android 13+) / `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+ partial access)
- `READ_EXTERNAL_STORAGE` (Android 10–12L)
- **100% Privacy-Preserving:** Zero network permissions requested (`INTERNET` permission omitted). All AI models, facial recognition, OCR, database storage, and vector indexes remain strictly on the user's local device.

---

## 8. Final Submission Archive Structure

```
24125041_24125048_24125049_24125052/
├── README.md              # Project metadata, members, build steps & instructions
├── src/                   # Complete source code (excluding build, .gradle, .idea)
│   └── Mobile-Dev-Final/
├── apk/
│   └── app-release.apk    # Installable Android APK (API 24+)
├── report/
│   └── report.pdf         # Final project report PDF (10–30 pages)
└── video/
    └── demo-link.txt      # Link to Google Drive / YouTube demo video
```
