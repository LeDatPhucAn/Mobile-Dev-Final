# Mobile Image Retrieval (CS426 Final Project)

An intelligent, native on-device Android application for privacy-preserving semantic photo search, personalized face retrieval, and Vietnamese receipt/screenshot OCR.

---

## 1. Group Information

**Course:** CS426 - Mobile Device Application Development  
**Project Title:** Mobile Image Retrieval: On-Device Semantic, Face & Text Search for Android  

### Group Members (Ascending Student ID Order)

| Student ID | Full Name | Email / Contribution Focus | Work Allocation (%) |
| :--- | :--- | :--- | :---: |
| **24125041** | **Pham Nguyen Minh Quan** | Jetpack Compose UI, Screen Navigation, Report & Presentation | 25% |
| **24125048** | **Tran Canh Anh Tuan** | Room Database, MediaStore Operations, Background Indexing Worker | 25% |
| **24125049** | **Vong Chi Van** | MobileCLIP2-S0 Semantic Search, SCRFD Face Detection & MobileFaceNet | 25% |
| **24125052** | **Le Dat Phuc An** | ML Kit Vietnamese OCR, FTS4 Full-Text Indexing, Product Requirements | 25% |

---

## 2. Demo Video & Test Credentials

- **Demo Video Link:** Please see [`video/demo-link.txt`](video/demo-link.txt) or access via:  
  👉 **[Demo Video on Google Drive / YouTube (Link)](video/demo-link.txt)**
- **Test Account Credentials:**  
  *None required.* The application is 100% on-device and privacy-first. It does not require login, authentication tokens, network access (`INTERNET` permission is omitted from `AndroidManifest.xml`), or any cloud backend servers.

---

## 3. Project Overview & Key Features

### Realistic Problem Solved
Modern smartphone users store thousands of photos, receipts, documents, and screenshots, making filename and chronological browsing ineffective when searching for specific visual concepts or unaccented Vietnamese text.

### Core Capabilities
1. **On-Device Semantic Visual Search:** Natural language search (e.g. `sunset on beach`, `coffee cup on table`) using an exported **MobileCLIP2-S0** model via ONNX Runtime without sending media to external servers.
2. **Reverse Image Search & Similarity:** Search using a reference photo directly from the gallery or camera.
3. **Face Enrollment & Multi-Person Identity Search:** Enroll people by face photo (`@alex`, `@mai_anh`). Filter photos by multiple people matching distinct faces simultaneously alongside visual scene descriptions.
4. **Vietnamese OCR & Full-Text Search (FTS4):** Offline text recognition with Google ML Kit Latin + SQLite FTS4 word index. Search receipts and bills with or without Vietnamese diacritics (`hoa don`, `70.000`, `hóa đơn`).
5. **Photo Viewer & Media Management:** Full-screen zoom/pan photo viewer, OCR text extractor/copying, safe lossless photo copying to `Pictures/Photo Search`, system sharing, and MediaStore deletion confirmation flow.
6. **Robust Background Indexing:** Incremental indexing with Android **WorkManager** observing battery constraints, handling device rotation, process recreation, and incremental MediaStore changes.

---

## 4. Application Architecture

The project follows standard Android **MVVM (Model-View-ViewModel)** and **Clean Architecture** patterns:

```
app/src/main/java/com/example/mobile_image_retrieval/
├── ai/                 # OnnxRuntime sessions, MobileCLIP2-S0, SCRFD, MobileFaceNet, Vector Math
├── data/
│   ├── db/             # Room DB, Entities, DAOs, FTS4 tables, Float32 Vector Codecs, Migrations
│   ├── mediastore/     # Android MediaStore access, query pagination, safe photo copying
│   ├── ocr/            # Google ML Kit Text Recognition, Vietnamese text normalization
│   └── repository/     # Search, Person, History, and MediaStore coordination repositories
├── model/              # Domain models (SearchResult, Person, SearchFilter, IndexState)
├── ui/                 # Jetpack Compose UI (8 connected screens, Navigation, ViewModels, Theme)
└── worker/             # Resumable WorkManager background indexing pipelines
```

---

## 5. Build and Installation Instructions

### Prerequisites
- **Android Studio:** Ladybug / Meerkat (or newer)
- **JDK:** Version 17+
- **Android SDK:** Compile SDK 36, Target SDK 36, **Minimum SDK 29 (Android 10+)**
- **Hardware/Emulator:** Physical device or emulator running Android 10 (API 29) to Android 16 (API 36+).

### Building from Command Line

#### On Windows:
```powershell
# Build Debug APK
.\gradlew.bat :app:assembleDebug

# Run Unit Tests
.\gradlew.bat :app:testDebugUnitTest

# Run Lint checks
.\gradlew.bat :app:lintDebug

# Build Release APK
.\gradlew.bat :app:assembleRelease
```

#### On Linux / macOS:
```bash
chmod +x gradlew
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

### Installation via ADB

```bash
# Verify connected device
adb devices

# Install Debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch Application
adb shell am start -n com.example.mobile_image_retrieval/.MainActivity
```

---

## 6. Permissions and Privacy

- `READ_MEDIA_IMAGES` (Android 13+) / `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+ partial access)
- `READ_EXTERNAL_STORAGE` (Android 10–12L)
- **Zero Network Permissions:** `INTERNET` permission is completely excluded. All AI inference, face extraction, OCR, and search indexing occur 100% locally on the device.

---

## 7. Submission Package Structure

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
