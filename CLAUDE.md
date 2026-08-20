# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build (canary channel = debug only; applicationIdSuffix ".canary")
./gradlew assembleDebug
./gradlew assembleRelease                 # minify + shrink + proguard

# Unit tests (JVM, no device)
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "by.iposdev.visorlink.utils.OutboxManagerTest"
./gradlew testDebugUnitTest --tests "by.iposdev.visorlink.ui.screens.auth.AuthViewModelTest.login with blank email sets error"

# Instrumented tests (needs device/emulator)
./gradlew connectedDebugAndroidTest

# Lint (AGP built-in only — no ktlint/detekt/spotless configured)
./gradlew lintDebug

# What CI actually runs
./gradlew testDebugUnitTest assembleRelease -PcommitId=<short-sha> --parallel --build-cache --configuration-cache
```

Toolchain: Gradle 9.7, daemon JVM 21 (auto-provisioned via foojay), AGP 9.3.1, Kotlin 2.4.10, compileSdk/targetSdk 37, minSdk 30, Java 11 source/target compatibility.

`-PcommitId` stamps `BuildConfig.CommitID` and the `-canary+<sha>` versionName suffix. Local builds shell out to `git rev-parse --short HEAD` instead. `versionCode`/`versionName` and `currentChannel` are bumped **manually** in `app/build.gradle.kts` — CI greps `CHANNEL` out of that file to decide which release channel it is publishing to.

## Architecture

Single Gradle module `:app`, package `by.iposdev.visorlink`. 100% Kotlin + Compose (no Fragments, no XML layouts beyond the launcher theme). `MainActivity` is an `AppCompatActivity` with one `setContent`.

### Composition root

`MainActivity.onCreate` nests the whole app: `VisorLinkTheme` → `AppCheckGuard` → `AppUpdateWrapper` → `Box { VisorLinkNavGraph(); FlagsOverlay() }`. Each wrapper can gate or overlay the entire UI, so anything that must apply app-wide belongs at this level, not inside a screen.

`VisorLinkApp.onCreate` (Application) does the eager init in order: Sentry → notification channels → Firebase App Check (debug provider via reflection, PlayIntegrity otherwise) → `startKoin` → image loader + cache eviction → `OutboxManager` instantiation (its constructor starts the drain loop) → flags fetch → diary-reminder observer → process-lifecycle foreground tracking (`ActiveChatTracker`) → auth-state listener that owns `PresenceManager` and FCM token sync.

### DI: Koin, one module

Everything lives in `di/AppModule.kt`. Repositories/managers are `single`, ViewModels are `viewModel { }`. **Any new repository, manager, or ViewModel must be registered here** — there is no annotation processing.

ViewModels needing runtime arguments (`ChatViewModel`, `CommentsViewModel`, `OtherProfileViewModel`, `ChatSettingsViewModel`) use `viewModel { parameters -> ... parameters.get() }`. Screens resolve VMs with `koinViewModel()`.

To share a ViewModel with an ancestor screen, NavGraph scopes it to another back stack entry — e.g. `DiaryEntry` and `Comments` do `koinViewModel(viewModelStoreOwner = navController.getBackStackEntry(Screen.ChatList.route))` so they see `MainScreen`'s state. Follow that pattern rather than hoisting state into a singleton.

### Navigation is reactive, not imperative

`ui/Screen.kt` holds route templates plus `createRoute()` builders. `ui/NavGraph.kt` is one flat `NavHost`.

Routing between app phases is driven by a single `LaunchedEffect(authState, isStealthUnlocked, showOnboarding, isTfaRequired)` that issues `navigate { popUpTo(0) { inclusive = true } }`. Precedence: **stealth → onboarding → 2FA → authState**. Consequently `onLoginSuccess`, `onRegistrationComplete`, `onVerified`, `onFinish` callbacks are deliberately empty — screens must *not* navigate on auth transitions. To log out, call `authViewModel.logout()` and let the guard redirect.

`Screen.ImageViewer.createRoute` URL-encodes its argument; do the same for any new route carrying a URL.

### Auth

`AuthState` is three-state: `NoSession` / `Unverified(user)` / `Verified(user)`. `AuthRepository.authState` is a `callbackFlow` registering **both** an `AuthStateListener` and an `IdTokenListener` — the latter exists because `AuthStateListener` does not fire when `isEmailVerified` flips. Anonymous `object :` implementations are used instead of lambdas to work around a Kotlin `UnknownInitialization` compiler bug.

Registration does not write Firestore directly (rules require `email_verified == true`); it calls the `createUserProfile` Cloud Function. Functions region is `europe-west1`.

2FA session state is cached per Firebase `auth_time` in `TfaManager` (`EncryptedSharedPreferences`, `visorlink_tfa_prefs`).

### Two backends live side by side

Production path is Firebase: Firestore (offline persistence on, unlimited cache), Auth, Functions, RTDB (presence), Storage, Messaging, Remote Config, App Check.

An experimental REST + WebSocket backend lives in `data/remote/chat/` (`VisorLinkApi`, `ChatWebSocketClient`, `BackendModels`). It is active only when **both** are true:

```kotlin
ChatRepository.isBackendEnabled() = flags.isEnabled("test_backend_enabled") && prefs("use_custom_backend")
ChatRepository.isFirestoreDisabled()  // can additionally kill Firestore entirely
```

The Retrofit base URL `http://10.0.2.2:8080` is a **placeholder**: `DynamicBaseUrlInterceptor` rewrites host/port at request time from `visorlink_backend_settings` prefs, and `FirebaseAuthInterceptor` attaches the ID token. When editing chat/user/feed data flows, handle both paths — note the offline cache key gains a `_backend` suffix in that mode, and DTO→domain mapping happens via private `toDomain()` extensions inside `ChatRepository`.

### Offline-first: hand-rolled SQLite cache + outbox

`utils/ChatDataCache.kt` (`LocalCacheDB`) stores chats, messages, profiles, sticker packs, and likes as JSON blobs, plus an **outbox** table of `QueuedAction`s. Repositories emit cached data first, then network data, from the same `channelFlow`.

`utils/OutboxManager.kt` drains the outbox on a 2s poll loop, gated on `NetworkMonitor.isOnline`. Actions are grouped by `chatId`: **parallel across chats, strictly serial within a chat**, and the first failure in a chat breaks that chat's loop so message order survives. Media uploads additionally pass through `mediaSemaphore`. Backoff is `retryCount * 10s` up to `MAX_RETRIES = 5`, after which status flips to 2 (failed). Its constructor takes `OutboxDataSource`, `CdnUploader`, and a `CoroutineDispatcher` purely as test seams — keep that injection intact when modifying it.

There is a `datastore-preferences` dependency but **no DataStore usage**; all settings are `SharedPreferences`.

### Media / CDN

`utils/CdnService.kt` talks to `https://api.visorlink.org` with raw `HttpURLConnection` and Firebase ID tokens: SHA-256 content hash for dedup, `public`/`vault` zones, multipart upload with progress, and a 401 → force-token-refresh retry. Quota errors (413) and overload (503) surface as user-facing Russian messages. Local layers: `ImageCache`, `VoiceCache`, `CacheManager` (size-capped eviction), `AppImageLoader` (Coil).

### Feature flags ("Aegis" flags service)

`FlagsRepository` + `data/remote/flags/`: on first run it generates a keypair (`AegisKeyManager`), pairs with `https://flags.visorlink.org` to get a `device_id`, then fetches config by signing `(device_id, timestamp, nonce)`. The response is a **JWT whose claims are the flags**.

Reading a flag needs no code change anywhere — `flags.isEnabled("some_key")` resolves local overrides first, then known fields, then `serverClaims`. `FlagFlipperScreen` (unlocked once `test_flag` arrives) writes local overrides; `FlagsOverlay` renders a debug overlay above the entire nav graph.

### Other subsystems

- **Aegis assistant** (`data/aegis/`, `ui/aegis/`): `AegisBrainEngine` interface with a `DictionaryHeuristicEngine` implementation (bundled `res/raw/heuristic_dictionary.json` plus a remote dict URL from flags) and a `MediaPipeLlmEngine` stub. Debug UI behind the `is_aegis_debug_mode` flag.
- **Self-hosted updater** (no Play Store): `UpdateApiClient` → `https://update-android.visorlink.org/api` with a generated `install_id` and channel (RELEASE/BETA/NIGHTLY/CANARY); `ApkDownloader` verifies SHA-256 before install. `AppUpdateWrapper` can force-block the UI on a required update. CI uploads the signed APK to that server and announces to Telegram.
- **Stealth mode**: `StealthManager` (salted SHA-256 PIN) plus `ui/screens/decoy/` — a fake news app that is the NavHost start destination when enabled, and re-locks on `Lifecycle.Event.ON_STOP`.
- **Biometrics**: used only by `SavedMessagesViewModel` and `DiaryViewModel` (`biometric_prefs`).

### Theming

Three themes, selected at runtime: `AppTheme.MATERIAL3_EXPRESSIVE` (clean M3E), `AppTheme.BIOLUME` (neumorphic relief), and `AppTheme.FORGE` (industrial, square edges, hard shadows). `AppTheme.id` is the stable persistence key (`"m3e"`, `"biolume"`, `"forge"`).

**The whole system hangs off one CompositionLocal.** `VisorLinkTheme` provides `LocalVlTokens` alongside `MaterialTheme`, carrying everything M3 has no role for: neumorphic depth, hard-edge shadows (Forge), signal glow, `success`/`warning`, and monospace `data*` text roles. Read it as `VlTheme.tokens`.

> **Components adapt to the theme; screens never mention it.** No composable takes `appTheme` as a parameter. A screen calls `VlSurface(...)` / `VlButton(...)` identically in both themes. The deleted theme system (pre-94a7fbb) threaded `appTheme: AppTheme` through every component signature — that is the mistake this design exists to avoid. If you find yourself wanting to pass a theme down, add a token instead.

Files in `ui/theme/`:

| File | Holds |
|---|---|
| `VlTokens.kt` | `VlTokens` contract, `VlDepth` (Raised/Inset/Flat), `LocalVlTokens`, `VlTheme` accessor |
| `BiolumePalette.kt` | Abyss (dark) / Tidepool (light) `ColorScheme`s, Biolume shapes, token factories |
| `VlDepth.kt` | `Modifier.vlRaised` / `vlInset` / `vlSignalGlow` / `vlSignalBorder` / `vlHairline` / `vlBiopulse` |
| `Type.kt` | Font families, per-theme type scales, the `data*` roles, `BiolumeBrandStyle` |
| `Theme.kt` | `VisorLinkTheme` — dispatches scheme/shapes/typography/tokens |

**Fonts.** Biolume runs on **Inter** (all text roles) + **JetBrains Mono** (`data*` roles). Space Grotesk is bundled but used *only* by `VlBrandText` for the "VisorLink" wordmark, because **it contains zero Cyrillic glyphs** — all 66 letters are absent from its cmap. Guideline §5 assigns it display/headline/titleLarge, but this app is Russian-first, so that would render Russian headlines in system Roboto and mix two faces inside strings like "Чат с Ivan". If you ever move a role onto Space Grotesk, verify the text is Latin-only first. M3E deliberately stays on system Roboto — "clean M3E" includes its font.

`BiolumeTypography` sets the family on **every** M3 role, not just the ones §5 names. The guideline only specifies eight, but leaving the rest on the default would mix Inter with Roboto in the same screen (`bodySmall`, `titleSmall`, `labelMedium` are all widely used). Sizes and line heights stay at M3 defaults, per §5.

Depth modifiers are deliberately **non-composable** and take tokens explicitly, so they can sit in conditional modifier chains without breaking composition. They no-op when `structure.enabled == false`, which is how one component body serves both themes — write the M3 path, add `.vlStructure(tokens.structure, depth, shape)`, done.

Biolume's two rules, both from the guidelines, both easy to violate accidentally:

1. **Structure ≠ signal.** Neutral relief gives shape and depth; color/glow appears *only* when an element is reporting something (focus, press, live). Nothing glows at rest except `VlFab`.
2. **One glow per screen.** Usually spent on the focused `VlTextField`. This is a convention, not an enforced invariant — see TODO.md.

Raised vs Inset is semantic, not decorative: things that *press on* something are raised (cards, chips at rest, switch thumb, icon trays, settings sections, chat list rows, the input panel); things that *receive* are inset (text fields, switch track, pressed buttons, selected chips, the selected nav pill, quoted-reply blocks).

**Relief needs clearance.** `vlRaised` draws outside the component's bounds, so a raised element wants ≥6–8dp of air. Where there isn't any — `ChatListItem` in compact mode, chat bubbles — use `vlHairline` alone instead; overlapping soft shadows read as dirty bands, which is worse than no relief. That trade-off is why bubbles have no relief at all (they also read `tokens.bubbles` instead of `colorScheme`, since Biolume's `primaryContainer` at alpha .12 is too faint for a bubble).

`tokens.selectionFill` is the opaque fill for a selected pill/chip/segment. It exists because `primaryContainer` in Biolume is `primary` at 12% alpha (§3), which over a container surface reads as *no selection at all*. Reach for it any time "selected" needs to be visible; `primaryContainer` alone is not enough in this theme.

`vlBiopulse` is the only animated glow in the app and belongs solely to live indicators (`VlLiveDot`, `AvatarWithPresence`). It falls back to a static peak-intensity glow when the system animation scale is 0 — `VisorLinkTheme` reads that once into `tokens.reduceMotion` so components don't each query `Settings`. `VlAmbientGlow` is disabled entirely in Biolume (three always-on colored blooms contradict rule 1).

`ColorPreset` re-tints only the **signal** layer (primary + glow) in Biolume; Abyss/Tidepool surfaces stay canonical, since those surfaces are the theme's identity. `DEFAULT` means the exact guideline colors. In M3E the preset still switches dynamic color off in favour of the static schemes, as before.

`withSignalAccent` must recompute `onPrimary`/`onPrimaryContainer` alongside `primary` — the base values are tuned for the canonical accent, and a dark preset over a dark theme otherwise yields unreadable button text. Pick the on-color by **comparing actual WCAG contrast** for black vs white, never by a luminance threshold: `#0EA5E9` (preset BLUE) reads as a light color at luminance 0.33, yet white on it gives 2.8:1 while dark gives 6.8:1.

`ThemeContrastTest` is the guard for all of the above: it checks every `on*`/`*` pair in both palettes, every `ColorPreset` override, and the §10 invariants, at a full 4.5:1. It implements WCAG itself rather than calling `Color.luminance()`, so the formula is part of the assertion. It has already caught three real defects (two under-contrast `on*Container` roles and the preset bug), so treat a failure there as a genuine finding, not as a threshold to relax.

`ThemeViewModel` is a `SharedPreferences` façade over `visorlink_settings` that registers an `OnSharedPreferenceChangeListener` on itself, so every instance across every screen stays in sync. **Setters write prefs, not state** — never assign to the `MutableStateFlow`s directly.

`UserProfileTheme` applies a PRO user's own theme/accent/font when viewing their chat or profile, gated by `CustomizationHelper.shouldApplyCustomization`; the keys are `theme` / `accent` / `font` in `UserProfile.customization`.

**Adding a theme:** add an `AppTheme` entry with a fresh `id`, build its `ColorScheme` + `VlShapeTokens`, add the branch in `VisorLinkTheme` (the `when` is exhaustive, so the compiler will point at every place needing an arm), and add its name/description strings. The selector picks it up automatically — it iterates `AppTheme.entries`. No component needs touching.

### UI conventions

Shared composables in `ui/components/` are prefixed `Vl`: `VlSurface`, `VlCard`, `VlButton`, `VlSwitch`, `VlSegmentedControl`, `VlSettingsSection`/`VlSettingsItem`, `VlOptionRow`, `VlDialog`, `VlToast`, `VlAmbientGlow`, `VlGlassPanel`, `VlTextField`, `VlFab`, `VlLiveDot`/`VlPresenceDot`, `VlNavigationBar`, `VlBrandText`. Two base containers, distinct on purpose: `VlSurface` is the general container (grouped-list corners via `index`/`total`, raised↔inset dispatch, press feedback), `VlCard` is content-card only (always raised + hairline in Biolume, plain M3 `Card` otherwise). Feature-specific composables go in `ui/components/<feature>/`.

`VlNavigationBar` (in `ui/components/VlNavBar.kt`, moved out of `MainScreen`) is a full-width floating stadium bar: the **container** carries the structure (raised + hairline), the **active item** is a *flat* `selectionFill` pill — no inset, no glow. §4.2 lists "чип, вкладка, nav-item" under flat `*Container` fill, so the chip rule from §7 (inset on select) does **not** apply here; a 30dp-tall pill with an inset shadow reads as a smudge. Three constraints this component learned the hard way:

- The indicator is a **sibling** of the icon, never its parent. Scaling a parent scales the icon with it, which silently rendered unselected icons at 70% size.
- It applies `navigationBarsPadding()` itself, because `MainScreen`'s Scaffold zeroes `contentWindowInsets`, so `innerPadding.calculateBottomPadding()` is 0 and the bar would sit in the gesture area.
- Items get `weight(1f)`, not content sizing — with two tabs a content-sized bar collapsed to ~164dp and read as an accident, and uneven label lengths made the items ragged.

Tab labels come from `nav_tab_*`, not the screen titles: `chatlist_title` is `"VisorLink"`, which as a tab label sat oddly next to "Discover" and "Дневник".

The theme selector lives in `ui/components/settings/ThemeSelector.kt` (`VlThemeSelector`) and renders each option's preview by wrapping **real components in a real `VisorLinkTheme`** — a preview therefore cannot drift from what the theme actually looks like. `ThemePreviewGrid` is a thin back-compat wrapper over it. It is wired into both `SettingsScreen` and the onboarding appearance step.

`MainScreen` is a tab host (Chats / Discover / Diary) whose bottom bar appears only if `diaryEnabled || discoverEnabled`; the Diary route is intentionally absent from NavGraph to avoid a duplicate PIN prompt. Its nav bar now lives in `ui/components/VlNavBar.kt` — it was theme-dependent drawing inside a screen, which the rule above forbids.

Neumorphic relief draws **outside** a component's bounds, so a Biolume raised element needs ≥6–8dp of clearance; a tight parent with `clip()` will shear the shadow off. Keep that in mind when adding padding-free containers.

### SharedPreferences files

`visorlink_settings` (theme, locale, UI toggles, onboarding version) · `visorlink_flags_prefs` (device_id, flag overrides) · `visorlink_backend_settings` (custom backend toggle + URL) · `visorlink_stealth_prefs` · `visorlink_update_prefs` (install_id, channel) · `visorlink_drafts` · `visorlink_tfa_prefs` (encrypted) · `biometric_prefs` · `fcm_prefs`.

## Conventions

- Code comments and commit messages are in Russian; identifiers and log tags in English. Match that.
- UI strings belong in `values/strings.xml` + `values-ru/strings.xml` (kept in lockstep, one entry per key in both) and are read with `stringResource`. ~32 files still hold hardcoded Russian literals — prefer extracting when you touch them.
- Tests are JUnit4 + `mockito-kotlin` + `kotlinx-coroutines-test`, with backtick method names and `Dispatchers.setMain(testDispatcher)`. `unitTests.isReturnDefaultValues = true`, so Android stubs return defaults instead of throwing. Existing coverage is ViewModel/logic only.
- `Log.d`/`Log.e` calls in hot or noisy paths are wrapped in `if (BuildConfig.DEBUG)` (see `FlagsRepository`).
- `app/google-services.json` is committed intentionally.
- `TODO.md` (repo root) tracks consciously deferred work on the theme system — read it before "finishing" anything theme-related.
- `analyzer.py` is a standalone Tkinter LOC-counting toy, unrelated to the build.
