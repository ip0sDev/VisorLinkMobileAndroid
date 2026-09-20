<div align="center">

# 🛰️ VisorLink Android

<p align="center">
  <strong>A modern, privacy-focused messenger built with 100% Jetpack Compose and the Biolume design system.</strong>
</p>

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/Language-English-blue?style=for-the-badge&logo=google-translate&logoColor=white" alt="English"></a>
  <a href="README.ru.md"><img src="https://img.shields.io/badge/Язык-Русский-red?style=for-the-badge&logo=google-translate&logoColor=white" alt="Русский"></a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-green.svg?style=flat-square" alt="License: GPLv3"></a>
  <img src="https://img.shields.io/badge/Kotlin-2.4.10-purple.svg?style=flat-square&logo=kotlin" alt="Kotlin 2.4.10">
  <img src="https://img.shields.io/badge/Android-CompileSdk%2037-brightgreen.svg?style=flat-square&logo=android" alt="CompileSdk 37">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-100%25-4285F4.svg?style=flat-square&logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2F%20UDF-orange.svg?style=flat-square" alt="Architecture">
</p>

---

[Key Features](#-key-features) •
[Design System](#-biolume-design-system) •
[Tech Stack](#-tech-stack) •
[Getting Started](#-getting-started) •
[Project Structure](#-project-structure) •
[Contributing](#-contributing) •
[License](#-license)

---

</div>

## ✨ Key Features

- 🎨 **Biolume Design System**: An immersive UI style blending soft neumorphic relief, ambient bioluminescent glow, and smooth fluid liquid animations.
- 💬 **Structured Conversations**: Direct messages, group discussions, and broadcast channels.
- 📂 **Forum-style Topics & Task Tracker**: Group topics for divided discussion threads with an integrated task management checklist right inside each topic.
- 🎵 **Full-featured Audio Player**: Background playback engine powered by AndroidX Media3 ExoPlayer with lock screen and notification shade controls.
- 🎙️ **Voice Messaging**: Voice notes with interactive audio waveform visualization.
- 🖼️ **Media Albums & Spoilers**: Multi-photo/video albums with customizable blur protection against sensitive media and spoilers.
- 📔 **Personal Diary**: A secluded personal space inside the messenger for encrypted notes, thoughts, and task drafts.
- 🔒 **App Lock & Security**: Master PIN code, biometric authorization (fingerprint/face unlock), and protection against screenshots and recent task switch previews.
- ⚡ **Offline-first Outbox**: Resilient message queue storing your actions locally and syncing automatically the moment network connectivity is restored.

---

## 🎨 Biolume Design System

VisorLink is built with **100% Jetpack Compose** without legacy XML layouts or Fragments. The interface adapts across three built-in visual identities:

1. **BIOLUME** (Default): Tactile neumorphic surfaces, deep lighting, and fluid liquid micro-interactions.
2. **FORGE**: Sharp, high-contrast industrial cyber aesthetic.
3. **M3E**: Modern, clean Material 3 Expressive.

---

## 🛠️ Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language & Tooling** | Kotlin 2.4.10, Android Gradle Plugin (AGP) 9.3.2, Gradle 9.7 (Java 21 daemon) |
| **UI Framework** | 100% Jetpack Compose, Material 3, Compose Animation |
| **Architecture** | MVVM / Unidirectional Data Flow (UDF), Clean Architecture |
| **Dependency Injection** | Koin 4.2.2 (`di/AppModule.kt`) |
| **Media & Audio** | AndroidX Media3 ExoPlayer 1.11.0, CameraX |
| **Image Loading** | Coil 2.7.0 (Compose, GIF, Video decoding) |
| **Networking & API** | Dual backend: Firebase BoM 34.18.0 + Retrofit 3.0.0, OkHttp 5.5.0, WebSockets |
| **Local Storage** | SQLite Cache (`LocalCacheDB` v5), EncryptedSharedPreferences |
| **Security** | AndroidX Biometric, AndroidX Security Crypto, KeyStore |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Meerkat (2024.3+) or Ladybug (2024.2+)
- **JDK**: Java 17 or Java 21 (configured as Gradle JDK)
- **Android SDK**: `compileSdk 37`, `minSdk 30`

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/VisorLinkMobileAndroid.git
cd VisorLinkMobileAndroid
```

### 2. Configure Firebase

For local builds and testing, create your `app/google-services.json` from the provided template:

```bash
cp app/google-services.json.example app/google-services.json
```

> [!NOTE]
> If you are connecting to your own Firebase instance, replace the dummy values in `app/google-services.json` with the file downloaded from your [Firebase Console](https://console.firebase.google.com/).

### 3. Build & Test

```bash
# Run JVM Unit Tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug

# Assemble Release APK (Minified with R8)
./gradlew assembleRelease
```

The generated debug APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📁 Project Structure

```
VisorLinkMobileAndroid/
├── app/
│   ├── src/main/
│   │   ├── java/org/visorlink/app/
│   │   │   ├── data/            # Repositories, models, remote APIs (REST/WebSocket/Firebase)
│   │   │   ├── di/              # Koin Dependency Injection module (AppModule.kt)
│   │   │   ├── ui/
│   │   │   │   ├── components/  # Reusable Vl* UI component library (VlButton, VlCard, etc.)
│   │   │   │   ├── screens/     # Jetpack Compose screens and ViewModels
│   │   │   │   ├── theme/       # Design tokens (Biolume, Forge, M3E)
│   │   │   │   └── NavGraph.kt  # Compose Navigation graph
│   │   │   └── utils/           # Outbox, Cache DB, Audio/Video services, Receivers
│   │   └── res/                 # Vector drawables, localization strings (values, values-ru)
│   └── build.gradle.kts         # App-level dependencies and configuration
├── docs/                        # Specifications, API contracts, and Biolume guidelines
├── .github/                     # Workflows (CI/CD) and issue templates
├── CONTRIBUTING.md              # Contribution guide and code conventions
├── LICENSE                      # GNU General Public License v3.0
└── README.md                    # Project documentation
```

---

## 🤝 Contributing

Contributions are warmly welcome! Please review our [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) before submitting pull requests.

To report bugs or suggest new features, feel free to open an [Issue](https://github.com/your-username/VisorLinkMobileAndroid/issues).

---

## 🔒 Security

We take security seriously. If you discover a security vulnerability, please refer to our [Security Policy](SECURITY.md) for confidential reporting instructions instead of opening a public issue.

---

## 📄 License

This project is licensed under the terms of the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for the full text.
