# Полное руководство по интеграции Ipos Store SDK (In-App Updates)

`ipos-store-sdk` — это легковесная Android-библиотека для внедрения in-app обновлений в любые приложения через инфраструктуру **Ipos Store**. Библиотека поддерживает как **прямое обновление внутри самого приложения** (Self-Update с системным диалогом установки), так и **тихую установку через каталог Ipos Store** при его наличии на устройстве.

Дизайн интерфейса полностью выполнен по спецификации **Biolume Design System** с нативной поддержкой светлой темы (**Tidepool**) и тёмной темы (**Abyss**).

---

## 🌟 Основные возможности

1. **Два пути доставки обновлений (UpdateMode)**:
   - **Direct Self-Update (прямое обновление)**: если Ipos Store **не установлен**, SDK скачивает APK напрямую из API во внутренний кэш и запускает системный установщик Android через изолированный `IposSdkFileProvider`.
   - **Store IPC**: при наличии установленного **Ipos Store** обновление устанавливается тихо в фоне через AIDL IPC-сервис каталога.
2. **Адаптивность тем Biolume**:
   - **Tidepool Light (светлая тема)**: тёплые нейтральные тона фона (`#F2F0E9`), глубокий морской тил (`#0E7FA3`), фиолетовые акценты и мягкие тени дневного освещения.
   - **Abyss Dark (тёмная тема)**: глубокий ночной океан (`#0E1214`), неоновый циан (`#35C7E8`), фиолетовые контейнеры и биолюминесцентные свечения.
   - Автоматически адаптируется под системную тему пользователя (`isSystemInDarkTheme()`).
3. **Автоматическая периодическая проверка обновлений**:
   - Встроенный в SDK планировщик проверок: при возвращении пользователя в приложение SDK проверяет, прошёл ли заданный интервал (по умолчанию раз в 24 часа), выполняет фоновый запрос и при наличии новой версии показывает UI обновления.
4. **Корректная обработка разрешений Android 8.0 – 15+**:
   - Бесшовная обработка AppOp-разрешения «Установка неизвестных приложений» (`REQUEST_INSTALL_PACKAGES`): если тумблер выключен, пользователю предлагается перейти в настройки, а после выдачи разрешения и возврата в приложение установка скачанного APK продолжается **автоматически**.
5. **Поддержка каналов тестирования**:
   - `UpdateChannel.RELEASE` ("release") — релизные версии (по умолчанию).
   - `UpdateChannel.BETA` ("beta") — публичные бета-версии для тестировщиков.
   - `UpdateChannel.NIGHTLY` ("nightly") — ночные сборки.
   - ⛔ *Канал CANARY заблокирован для публичных SDK*.
6. **Два формата UI**:
   - `UpdateType.FLEXIBLE` *(по умолчанию)* — неоновая шторка (Bottom Sheet) с чейнджлогом, прогресс-баром и кнопкой «Позже».
   - `UpdateType.IMMEDIATE` — полноэкранный блокирующий интерфейс для обязательных обновлений.

---

## 📦 Шаг 1. Подключение AAR к проекту

### 1. Добавление файла AAR
Скопируйте `ipos-store-sdk-release.aar` (находится в `ipos-store-sdk/build/outputs/aar/ipos-store-sdk-release.aar`) в папку `app/libs/` вашего проекта:

```
my-application/
├── app/
│   ├── libs/
│   │   └── ipos-store-sdk-release.aar
│   └── build.gradle.kts
```

### 2. Конфигурация `app/build.gradle.kts`
Подключите локальный AAR и транзитивные библиотеки Jetpack Compose & Coroutines:

```kotlin
dependencies {
    // Подключение SDK AAR
    implementation(files("libs/ipos-store-sdk-release.aar"))

    // Необходимые транзитивные зависимости Compose и AndroidX:
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.browser:browser:1.10.0")
}
```

---

## 🛡️ Шаг 2. Настройка `AndroidManifest.xml`

Манифест библиотеки содержит необходимые объявления, но в манифесте вашего приложения (`app/src/main/AndroidManifest.xml`) рекомендуется убедиться в наличии следующих разрешений:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Доступ к сети для проверки и скачивания APK -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Разрешение на запрос установки APK (Android 8.0+) -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

    <!-- Видимость сервиса Ipos Store для Android 11+ (API 30+) -->
    <queries>
        <package android:name="com.ipos.store" />
        <intent>
            <action android:name="com.ipos.store.action.UPDATE_SERVICE" />
        </intent>
    </queries>

    <application
        android:name=".MyApp"
        ...>
        <!-- Ваши активности -->
    </application>
</manifest>
```

> [!TIP]
> **Конфликты FileProvider исключены**: библиотека использует изолированный класс `com.ipos.store.sdk.internal.IposSdkFileProvider`, поэтому в вашем приложении может спокойно сосуществовать собственный `androidx.core.content.FileProvider` без коллизий манифест-мерджера.

---

## 🚀 Шаг 3. Инициализация в `Application`

Создайте или откройте класс вашего `Application`:

```kotlin
package com.example.myapp

import android.app.Application
import com.ipos.store.sdk.IposStoreUpdates
import com.ipos.store.sdk.UpdateChannel
import com.ipos.store.sdk.UpdateMode

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Стандартная инициализация с периодической автопроверкой:
        IposStoreUpdates.init(
            context = this,
            channel = UpdateChannel.RELEASE,          // "release", "beta" или "nightly"
            mode = UpdateMode.AUTO,                   // AUTO, DIRECT или STORE_ONLY
            enablePeriodicChecks = true,              // Включить автопроверку обновлений
            periodicCheckIntervalHours = 24L,         // Проверять раз в 24 часа
            autoShowPeriodicUpdateDialog = true       // Автоматически открывать диалог при нахождении
        )
    }
}
```

*Убедитесь, что класс зарегистрирован в манифесте: `<application android:name=".MyApp" ...>`.*

---

## 📱 Шаг 4. Отображение окна обновления в `Activity`

В вашей главной или стартовой `Activity`:

```kotlin
package com.example.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ipos.store.sdk.IposStoreUpdates

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверяет обновление на сервере.
        // Если новая версия найдена — автоматически открывает диалог Biolume.
        // Окно автоматически адаптируется под системную светлую/тёмную тему!
        IposStoreUpdates.showUpdateIfAvailable(this)
    }
}
```

---

## ⏱️ Шаг 5. Периодическая автопроверка обновлений

Вы можете настроить автопроверку как при инициализации (Шаг 3), так и динамически в коде:

```kotlin
// Включение автопроверки раз в 12 часов с пользовательским коллбэком:
IposStoreUpdates.enablePeriodicChecks(
    intervalHours = 12L,
    autoShowDialog = true
) { updateInfo ->
    println("Найдено обновление: v${updateInfo.latestVersionName}")
}

// Отключение автопроверки (если пользователь отключил в настройках приложения):
IposStoreUpdates.disablePeriodicChecks()

// Ручная проверка наступления срока:
if (IposStoreUpdates.isPeriodicCheckDue(intervalHours = 12L)) {
    IposStoreUpdates.showUpdateIfAvailable(this)
}
```

### Как работает автопроверка под капотом:
- SDK запоминает таймштамп последней проверки в локальном `SharedPreferences`.
- При выходе приложения из фона (`onActivityResumed`) SDK проверяет, прошло ли `intervalHours` с момента последней проверки.
- Проверка выполняется легковесным запросом в фоновом пуле корутин без пробуждения системы в глубоком сне и без расхода заряда батареи.
- При обнаружении новой версии автоматически отображается диалог обновления.

---

## 🔑 Шаг 6. Механика выдачи разрешения на установку (Android 8.0+)

Начиная с Android 8.0 (API 26), наличие `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` объявляет возможность установки, но пользователь обязан включить переключатель **«Разрешить установку из этого источника»** в настройках устройства.

В `ipos-store-sdk` этот процесс автоматизирован:
1. Пользователь нажимает кнопку **«Обновить»**.
2. Файл APK скачивается в защищенный кэш приложения.
3. Если системное разрешение ещё не выдано:
   - В шторке/экране появляется статус **«Требуется разрешение»** и кнопка **«В настройки»**.
   - Нажатие на кнопку сразу открывает экран настроек нужного приложения (`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`).
4. Пользователь включает тумблер и нажимает стрелку «Назад» в системе.
5. При возвращении в приложение SDK в методе `onResume` детектирует выдачу разрешения и **мгновенно запускает системное окно установки APK без повторного скачивания!**

---

## 🎨 Шаг 7. Использование в Jetpack Compose (Pure Compose)

Если ваше приложение использует Compose без XML-активностей, можно встроить компонент обновления прямо в корень Compose-дерева:

```kotlin
@Composable
fun MainApp() {
    MaterialTheme {
        AppNavigation()

        // Встроенный хост диалогов обновления Ipos Store SDK
        IposStoreUpdates.IposUpdateHost()
    }
}
```

---

## 🧩 Шаг 8. Ручной контроль процесса (Custom UI)

Если вы хотите отрисовать собственный экран обновлений или управлять логикой вручную:

```kotlin
lifecycleScope.launch {
    // 1. Проверка наличия обновления
    val info = IposStoreUpdates.checkUpdate(channel = UpdateChannel.RELEASE)
    if (info != null) {
        // 2. Старт скачивания
        IposStoreUpdates.startUpdate(info.latestVersionCode)
    }
}

// 3. Подписка на состояния процесса
lifecycleScope.launch {
    IposStoreUpdates.updateState.collect { state ->
        when (state) {
            is UpdateState.Idle -> {}
            is UpdateState.Checking -> println("Проверка...")
            is UpdateState.Available -> println("Доступно: ${state.info.latestVersionName}")
            is UpdateState.Downloading -> println("Загрузка: ${(state.progress * 100).toInt()}%")
            is UpdateState.PermissionRequired -> {
                println("Нужно разрешение на установку APK")
                // Открыть настройки:
                IposStoreUpdates.openInstallPermissionSettings()
            }
            is UpdateState.ReadyToInstall -> {
                println("APK скачан: ${state.apkFile.name}")
                // Запуск установки:
                IposStoreUpdates.installDownloadedApk(state.apkFile)
            }
            is UpdateState.Installing -> println("Установка...")
            is UpdateState.Completed -> println("Обновление завершено")
            is UpdateState.Error -> println("Ошибка: ${state.message}")
            is UpdateState.StoreNotInstalled -> {}
        }
    }
}
```

---

## 📋 Таблица состояний `UpdateState`

| Состояние | Описание |
|---|---|
| `UpdateState.Idle` | Спокойное состояние (нет активных процессов). |
| `UpdateState.Checking` | Выполняется сетевой запрос проверки обновлений. |
| `UpdateState.Available(info)` | Найдено обновление с описанием `UpdateInfo`. |
| `UpdateState.Downloading(progress)` | Идёт скачивание APK (`progress` от 0.0 до 1.0). |
| `UpdateState.PermissionRequired(apkFile)` | APK скачан, но требуется включить «Установку неизвестных приложений». |
| `UpdateState.ReadyToInstall(apkFile)` | APK готов к установке (можно повторно запустить системный установщик). |
| `UpdateState.Installing` | Запущен системный установщик или сессия PackageInstaller. |
| `UpdateState.Completed` | Процесс обновления успешно завершён. |
| `UpdateState.Error(message)` | Произошла ошибка (сеть, невалидный APK, сбой сервиса). |
| `UpdateState.StoreNotInstalled` | Режим `STORE_ONLY`, но приложение Ipos Store не найдено. |

---

## ❓ FAQ и решение проблем (Troubleshooting)

### 1. Окно установки не открывалось после загрузки
- **Причина 1**: Не было выдано системное разрешение «Установка неизвестных приложений». Начиная с Android 8.0, SDK теперь автоматически предлагает пользователю перейти в настройки и продолжит установку сразу после возвращения.
- **Причина 2**: Конфликт `FileProvider`. В старых версиях использовался общий `androidx.core.content.FileProvider`. В текущей версии SDK использует собственный класс `com.ipos.store.sdk.internal.IposSdkFileProvider`, что гарантирует отсутствие конфликтов с хост-приложением.

### 2. Окно обновления было тёмным даже при светлой теме системы
- В текущей версии диалог полностью переведён на семантические токены Material 3 и Biolume Design System: в светлой теме применяется палитра **Tidepool** (`#F2F0E9`, `#0E7FA3`), в тёмной — **Abyss** (`#0E1214`, `#35C7E8`).

### 3. Как протестировать прямое обновление локально?
1. Соберите версию приложения с меньшим `versionCode` (например, `1`).
2. Убедитесь, что на сервере зарегистрирована версия с большим `versionCode` (например, `2`).
3. Запустите приложение с `UpdateMode.DIRECT`. Нажмите «Обновить» — APK скачается и откроется системный установщик Android.
