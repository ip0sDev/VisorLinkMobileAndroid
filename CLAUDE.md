# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build (canary channel = debug only; applicationIdSuffix ".canary")
./gradlew assembleDebug
./gradlew assembleRelease                 # minify + shrink + proguard

# Unit tests (JVM, no device)
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "org.visorlink.app.utils.OutboxManagerTest"
./gradlew testDebugUnitTest --tests "org.visorlink.app.ui.screens.auth.AuthViewModelTest.login with blank email sets error"

# Instrumented tests (needs device/emulator)
./gradlew connectedDebugAndroidTest

# Lint (AGP built-in only — no ktlint/detekt/spotless configured)
./gradlew lintDebug

# Screenshot tests (Roborazzi + Robolectric, JVM): every scene × AppTheme × light/dark
./gradlew recordRoborazziPlayDebug        # rewrite goldens in app/src/test/screenshots
./gradlew verifyRoborazziPlayDebug        # fail on any visual diff
./gradlew compareRoborazziPlayDebug       # write diffs to app/build/outputs/roborazzi, don't fail

# UI rules: no new Color(0x…) outside ui/theme, no new raw M3 components in ui/screens
./gradlew checkUiRules                    # ratchet against config/ui-rules-baseline.txt
./gradlew checkUiRules -PupdateUiBaseline # re-snapshot after removing violations

# What CI actually runs
./gradlew testDebugUnitTest assembleRelease -PcommitId=<short-sha> --parallel --build-cache --configuration-cache
```

Toolchain: Gradle 9.7, daemon JVM 21 (auto-provisioned via foojay), AGP 9.3.1, Kotlin 2.4.10, compileSdk/targetSdk 37, minSdk 30, Java 11 source/target compatibility.

`-PcommitId` stamps `BuildConfig.CommitID` and the `-canary+<sha>` versionName suffix. Local builds shell out to `git rev-parse --short HEAD` instead. `versionCode`/`versionName` and `currentChannel` are bumped **manually** in `app/build.gradle.kts` — CI greps `CHANNEL` out of that file to decide which release channel it is publishing to.

## Architecture

Single Gradle module `:app`, package `org.visorlink.app`. 100% Kotlin + Compose (no Fragments, no XML layouts beyond the launcher theme). `MainActivity` is an `AppCompatActivity` with one `setContent`.

### Composition root

`MainActivity.onCreate` nests the whole app: `VisorLinkTheme` → `ServiceModeGuard` → `AppCheckGuard` → `LegalConsentGuard` → `Box { VisorLinkNavGraph(); IdCardGate(); FlagsOverlay(); UpdateManager.UpdateHost() }`. Each wrapper can gate or overlay the entire UI, so anything that must apply app-wide belongs at this level, not inside a screen.

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

Message editing is supported in both modes. For Firestore, it requires `lastEdited` (Timestamp) and `editHistory` (Array of maps) fields. For Saved Messages, text/caption is re-encrypted with AES-GCM before update.
```

The Retrofit base URL `http://10.0.2.2:8080` is a **placeholder**: `DynamicBaseUrlInterceptor` rewrites host/port at request time from `visorlink_backend_settings` prefs, and `FirebaseAuthInterceptor` attaches the ID token. When editing chat/user/feed data flows, handle both paths — note the offline cache key gains a `_backend` suffix in that mode, and DTO→domain mapping happens via private `toDomain()` extensions inside `ChatRepository`.

### Offline-first: hand-rolled SQLite cache + outbox

`utils/ChatDataCache.kt` (`LocalCacheDB`) stores chats, messages, profiles, sticker packs, and likes as JSON blobs, plus an **outbox** table of `QueuedAction`s. DB version 5 adds a `progress` column for media uploads. Repositories emit cached data first, then network data, from the same `channelFlow`.

`utils/OutboxManager.kt` drains the outbox on a 2s poll loop, gated on `NetworkMonitor.isOnline`. Actions are grouped by `chatId`: **parallel across chats, strictly serial within a chat**, and the first failure in a chat breaks that chat's loop so message order survives. Media uploads additionally pass through `mediaSemaphore`. Backoff is `retryCount * 10s` up to `MAX_RETRIES = 5`, after which status flips to 2 (failed). Real-time progress is pushed to DB via `outboxDataSource.updateProgress` and observed by chat bubbles.

There is a `datastore-preferences` dependency but **no DataStore usage**; all settings are `SharedPreferences`.

### Media / CDN

`utils/CdnService.kt` talks to `https://api.visorlink.org` with raw `HttpURLConnection` and Firebase ID tokens: SHA-256 content hash for dedup, `public`/`vault` zones, multipart upload with progress, and a 401 → force-token-refresh retry. Quota errors (413) and overload (503) surface as user-facing Russian messages. Local layers: `ImageCache`, `VoiceCache`, `CacheManager` (size-capped eviction), `AppImageLoader` (Coil).

### Feature flags ("Aegis" flags service)

`FlagsRepository` + `data/remote/flags/`: on first run it generates a keypair (`AegisKeyManager`), pairs with `https://flags.visorlink.org` to get a `device_id`, then fetches config by signing `(device_id, timestamp, nonce)`. The response is a **JWT whose claims are the flags**.

Reading a flag needs no code change anywhere — `flags.isEnabled("some_key")` resolves local overrides first, then known fields, then `serverClaims`. `FlagFlipperScreen` (unlocked once `test_flag` arrives) writes local overrides; `FlagsOverlay` renders a debug overlay above the entire nav graph.

### Other subsystems

- **Aegis assistant** (`data/aegis/`, `ui/aegis/`): `AegisBrainEngine` interface with a `DictionaryHeuristicEngine` implementation (bundled `res/raw/heuristic_dictionary.json` plus a remote dict URL from flags) and a `MediaPipeLlmEngine` stub. Debug UI behind the `is_aegis_debug_mode` flag.
- **Updates are per flavor** (`play` / `standalone`, both expose the same `utils/UpdateManager` object; `MainActivity` renders `UpdateManager.UpdateHost()` over the nav graph). `standalone` uses the bundled Ipos Store SDK (`libs/ipos-store-sdk-release.aar`, channels release/beta/nightly, settings section «Обновления»). `play` uses Google Play In-App Updates (`app-update-ktx`, `playImplementation` only): checked on every `ON_RESUME`; the prompt (`ui/components/UpdatePromptDialog.kt`) is always dismissible and snoozed for 24 h per `availableVersionCode` in `visorlink_update_prefs`; flexible download when Play allows it, then «Перезапустить» → `completeUpdate()`. Play builds must never install APKs themselves (Device and Network Abuse policy). Off-Play installs get no prompt — test it through Play internal app sharing.
- **Stealth mode**: `StealthManager` (salted SHA-256 PIN) plus `ui/screens/decoy/` — a fake news app that is the NavHost start destination when enabled, and re-locks on `Lifecycle.Event.ON_STOP`.
- **System Status**: `StatusScreen` performs live diagnostics (CDN ping, Firestore read, Flags server ping). It features a 24h uptime timeline and incident history with duplicate suppression and local fallback for network outages. Reports incidents via `reportServiceIncident` and `resolveServiceIncident` functions.
- **Biometrics**: used only by `SavedMessagesViewModel` and `DiaryViewModel` (`biometric_prefs`).
- **Sessions**: `SessionRepository.register()` calls `registerSession` on every start with `deviceModel` / `osVersion` / `appVersion`. The server trusts them (and writes `client: android`) only when the App Check token proves the official app — the Functions SDK attaches it automatically, so App Check must stay installed before the first callable. `utils/SessionDescriber` ports the web `describeSession`: `client == android` or an `okhttp/` / `Dalvik/` User-Agent → «VisorLink {ver} · {model} · Android {os}», otherwise «browser · OS».

### ID cards, Protogen / Beast modes, Mask Mode

Specs: `ANDROID_ID_CARDS_SPEC.md` (cards, modes, theme) and `ANDROID_ID_SKINS_AND_SESSIONS_SPEC.md` (skin inventory, trades in chats, Android sessions); source of truth is the web repo (`src/utils/idCardModel.js`, `src/components/idcard/*`, `src/utils/modeTheme.js`). Everything is behind Remote Config `id_cards_enabled` (local override via FlagFlipper's `localOverrides`), read by `IdCardRepository.idCardsEnabled`.

- **The client never writes** `idCards/**`, `idTrades/**`, `users.idMode` or `chats.groupId` — only the callables `issueIdCard` / `setIdMode` / `setGroupIdCard` / `idSkinInventory` / `idSkinTrade` (europe-west1). `reissueIdCard` is dead server-side (always `reason: outdated`); rolling a skin replaced it. Errors carry `details.reason` (`cooldown` + `waitMs`, `no-card`, `not-enough-bits`, `no-slot`, `equipped`, `already-listed`…) → `IdCardException`; `ui/idcard/IdSkinUi.kt` `skinErrorText` maps every reason to an `idskin_err_*` string. Reading someone else's card returns `PERMISSION_DENIED` unless the reader's own mode is special; that is `IdCardState.Denied`, not an error.
- `data/idcard/IdCardGenerator.kt` is a **byte-exact port** of `idCardModel.js` (mulberry32, `deriveDetails` call order, signature, MRZ with transliteration). `IdCardGeneratorTest` holds reference values computed by the original JS with node; if it fails, cards look different from the web. Never reorder `r()` calls.
- `data/idcard/ModeTheme.kt` ports `modeTheme.js` (tests in `ModeThemeTest` mirror `modeTheme.test.js`). `MainActivity` computes `IdModeUiState` (flag, own `idMode`, mask, chat context) and provides it as `LocalIdModeState`, plus the effective `ViewerTheme` as `LocalViewerTheme`; `ProfileAppearanceTheme` layers owner customization over **that**, not over the settings. `ChatScreen` declares its context with `DeclareChatTheme` (DM: partner's mode, bots → MODE; groups: `groupId.enabled`).
- With the flag on, the theme is decided by the mode (Biolume or Forge v2), the theme selector is hidden and `customization.theme` is ignored; with the flag off, the user's selection works as before.
- The card (`ui/components/idcard/VlIdCard.kt`, art in `CardArt.kt`) is a physical object: it looks the same in every theme. Its colors live in `ui/theme/IdCardMaterials.kt` / `IdCardPalette.kt` and its corners go through `IdCardMaterials.corner()`, deliberately **not** `shapes.adapt()`. Layout is in em (card width = 36em) like `idcard.css`. `IdCardScreenshotTest` snapshots every mode front/back.
- **Skins** (`data/idcard/IdSkinModel.kt`): a skin is a card blank (`seed` → traits + serial) in `idCards/{uid}/skins`; mode and species belong to the *wearer*, so every skin preview is drawn on the viewer's own card (`IdSkinLook.asCard(wearer)`, `IdSkinThumb`) — in a chat with Mask Mode on, as Standard, which keeps someone else's special mode hidden. Cards issued before the inventory have no `skinId` and an empty subcollection until the server migrates them on the first inventory action; `IdSkinRules.inventoryOf` shows the card itself as the single equipped *virtual* skin meanwhile. Roll cooldown counts from `rolledAt ?: reissuedAt ?: issuedAt`. Inventory UI lives in the ID card settings (`IdSkinInventory`, `IdSkinSheet`, `IdSkinRoll` — call `roll` first, animate the known result after).
- **Trades**: a chat message `type: id_trade` carries only `tradeId` (`Message.tradeId`, also persisted in `ChatDataCache`). It proves nothing — `IdTradeMessage` renders the card only if `idTrades/{tradeId}` belongs to *this* `chatId` and `messageId`, otherwise «Обмен недоступен». Posting goes through the attachment picker (`VlMediaPickerSheet` `onOpenIdTrade`, DMs and groups only, not bots); `lastMessage` previews are localized via `isIdTradePreview`. Forwarding `id_trade` is hidden. Full-screen flows (`IdOverlay`) are `Dialog`s, so screenshot them with `captureScreenRoboImage` (`IdSkinScreenshotTest`).
- Mask Mode (`utils/MaskModeManager`, prefs `visorlink_mask_prefs`) is device-local; `until == -1` means indefinite; it expires on read, on a timer and on `onResume`. UI for it and every other mode feature is shown only to special-mode users (spec §10).

### Theming

Three themes, selected at runtime: `AppTheme.MATERIAL3_EXPRESSIVE` (clean M3E), `AppTheme.BIOLUME` (neumorphic relief), and `AppTheme.FORGE` (industrial, square edges, hard shadows). `AppTheme.id` is the stable persistence key (`"m3e"`, `"biolume"`, `"forge"`).

**The whole system hangs off one CompositionLocal.** `VisorLinkTheme` provides `LocalVlTokens` alongside `MaterialTheme`, carrying everything M3 has no role for: neumorphic depth, hard-edge shadows (Forge), signal glow, `success`/`warning`, and monospace `data*` text roles. Read it as `VlTheme.tokens`. This is the load-bearing rule of the UI layer:

> **Components adapt to the theme; screens never mention it.** No composable takes `appTheme` as a parameter. A screen calls `VlSurface(...)` / `VlButton(...)` identically in all themes. The deleted theme system (pre-94a7fbb) threaded `appTheme: AppTheme` through every component signature — that is the mistake this design exists to avoid. If you find yourself wanting to pass a theme down, add a token instead.

**Forge Specifics.** Forge uses `hardEdge = true` in structure tokens (no blur on shadows), `VlPressStyle.STAMP` (element moves into its shadow), and linear 80ms transitions (`useSpring = false`). Headers use **JetBrains Mono** with wide tracking to mimic machine marking.

Files in `ui/theme/`:

| File | Holds |
|---|---|
| `VlTokens.kt` | `VlTokens` contract, `VlDepth` (Raised/Inset/Flat), `LocalVlTokens`, `VlTheme` accessor |
| `BiolumePalette.kt` | Abyss (dark) / Tidepool (light) `ColorScheme`s, Biolume shapes |
| `ForgePalette.kt` | Steel (dark) / Concrete (light) `ColorScheme`s, Forge shapes (square), tokens |
| `VlDepth.kt` | `Modifier.vlRaised` / `vlInset` / `vlSignalGlow` / `vlSignalBorder` / `vlHairline` / `vlBiopulse` |
| `Type.kt` | Font families, per-theme type scales, the `data*` roles, Forge technical typography |
| `Theme.kt` | `VisorLinkTheme` — dispatches scheme/shapes/typography/tokens |

**Fonts.** Biolume runs on **Inter** (all text roles) + **JetBrains Mono** (`data*` roles). Space Grotesk is bundled but used *only* by `VlBrandText` for the "VisorLink" wordmark, because **it contains zero Cyrillic glyphs** — all 66 letters are absent from its cmap. Guideline §5 assigns it display/headline/titleLarge, but this app is Russian-first, so that would render Russian headlines in system Roboto and mix two faces inside strings like "Чат с Ivan". If you ever move a role onto Space Grotesk, verify the text is Latin-only first. M3E deliberately stays on system Roboto — "clean M3E" includes its font.

`BiolumeTypography` sets the family on **every** M3 role, not just the ones §5 names. The guideline only specifies eight, but leaving the rest on the default would mix Inter with Roboto in the same screen (`bodySmall`, `titleSmall`, `labelMedium` are all widely used). Sizes and line heights stay at M3 defaults, per §5.

Depth modifiers are deliberately **non-composable** and take tokens explicitly, so they can sit in conditional modifier chains without breaking composition. They no-op when `structure.enabled == false`, which is how one component body serves both themes — write the M3 path, add `.vlStructure(tokens.structure, depth, shape)`, done.

Biolume's two rules, both from the guidelines, both easy to violate accidentally:

1. **Structure ≠ signal.** Neutral relief gives shape and depth; color/glow appears *only* when an element is reporting something (focus, press, live). Nothing glows at rest except `VlFab`.
2. **One glow per screen.** Usually spent on the focused `VlTextField`. This is a convention, not an enforced invariant — see TODO.md.

Raised vs Inset is semantic, not decorative: things that *press on* something are raised (cards, chips at rest, switch thumb, icon trays, settings sections, chat list rows, the input panel); things that *receive* are inset (text fields, switch track, pressed buttons, selected chips, the selected nav pill, quoted-reply blocks).

**The "liquid" variant is what users actually see.** `AppFlags.isEnabled("animation_test")` defaults to `true` (`FlagsModels.kt`), so `rememberLiquidEnabled()` branches in `VlSwitch`, `VlSettingsSection`, `VlSettingsItem`, `VlOptionRow` and `VlNavigationBar` (`NeumorphicLiquidNavBarContent`) are the production path. Screenshot tests render it too, because the stub flags use the same default. Every ad-hoc shape in `ui/components` and `ui/screens` goes through `VlTheme.tokens.shapes.adapt(shape)` or `.rounded(r)`, which collapse to `RectangleShape` on a square scale (`cardRadius == 0`, Forge) and return the shape unchanged otherwise. A bare `RoundedCornerShape` or `CircleShape` there is how Forge shipped with rounded sections, sheets and dialogs. `checkUiRules` counts them as `raw-shape`. `section` and `row` are separate shape roles because Biolume's `card` (16dp) differs from its settings section (24dp).

**Relief needs clearance.** `vlRaised` draws outside the component's bounds, so a raised element wants ≥6–8dp of air. Where there isn't any — `ChatListItem` in compact mode, chat bubbles — use `vlHairline` alone instead; overlapping soft shadows read as dirty bands, which is worse than no relief. That trade-off is why bubbles have no relief at all (they also read `tokens.bubbles` instead of `colorScheme`, since Biolume's `primaryContainer` at alpha .12 is too faint for a bubble).

`tokens.selectionFill` is the opaque fill for a selected pill/chip/segment. It exists because `primaryContainer` in Biolume is `primary` at 12% alpha (§3), which over a container surface reads as *no selection at all*. Reach for it any time "selected" needs to be visible; `primaryContainer` alone is not enough in this theme.

`vlBiopulse` is the only animated glow in the app and belongs solely to live indicators (`VlLiveDot`, `AvatarWithPresence`). It falls back to a static peak-intensity glow when the system animation scale is 0 — `VisorLinkTheme` reads that once into `tokens.reduceMotion` so components don't each query `Settings`. `VlAmbientGlow` is disabled entirely in Biolume (three always-on colored blooms contradict rule 1).

`ColorPreset` re-tints only the **signal** layer (primary + glow) in Biolume; Abyss/Tidepool surfaces stay canonical, since those surfaces are the theme's identity. `DEFAULT` means the exact guideline colors. In M3E a non-`DEFAULT` preset switches dynamic color off and is applied through `withMaterialAccent`. M3 containers are opaque (`primaryContainer` is the background of my own bubbles, `secondaryContainer` is the M3E `selectionFill`), so they are blended from surface + accent rather than set to the accent. `primary` is pushed to ≥3:1 against the background, because preset BLUE alone gives 2.7:1 there. Before this, the preset only swapped in the static indigo palette and the chosen accent appeared nowhere.

`withSignalAccent` must recompute `onPrimary`/`onPrimaryContainer` alongside `primary` — the base values are tuned for the canonical accent, and a dark preset over a dark theme otherwise yields unreadable button text. Pick the on-color by **comparing actual WCAG contrast** for black vs white, never by a luminance threshold: `#0EA5E9` (preset BLUE) reads as a light color at luminance 0.33, yet white on it gives 2.8:1 while dark gives 6.8:1.

`ThemeContrastTest` is the guard for all of the above: it checks every `on*`/`*` pair in both palettes, every `ColorPreset` override, and the §10 invariants, at a full 4.5:1. It implements WCAG itself rather than calling `Color.luminance()`, so the formula is part of the assertion. It has already caught three real defects (two under-contrast `on*Container` roles and the preset bug), so treat a failure there as a genuine finding, not as a threshold to relax.

`ThemeViewModel` is a `SharedPreferences` façade over `visorlink_settings` that registers an `OnSharedPreferenceChangeListener` on itself, so every instance across every screen stays in sync. **Setters write prefs, not state** — never assign to the `MutableStateFlow`s directly.

Profile customization is typed: `data/model/ProfileAppearance.kt` is the only place that parses `UserProfile.customization` (keys `theme`, `accent`, `font`, `layout`, `bgUrl`, `gifUrl`, `emojis`) and the only place that decides visibility. `ProfileAppearance.resolve(owner, viewer)` always shows your own customization, and shows someone else's only when they have active PRO and the viewer's `ignoreCustomizations` is off. Use it everywhere, including the chat wallpaper "other" mode, instead of reading the map. `writeTo` preserves unrelated keys (the backend keeps `diaryEnabled` and similar in the same map). `UserProfileTheme(profile, currentUser)` → `ProfileAppearanceTheme(appearance)` applies the result over the viewer's own settings; light/dark always stays the viewer's. It wraps `ChatScreen`, `ProfileScreen` and `OtherProfileScreen`, and the editor's `ProfilePreview`, so the preview is drawn by the same code path other users see. The VM-free overload exists for screenshot tests. The `rounded` font is bundled Nunito; previously it mapped to `SansSerif`, which is Roboto.

**Forge v2** (`ForgeV2Palette.kt`, `AppTheme.FORGE_PROTOGEN` / `FORGE_BEAST`, `selectable = false`) is the special-mode terminal from the web: dark only, `structure.outline` (1dp `primary` 16% contour) instead of relief, `VlShapeTokens.maxRadius` clamps every `adapt()`/`rounded()` shape to 8–10dp, `VlTokens.terminal` gives section titles a mono-caps label (Protogen: `> ` prefix) and scanlines. It is never read from prefs or profiles (`AppTheme.fromId` only knows selectable themes). The old `AppTheme.FORGE` is `@Deprecated` and will be removed once the flag ships.

**Adding a theme:** add an `AppTheme` entry with a fresh `id`, build its `ColorScheme` + `VlShapeTokens`, add the branch in `VisorLinkTheme` (the `when` is exhaustive, so the compiler will point at every place needing an arm), and add its name/description strings. The selector picks it up automatically — it iterates `AppTheme.selectableEntries`. No component needs touching.

### UI conventions

Shared composables in `ui/components/` are prefixed `Vl`: `VlSurface`, `VlCard`, `VlButton`, `VlSwitch`, `VlSegmentedControl`, `VlSettingsSection`/`VlSettingsItem`, `VlOptionRow`, `VlDialog`, `VlToast`, `VlAmbientGlow`, `VlGlassPanel`, `VlTextField`, `VlFab`, `VlLiveDot`/`VlPresenceDot`, `VlNavigationBar`, `VlBrandText`, `VlTopAppBar`.

`VlTopAppBar` should be used instead of `TopAppBar` on main screens. Media viewers (`ImageViewerScreen`, `ImageEditorScreen`) are the deliberate exception: their bars are transparent over the image.

Dialogs are `VlAlertDialog` + `VlDialogButton`, never M3 `AlertDialog`/`TextButton`. One overload mirrors the M3 API (`confirmButton` / `dismissButton` / `icon`), so moving a screen over is a rename; use `isDestructive` instead of `colors = …error`. It enforces M3's 280dp minimum width. Category icon tints in settings live in `VlCategoryTint`, not as `Color(0x…)` in the screen. In Forge it uses `surfaceContainerHigh` with a bottom hairline.

`VlFab` supports both standard icon-only and extended (icon + text) modes. It also has a `content` slot for custom icon morphs.

`VlNavigationBar` (in `ui/components/VlNavBar.kt`, moved out of `MainScreen`) is a full-width floating stadium bar: the **container** carries the structure (raised + hairline), the **active item** is a *flat* `selectionFill` pill — no inset, no glow. §4.2 lists "чип, вкладка, nav-item" under flat `*Container` fill, so the chip rule from §7 (inset on select) does **not** apply here; a 30dp-tall pill with an inset shadow reads as a smudge. Three constraints this component learned the hard way:

- The indicator is a **sibling** of the icon, never its parent. Scaling a parent scales the icon with it, which silently rendered unselected icons at 70% size.
- It applies `navigationBarsPadding()` itself, because `MainScreen`'s Scaffold zeroes `contentWindowInsets`, so `innerPadding.calculateBottomPadding()` is 0 and the bar would sit in the gesture area.
- Items get `weight(1f)`, not content sizing — with two tabs a content-sized bar collapsed to ~164dp and read as an accident, and uneven label lengths made the items ragged.

Tab labels come from `nav_tab_*`, not the screen titles: `chatlist_title` is `"VisorLink"`, which as a tab label sat oddly next to "Discover" and "Дневник".

The theme selector lives in `ui/components/settings/ThemeSelector.kt` (`VlThemeSelector`) and renders each option's preview by wrapping **real components in a real `VisorLinkTheme`** — a preview therefore cannot drift from what the theme actually looks like. `ThemePreviewGrid` is a thin back-compat wrapper over it. It is wired into both `SettingsScreen` and the onboarding appearance step.

`MainScreen` is a tab host (Chats / Discover / Diary) whose bottom bar appears only if `diaryEnabled || discoverEnabled`; the Diary route is intentionally absent from NavGraph to avoid a duplicate PIN prompt. Its nav bar now lives in `ui/components/VlNavBar.kt` — it was theme-dependent drawing inside a screen, which the rule above forbids.

Neumorphic relief draws **outside** a component's bounds, so a Biolume raised element needs ≥6–8dp of clearance; a tight parent with `clip()` will shear the shadow off. Keep that in mind when adding padding-free containers.

### SharedPreferences files

`visorlink_settings` (theme, locale, UI toggles, onboarding version) · `visorlink_flags_prefs` (device_id, flag overrides) · `visorlink_backend_settings` (custom backend toggle + URL) · `visorlink_stealth_prefs` · `visorlink_update_prefs` (install_id, channel) · `visorlink_drafts` · `visorlink_tfa_prefs` (encrypted) · `biometric_prefs` · `fcm_prefs` · `visorlink_mask_prefs` (Mask Mode).

## Conventions

- Code comments and commit messages are in Russian; identifiers and log tags in English. Match that.
- UI strings belong in `values/strings.xml` + `values-ru/strings.xml` (kept in lockstep, one entry per key in both) and are read with `stringResource`. ~32 files still hold hardcoded Russian literals — prefer extracting when you touch them.
- Screenshot tests live in `ui/theme/ThemeScreenshotTest.kt`. They run under a bare `Application` (no Firebase), with Koin started on a stub `FlagsRepository`, because base components such as `VlSwitch`, `VlSettingsSection` and `VlAlertDialog` call `koinInject()` through `rememberLiquidEnabled`. The Compose clock is paused (`autoAdvance = false`) because `VlLiveDot` and the FAB glow animate forever and would hang `waitForIdle`. Content sits in a `Surface`, not `Box.background`, so `LocalContentColor` is set the way `Scaffold` sets it. When a visual change is intentional, re-record and commit the PNGs together with the code. CI currently runs `compare`, not `verify`: the goldens were recorded on Windows and have not yet been confirmed pixel-identical on Linux.
- `checkUiRules` is a ratchet: it fails only when a file gains violations. After you remove some, run it with `-PupdateUiBaseline` so the lower count is locked in.
- Tests are JUnit4 + `mockito-kotlin` + `kotlinx-coroutines-test`, with backtick method names and `Dispatchers.setMain(testDispatcher)`. `unitTests.isReturnDefaultValues = true`, so Android stubs return defaults instead of throwing. Existing coverage is ViewModel/logic only.
- `Log.d`/`Log.e` calls in hot or noisy paths are wrapped in `if (BuildConfig.DEBUG)` (see `FlagsRepository`).
- `app/google-services.json` is committed intentionally.
- `TODO.md` (repo root) tracks consciously deferred work on the theme system — read it before "finishing" anything theme-related.
- `analyzer.py` is a standalone Tkinter LOC-counting toy, unrelated to the build.
