# Contributing to VisorLink

🌐 **Language**: **[English](CONTRIBUTING.md)** • **[Русский](CONTRIBUTING.ru.md)**

Thank you for your interest in contributing to VisorLink! We are excited to build a modern, tactile, and private communication platform together.

---

## 📋 Code of Conduct

All contributors and participants must adhere to our [Code of Conduct](CODE_OF_CONDUCT.md). Please treat all members of the community with respect and empathy.

---

## 🛠️ Development Workflow

1. **Fork & Branch**
   - Fork the repository and create a new branch from `canary` or `main`.
   - Use meaningful branch names: `feature/cool-feature`, `fix/issue-123`, `refactor/audio-engine`.

2. **Setup Local Environment**
   - Use JDK 17 or JDK 21.
   - Copy the Firebase configuration template:
     ```bash
     cp app/google-services.json.example app/google-services.json
     ```

3. **Coding Standards & Architectural Rules**
   - **100% Jetpack Compose**: Do not use XML layouts, ViewBinding, or Fragments.
   - **Component Prefix**: All reusable design components in `ui/components/` must use the `Vl` prefix (`VlButton`, `VlCard`, `VlTextField`, etc.).
   - **Language Conventions**:
     - Class names, function names, variables, and log tags must be in **English**.
     - Code comments and git commit messages are written in **Russian** (or English).
     - UI strings must be placed synchronously in both `res/values/strings.xml` (English/Default) and `res/values-ru/strings.xml` (Russian).
   - **Architecture**:
     - Follow MVVM with Unidirectional Data Flow (UDF).
     - Expose state via `StateFlow` (`_uiState` private mutable, `uiState` public read-only).
     - Collect flows in Compose using `collectAsStateWithLifecycle()`.
     - Register dependencies in `di/AppModule.kt` using **Koin** (`single`, `viewModel`).
   - **Do Not Add Heavy Dependencies**: Please discuss with maintainers before introducing new external dependencies in `gradle/libs.versions.toml`.

4. **Testing & Verification**
   - Run unit tests before submitting your changes:
     ```bash
     ./gradlew testDebugUnitTest
     ```
   - Ensure the app builds without errors:
     ```bash
     ./gradlew assembleDebug
     ```

5. **Submitting a Pull Request**
   - Write a clear, descriptive PR title and summary.
   - Reference any relevant issues (e.g. `Closes #42`).
   - Ensure your code is formatted and passes tests.

---

## 📄 License

By contributing to VisorLink, you agree that your contributions will be licensed under the project's [GNU General Public License v3.0 (GPLv3)](LICENSE).
