# GEMINI.md — Контекстная инструкция для AI-агентов (VisorLink Android)

Этот файл — постоянный свод правил, архитектурных соглашений и ограничений для AI-ассистентов при работе с проектом VisorLink.

---

## 1. Project Overview & Tech Stack

- **Назначение:** VisorLink — защищенный многофункциональный мессенджер с поддержкой личных/групповых чатов, голосовых сообщений, медиа-хранилища, персонального дневника, режима маскировки (Stealth mode) и ассистента Aegis. Распространяется через собственный сервис обновлений (без Google Play).
- **Пакет:** `by.iposdev.visorlink` (модуль `:app`).
- **Тулчейн и окружение:**
  - Kotlin: `2.4.10` (Compose Compiler: `org.jetbrains.kotlin.plugin.compose`)
  - Android Gradle Plugin (AGP): `9.3.2`
  - Gradle: `9.7` (JVM 21 daemon via Foojay toolchain resolver)
  - `compileSdk`: `37` | `targetSdk`: `37` | `minSdk`: `30`
  - Java Source/Target Compatibility: `JavaVersion.VERSION_11`
- **UI-стек:**
  - 100% **Jetpack Compose** (Compose BoM `2026.08.00`, Material 3, Animation `1.12.0`). Никаких XML-разметок или Fragments.
  - Собственная дизайн-система `Vl*` (`ui/components/`), токены `VlTokens` (`LocalVlTokens`).
  - 3 темы оформления: `M3E` (Material 3 Expressive), `BIOLUME` (неоморфизм/рельеф), `FORGE` (индустриальный стиль).
  - Загрузка изображений: **Coil 2.7.0** (`coil-compose`, `coil-gif`, `coil-video`).
  - Аудио/видео: **Media3 ExoPlayer 1.11.0**.
- **Архитектурный паттерн:**
  - **MVVM / UDF (Unidirectional Data Flow)** с Clean-подобным разделением:
    - `data/`: модели (`model/`), удаленные источники (`remote/`), репозитории (`repository/`), движок Aegis (`aegis/`).
    - `di/`: внедрение зависимостей — **Koin 4.2.2** (единый модуль `di/AppModule.kt`, без kapt/ksp/Hilt).
    - `ui/`: экраны (`screens/`), переиспользуемые компоненты (`components/`), навигация (`NavGraph.kt`, `Screen.kt`), тема (`theme/`).
    - `utils/`: кэширование, outbox, шифрование, медиа-сервисы, фоновые ресиверы.
- **Бэкенд и хранилище данных:**
  - **Двойной бэкенд:**
    1. Firebase BoM `34.18.0` (Auth, Firestore, Cloud Functions, RTDB, Storage, FCM, Remote Config, App Check).
    2. REST + WebSocket (`data/remote/chat/` — Retrofit 3.0.0, OkHttp 5.5.0, Gson/Kotlinx Serialization), переключается через feature-флаги.
  - **Офлайн-кэш и Outbox:** собственный SQLite-кэш (`utils/ChatDataCache.kt` / `LocalCacheDB`, v5) + `OutboxManager` (последовательная отправка действий по чатам, устойчивая к офлайну).
  - **Настройки:** `SharedPreferences` и `EncryptedSharedPreferences` (`androidx.security:security-crypto:1.1.0`).

---

## 2. Build & Test Commands

Выполняются из корня репозитория через Gradle wrapper:

```bash
# Сборка Debug (Canary-сборка с суффиксом applicationId .canary)
./gradlew assembleDebug

# Сборка Release (с оптимизацией, R8/ProGuard и shrinkResources)
./gradlew assembleRelease

# Запуск всех JVM юнит-тестов
./gradlew testDebugUnitTest

# Запуск конкретного тестового класса или метода
./gradlew testDebugUnitTest --tests "by.iposdev.visorlink.utils.OutboxManagerTest"
./gradlew testDebugUnitTest --tests "by.iposdev.visorlink.ui.screens.auth.AuthViewModelTest.login with blank email sets error"

# Запуск Instrumented-тестов на подключенном устройстве/эмуляторе
./gradlew connectedDebugAndroidTest

# Проверка линтером (встроенный Android Gradle Plugin Lint; ktlint/detekt не подключены)
./gradlew lintDebug

# Полная сборка с проверкой (аналог CI)
./gradlew testDebugUnitTest assembleRelease --parallel --build-cache --configuration-cache
```

---

## 3. Code Style & Architecture Rules

### 3.1. Нейминг и организация кода
- **Composables:** Все Composable-функции именуются с заглавной буквы (`PascalCase`), например: `VlButton`, `ChatScreen`. Не возвращают значений (`Unit`).
- **Префикс Vl:** Все переиспользуемые UI-компоненты библиотеки `ui/components/` именуются с префиксом `Vl` (`VlCard`, `VlTextField`, `VlSurface`, `VlTopAppBar` и т.д.).
- **Языки:** Комментарии в коде и коммит-сообщения пишутся **на русском языке**. Идентификаторы, функции, классы и теги логов — **на английском языке**.
- **Локализация:** Тексты пользовательского интерфейса хранятся синхронно в `res/values/strings.xml` и `res/values-ru/strings.xml` и читаются через `stringResource()`.

### 3.2. Управление состоянием (State Management)
- **State Hoisting:** Компоненты UI не должны иметь собственного скрытого мутабельного состояния без необходимости. Состояние передается сверху вниз через неизменяемые структуры, события поднимаются вверх через лямбды.
- **Неизменяемость:** Модели состояния экрана объявляются как `data class` с `val`-полями.
- **StateFlow / SharedFlow:**
  - В ViewModels мутабельное состояние строго инкапсулировано:
    ```kotlin
    private val _uiState = MutableStateFlow(InitialState)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    ```
  - Одноразовые события (навигация, ошибки, снэкбары) передаются через `Channel<UiEvent>(Channel.BUFFERED).receiveAsFlow()`.
- **Сайд-эффекты:** Строгий запрет выполнения сайд-эффектов вне жизненного цикла Compose. Все побочные действия оформляются только через `LaunchedEffect`, `DisposableEffect` или `SideEffect`.

### 3.3. Корутины и реактивность
- **Запрет `GlobalScope`:** Использовать только `viewModelScope` или инжектируемые скоупы жизненного цикла.
- **Диспетчеры:** Не хардкодить `Dispatchers.IO` глубоко в классах; передавать диспетчеры через конструктор или использовать стандартные расширения репозиториев для удобства тестирования.
- **Подписка на Flow в Compose:** Собирать потоки данных исключительно через lifecycle-aware методы:
  ```kotlin
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ```
- **Koin DI:** Все новые репозитории регистрируются как `single`, ViewModels как `viewModel { }` в файле `di/AppModule.kt`.

### 3.4. Темизация и UI
- **Компоненты адаптируются к теме сами:** Экраны **никогда не принимают `AppTheme` как параметр**. Все свойства читаются из `VlTheme.tokens` / `LocalVlTokens`.
- Рельеф (Biolume neumorphic) рисуется вне границ элемента — компоненты требуют отступ ≥ 6–8dp. В плотных списках используется `vlHairline`.

---

## 4. Agent Guardrails (Жесткие ограничения)

Агент ОБЯЗАН неукоснительно соблюдать следующие правила при любых правках:

1. **Запрет на новые зависимости:** Запрещено добавлять новые тяжелые сторонние библиотеки в `gradle/libs.versions.toml` и `build.gradle.kts` без явного предварительного согласования с пользователем.
2. **Запрет на потерю кода и заглушки:** Категорически запрещено удалять существующую бизнес-логику, комментарии или заменять рабочий код плейсхолдерами вида `// TODO`, `// Same as before...` или пустыми реализациями.
3. **Запрет на скрытое изменение версий:** Запрещено самовольно повышать или понижать версии SDK (`compileSdk`, `targetSdk`, `minSdk`), версии плагинов AGP, Kotlin или Gradle.
4. **Обратная совместимость:** Сохранять обратную совместимость API, DTO-моделей, схемы локальной базы данных (`LocalCacheDB` в `ChatDataCache.kt`) и ключей `SharedPreferences`. При изменении схемы БД обязательно предусматривать миграцию.
5. **Обязательный план перед изменениями (Plan-First):** Перед генерацией правок в коде агент обязан сначала сформулировать краткий план действий из 2–3 пунктов и только после этого приступать к формированию модификаций.
