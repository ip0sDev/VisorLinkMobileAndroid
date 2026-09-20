<div align="center">

# 🛰️ VisorLink Android

<p align="center">
  <strong>Современный защищенный мессенджер на 100% Jetpack Compose с уникальной дизайн-системой Biolume.</strong>
</p>

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/Language-English-blue?style=for-the-badge&logo=google-translate&logoColor=white" alt="English"></a>
  <a href="README.ru.md"><img src="https://img.shields.io/badge/Язык-Русский-red?style=for-the-badge&logo=google-translate&logoColor=white" alt="Русский"></a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/Лицензия-GPLv3-green.svg?style=flat-square" alt="Лицензия: GPLv3"></a>
  <img src="https://img.shields.io/badge/Kotlin-2.4.10-purple.svg?style=flat-square&logo=kotlin" alt="Kotlin 2.4.10">
  <img src="https://img.shields.io/badge/Android-CompileSdk%2037-brightgreen.svg?style=flat-square&logo=android" alt="CompileSdk 37">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-100%25-4285F4.svg?style=flat-square&logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/Архитектура-MVVM%20%2F%20UDF-orange.svg?style=flat-square" alt="Архитектура">
</p>

---

[Возможности](#-основные-возможности) •
[Дизайн-система](#-дизайн-система-biolume) •
[Стек технологий](#-стек-технологий) •
[Быстрый старт](#-быстрый-старт-и-сборка) •
[Структура проекта](#-структура-проекта) •
[Участие в разработке](#-участие-в-разработке) •
[Лицензия](#-лицензия)

---

</div>

## ✨ Основные возможности

- 🎨 **Дизайн-система Biolume**: Интерфейс нового поколения, сочетающий мягкий неоморфизм, тактильный рельеф, неоновое свечение и анимацию взаимодействия физики жидкости (Fluid / Liquid motion).
- 💬 **Развитые коммуникации**: Личные диалоги, каналы для публикаций и групповые чаты.
- 📂 **Топики (форумы) и трекер задач**: Структурированные ветки внутри групп с интегрированным трекером задач прямо в обсуждении.
- 🎵 **Встроенный аудиоплеер**: Фоновое воспроизведение на базе AndroidX Media3 ExoPlayer с управлением из шторки и экрана блокировки.
- 🎙️ **Голосовые сообщения**: Запись и воспроизведение с динамической визуализацией звуковой волны.
- 🖼️ **Медиаальбомы и спойлеры**: Отправка подборок фото и видео со скрытием под блюр-спойлер.
- 📔 **Персональный дневник**: Личное защищенное пространство для заметок, черновиков и мыслей внутри приложения.
- 🔒 **Безопасность доступа**: Блокировка мастер-PIN кодом, биометрией (отпечаток / Face Unlock), защита от скриншотов и скрытие превью в недавних задачах.
- ⚡ **Офлайн-архитектура (Outbox)**: Локальная очередь действий и кэш SQLite, гарантирующие мгновенную отправку сообщений при восстановлении связи.

---

## 🎨 Дизайн-система Biolume

VisorLink на **100% написан на Jetpack Compose** без XML-разметок и Fragments. Пользователю доступны 3 темы оформления:

1. **BIOLUME** (По умолчанию): Мягкий неоморфизм, рельефные поверхности и жидкостная анимация.
2. **FORGE**: Строгий индустриальный киберпанк с контрастными гранями.
3. **M3E**: Лаконичный и чистый стиль Material 3 Expressive.

---

## 🛠️ Стек технологий

| Слой | Технологии |
| :--- | :--- |
| **Язык и тулчейн** | Kotlin 2.4.10, Android Gradle Plugin (AGP) 9.3.2, Gradle 9.7 (Java 21 daemon) |
| **UI-фреймворк** | 100% Jetpack Compose, Material 3, Compose Animation |
| **Архитектурный паттерн** | MVVM / Unidirectional Data Flow (UDF), Clean Architecture |
| **Внедрение зависимостей** | Koin 4.2.2 (`di/AppModule.kt`) |
| **Аудио и медиа** | AndroidX Media3 ExoPlayer 1.11.0, CameraX |
| **Загрузка изображений** | Coil 2.7.0 (Compose, GIF, декодирование видео) |
| **Сетевой стек** | Двойной бэкенд: Firebase BoM 34.18.0 + Retrofit 3.0.0, OkHttp 5.5.0, WebSockets |
| **Локальное хранилище** | SQLite Cache (`LocalCacheDB` v5), EncryptedSharedPreferences |
| **Безопасность** | AndroidX Biometric, AndroidX Security Crypto, KeyStore |

---

## 🚀 Быстрый старт и сборка

### Требования к окружению

- **Android Studio**: Meerkat (2024.3+) или Ladybug (2024.2+)
- **JDK**: Java 17 или Java 21 (указывается в Gradle JDK)
- **Android SDK**: `compileSdk 37`, `minSdk 30`

### 1. Клонирование репозитория

```bash
git clone https://github.com/ip0sDev/VisorLinkMobileAndroid.git
cd VisorLinkMobileAndroid
```

### 2. Настройка Firebase

Для локальной сборки создайте файл `app/google-services.json` на основе готового шаблона:

```bash
cp app/google-services.json.example app/google-services.json
```

> [!NOTE]
> Если вы планируете использовать собственный проект Firebase, замените содержимое файла данными из [Firebase Console](https://console.firebase.google.com/).

### 3. Сборка и тестирование

```bash
# Запуск юнит-тестов JVM
./gradlew testDebugUnitTest

# Сборка Debug APK
./gradlew assembleDebug

# Сборка Release APK (с оптимизацией R8)
./gradlew assembleRelease
```

Готовый файл Debug APK будет находиться по пути:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📁 Структура проекта

```
VisorLinkMobileAndroid/
├── app/
│   ├── src/main/
│   │   ├── java/org/visorlink/app/
│   │   │   ├── data/            # Репозитории, модели, API бэкенда (REST/WebSocket/Firebase)
│   │   │   ├── di/              # Модуль Koin Dependency Injection (AppModule.kt)
│   │   │   ├── ui/
│   │   │   │   ├── components/  # Библиотека компонентов Vl* (VlButton, VlCard и др.)
│   │   │   │   ├── screens/     # Экраны Jetpack Compose и их ViewModels
│   │   │   │   ├── theme/       # Дизайн-токены (Biolume, Forge, M3E)
│   │   │   │   └── NavGraph.kt  # Граф навигации Compose
│   │   │   └── utils/           # Outbox, база кэша, аудио/видео плееры, ресиверы
│   │   └── res/                 # Векторная графика, строки локализации (values, values-ru)
│   └── build.gradle.kts         # Конфигурация сборки и зависимости модуля
├── docs/                        # Спецификации, контракты API и гайдлайны Biolume
├── .github/                     # CI/CD воркфлоу и шаблоны задач
├── CONTRIBUTING.ru.md           # Руководство для участников разработки
├── LICENSE                      # Лицензия GNU General Public License v3.0
└── README.ru.md                 # Документация проекта
```

---

## 🤝 Участие в разработке

Мы приветствуем вклад сообщества в проект! Пожалуйста, ознакомьтесь с [Руководством контрибьютора](CONTRIBUTING.ru.md) и [Кодексом поведения](CODE_OF_CONDUCT.md).

Нашли ошибку или хотите предложить улучшение? Откройте [Issue](https://github.com/ip0sDev/VisorLinkMobileAndroid/issues).

---

## 🔒 Безопасность

Вопросы безопасности для нас в приоритете. Если вы обнаружили уязвимость, пожалуйста, ознакомьтесь с нашей [Политикой безопасности](SECURITY.md) для конфиденциального сообщения информации вместо создания публичного Issue.

---

## 📄 Лицензия

Проект распространяется на условиях лицензии **GNU General Public License v3.0 (GPLv3)**. Полный текст доступен в файле [LICENSE](LICENSE).
