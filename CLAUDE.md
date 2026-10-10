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

Routing between app phases is driven by a single `LaunchedEffect(authState, isStealthUnlocked, showOnboarding, isTfaRequired, isSessionReady, needsGoogleSignup)` that issues `navigate { popUpTo(0) { inclusive = true } }` through `go(route)`, which skips the call when that route is already current. Precedence: **stealth → onboarding → authState; for a verified user: unfinished Google sign-up → 2FA code → ready → «Проверяем сессию…»**. Once the user is inside the app (current route not in `GATE_ROUTES`), "ready" and "checking" never navigate — a gate recomputation (e.g. 2FA switched on in settings) must not reset the back stack. Consequently `onLoginSuccess`, `onRegistrationComplete`, `onVerified`, `onFinish` callbacks are deliberately empty — screens must *not* navigate on auth transitions. To log out, call `authViewModel.logout()` and let the guard redirect.

`Screen.ImageViewer.createRoute` URL-encodes its argument; do the same for any new route carrying a URL.

### Auth

`AuthState` is three-state: `NoSession` / `Unverified(user)` / `Verified(user)`. `AuthRepository.authState` is a `callbackFlow` registering **both** an `AuthStateListener` and an `IdTokenListener` — the latter exists because `AuthStateListener` does not fire when `isEmailVerified` flips. Anonymous `object :` implementations are used instead of lambdas to work around a Kotlin `UnknownInitialization` compiler bug.

Registration does not write Firestore directly (rules require `email_verified == true`); it calls the `createUserProfile` Cloud Function. Functions region is `europe-west1`.

2FA session state is cached per Firebase `auth_time` in `TfaManager` (`EncryptedSharedPreferences`, `visorlink_tfa_prefs`).

Spec: `ANDROID_AUTH_2FA_ACCENT_SPEC.md` in the web repo (`docs/`). Its rules, all easy to break:

- **2FA gate** (`data/auth/TfaGate.kt`, computed in `AuthViewModel.tfaGate`): `Off` / `Checking` / `Required(slow)` / `Passed`. "Code needed" is decided **only by a server snapshot** of `users/{uid}/authorized_sessions/{auth_time}` (`AuthRepository.sessionAuthorizedFlow` drops `!exists && isFromCache`). 12 s of silence or a read error → `Required(slow = true)` (code screen with a note), but the listener stays alive and closes the screen itself if the doc appears. The local `TfaManager` cache is only a fast "passed" path. The gate depends on session + `tfaEnabled` + `TfaGateHold.refreshTick`, not on every profile snapshot; the 3.5 s profile watchdog only matters while the profile has not arrived. `TfaGateHold` (Koin single) keeps the code screen up for the ~0.6 s «Вход подтверждён» — the session doc arrives before the `verify2FA` response.
- **Code screen** (`TfaScreen` + `TfaLockViewModel`): `request2FA` is idempotent (`TfaCodeInfo`: `sent` / `alreadySent` / `method` / `destination` / `expiresAt` / `resendAt`), so a code is requested on every open; `SessionRepository.ensureRegistered()` runs **before** it (the server describes the device from the session card). Errors are read from `details.reason` (`TfaError`, texts in `TfaTexts`), never from the error code: delivery failure is `unavailable` + `send-failed`. Last method → `tfa_last_method` in `visorlink_settings`. Telegram is offered only when `telegramId` and `tfaTelegram == true`.
- **Never write** `tfaEnabled` / `tfaTelegram`: `set2FAEnabled {enabled, code}`, `setTfaTelegram {enabled}`, `unbindTelegram` (the old `unlinkTelegram` never existed). Telegram link fields are `telegramId` / `telegramUsername` (`tg_username` is legacy).
- **`AuthRepository.keepSession`**: linking Google or a password, re-auth and `updatePassword` mint a new `auth_time` — a new session without 2FA for the rules. Wrap every such call: it takes the old ID token *before* the action, calls `transferTfaSession {previousIdToken}` and re-registers the session.
- **Google** (`data/auth/GoogleSignIn.kt`, Credential Manager, `serverClientId = default_web_client_id`, account chooser every time). Sign-up is still invite-only: a Google account without `users/{uid}` (server snapshot, `UserRepository.profileExistsFlow`) and without the `password` provider is `needsGoogleSignup` → `GoogleSignupScreen` (username defaults to the e-mail local part, `createUserProfile {username, inviteCode}`; on failure the server keeps the Google account). Google unlink is refused without a password (`NoPasswordException`). Errors → `GoogleAuthErrors.messageRes` (`null` = user cancelled). SHA-1/SHA-256 of debug, release **and the Play signing key** must be in Firebase, or sign-in fails with a developer error.
- Settings → Account: `AccountSecurity.kt` (`SignInMethodsItems`, `TwoFactorItems`) + `AccountSecurityViewModel`; «Сменить пароль» is shown only with the `password` provider.

### Backend: Firebase only

Firestore (offline persistence on, unlimited cache), Auth, Functions, RTDB (presence), Storage, Messaging, App Check. There is no Remote Config: every flag comes from the Aegis flags service (below). The experimental REST + WebSocket backend (`data/remote/chat/`, `test_backend_enabled`) was removed; chat, profile and feed repositories have a single Firestore path.

Message editing requires `lastEdited` (Timestamp) and `editHistory` (Array of maps) fields. For Saved Messages, text/caption is re-encrypted with AES-GCM before update.

### Offline-first: hand-rolled SQLite cache + outbox

`utils/ChatDataCache.kt` (`LocalCacheDB`) stores chats, messages, profiles, sticker packs, and likes as JSON blobs, plus an **outbox** table of `QueuedAction`s. DB version 5 adds a `progress` column for media uploads. Repositories emit cached data first, then network data, from the same `channelFlow`.

`utils/OutboxManager.kt` drains the outbox on a 2s poll loop, gated on `NetworkMonitor.isOnline`. Actions are grouped by `chatId`: **parallel across chats, strictly serial within a chat**, and the first failure in a chat breaks that chat's loop so message order survives. Media uploads additionally pass through `mediaSemaphore`. Backoff is `retryCount * 10s` up to `MAX_RETRIES = 5`, after which status flips to 2 (failed). Real-time progress is pushed to DB via `outboxDataSource.updateProgress` and observed by chat bubbles.

There is a `datastore-preferences` dependency but **no DataStore usage**; all settings are `SharedPreferences`.

### Media / CDN

`utils/CdnService.kt` talks to `https://api.visorlink.org` with raw `HttpURLConnection` and Firebase ID tokens: SHA-256 content hash for dedup, `public`/`vault` zones, multipart upload with progress, and a 401 → force-token-refresh retry. Quota errors (413) and overload (503) surface as user-facing Russian messages. Local layers: `ImageCache`, `VoiceCache`, `CacheManager` (size-capped eviction), `AppImageLoader` (Coil).

Chat media (photos, albums, video/GIF, voice, audio) is uploaded to the **sender's Google Drive** (`GoogleDriveAuthManager`, `GoogleDriveMediaService`). `ChatViewModel.needsDrive { … }` runs before every media send: without a connected Drive nothing is queued — `DriveRequiredDialog` offers to connect right in the chat (silent authorization or Google's consent sheet) and the deferred send runs by itself afterwards. Inside the outbox a missing Drive is `DriveNotConnectedException`, which marks the action failed at once instead of burning retries behind a spinner.

### Feature flags ("Aegis" flags service)

`FlagsRepository` + `data/remote/flags/`: on first run it generates a keypair (`AegisKeyManager`), pairs with `https://flags.visorlink.org` to get a `device_id`, then fetches config by signing `(device_id, timestamp, nonce)`. The response is a **JWT whose claims are the flags**. A 401 means the server no longer knows the `device_id`: the client drops it, re-pairs with the same Keystore key and retries once (`FlagsRepositoryTest`). Fetches are serialized by a mutex, and `refreshIfStale` checks the age under the same lock.

Reading a flag needs no code change anywhere — `flags.isEnabled("some_key")` is true only when the server sent `true`, and a local override can only switch it **off** (overrides of keys the server did not enable are ignored, so a stale `true` never keeps a feature alive). Claims are cached in `visorlink_flags_prefs` for offline start. `FlagFlipperScreen` lists exactly the server-enabled keys and stores only "off" overrides; it and its dev-menu entry exist only while the **server** `test_flag` is on (`AppFlags.testFlag` ignores overrides, so switching `test_flag` off locally cannot lock you out). `FlagsOverlay` renders a debug overlay above the entire nav graph.

Flags in use: `test_flag` (Flipper + overlay; in debug builds the Flipper is available regardless, release needs the server flag), `aegis_debug_mode_enabled` / alias `is_aegis_debug_mode` (Aegis debug UI, together with `test_flag`), `enable_alternative_outbox` (Yandex Disk emergency relay, with the string claims `yandex_relay_token` / `yandex_client_id`), `service_mode_enabled` (see below; not overridable, never shown in the Flipper), `enable_profile_navbar` (`AppFlags.profileNavbar`: the own profile becomes the last navbar tab, see `MainScreen` below). `heuristic_dict_url` is parsed into `AppFlags.heuristicDictUrl` but not used yet (TODO in `DictionaryRepository`). Removed keys (`animation_test`, `test_backend_enabled`, `id_cards_enabled`) are pruned from saved overrides on start (`AppFlags.REMOVED_KEYS`).

### Other subsystems

- **Aegis assistant** (`data/aegis/`, `ui/aegis/`): `AegisBrainEngine` interface with a `DictionaryHeuristicEngine` implementation (bundled `res/raw/heuristic_dictionary.json`; the remote `heuristic_dict_url` from flags is not wired in yet) and a `MediaPipeLlmEngine` stub. Debug UI behind the `is_aegis_debug_mode` flag.
- **Updates are per flavor** (`play` / `standalone`, both expose the same `utils/UpdateManager` object; `MainActivity` renders `UpdateManager.UpdateHost()` over the nav graph). `standalone` uses the bundled Ipos Store SDK (`libs/ipos-store-sdk-release.aar`, channels release/beta/nightly, settings section «Обновления»). `play` uses Google Play In-App Updates (`app-update-ktx`, `playImplementation` only): checked on every `ON_RESUME`; the prompt (`ui/components/UpdatePromptDialog.kt`) is always dismissible and snoozed for 24 h per `availableVersionCode` in `visorlink_update_prefs`; flexible download when Play allows it, then «Перезапустить» → `completeUpdate()`. Play builds must never install APKs themselves (Device and Network Abuse policy). Off-Play installs get no prompt — test it through Play internal app sharing. Once the user accepts in Play's window, the version is remembered as started (`play_started_version`, 12 h TTL): the "available" prompt stays hidden until the download finishes (minimizing Play's sheet used to bring the prompt straight back). **Forced update:** the server claim `force_update_min_version` (number) above `BuildConfig.VERSION_CODE` (`AppFlags.forceUpdateRequired`) makes `UpdateHost(force = true)` cover the app with `ui/maintenance/ForceUpdateScreen` — Play starts an IMMEDIATE update once per process (then by button, or the Play listing), a downloaded update installs without asking; standalone shows Ipos Store's `IposUpdateHost(UpdateType.IMMEDIATE)` over it with «Проверить снова».
- **Service mode**: `ui/maintenance/ServiceModeGuard` reads `AppFlags.serviceMode` (`service_mode_enabled`). While it is on, the app content is not composed at all — a full-screen maintenance screen with «Проверить снова» replaces it. Flags are re-fetched on resume (at most once a minute), every 10 min while the app is in the foreground (`FlagsRepository.startAutoRefresh`, started/stopped by the process lifecycle in `VisorLinkApp`) and every 30 s while blocked, so the block appears and lifts without a restart. Offline, the last cached value applies. `users.isAdmin` can bypass until the process dies.
- **Stealth mode**: `StealthManager` (salted SHA-256 PIN) plus `ui/screens/decoy/` — a fake news app that is the NavHost start destination when enabled, and re-locks on `Lifecycle.Event.ON_STOP`.
- **Channels feed** (the Discover tab, titled «Каналы»; `ui/screens/feed/`, `data/repository/FeedRepository.kt`, `data/model/FeedModels.kt`) mirrors the web's `FeedWindow.jsx`: «Подписки» = the last `FeedPosts.PER_CHANNEL` messages of my freshest `MAX_CHANNELS` channels (one listener per channel, keyed by the *set* of channel ids so a new post does not re-create them all), «Рекомендуемые» = callable `getCuratedChannels` + `joinChannel`. The old `discover_feed` collection is not read any more: a like **is the 👍 reaction on the channel message** (callable `toggleLike {chatId, messageId, isLiked}` = the wanted state, idempotent server-side), views are `Message.viewsCount`, bumped only by the callable `recordPostViews` (one per person, dedupe docs `messages/{id}/views/{uid}`). Likes are optimistic in `FeedViewModel` with at most one request per post in flight (later taps are coalesced into the final state); a view is reported when ≥ half of a post stayed on screen for 1 s (`TrackPostViews`), never on tap. `FeedPost.likeCount` counts distinct uids, not the stored `count`. Hidden (`is_hidden`) and unpublished (`is_published == false`, except the author's own) posts are filtered client-side like the web. Share = forward (`ForwardPickerDialog`) or a plain-text system share; channels with `noForwards` hide share/copy/save.
- **Reports**: `ReportRepository` calls `submitAbuseReport {targetType, targetId, chatId, reason, comment}` (as the web's `ReportModal`); pass `chatId` for messages/posts so the server can auto-hide after three reporters. The old `submitReport` callable never existed and `/reports` is closed by the rules.
- **System Status**: `StatusScreen` performs live diagnostics (CDN ping, Firestore read, Flags server ping). It features a 24h uptime timeline and incident history with duplicate suppression and local fallback for network outages. Reports incidents via `reportServiceIncident` and `resolveServiceIncident` functions.
- **Biometrics**: used only by `SavedMessagesViewModel` and `DiaryViewModel` (`biometric_prefs`).
- **Sessions**: `SessionRepository.register()` calls `registerSession` on every start with `deviceModel` / `osVersion` / `appVersion`. The server trusts them (and writes `client: android`) only when the App Check token proves the official app — the Functions SDK attaches it automatically, so App Check must stay installed before the first callable. `utils/SessionDescriber` ports the web `describeSession`: `client == android` or an `okhttp/` / `Dalvik/` User-Agent → «VisorLink {ver} · {model} · Android {os}», otherwise «browser · OS».

### ID cards, Protogen / Beast modes, Mask Mode

Specs: `ANDROID_ID_CARDS_SPEC.md` (cards, modes, theme) and `ANDROID_ID_SKINS_AND_SESSIONS_SPEC.md` (skin inventory, trades in chats, Android sessions); source of truth is the web repo (`src/utils/idCardModel.js`, `src/components/idcard/*`, `src/utils/modeTheme.js`). There is no feature flag any more (`id_cards_enabled` was removed): everything is on for a signed-in user, and `IdModeUiState.enabled` means exactly that.

- **The client never writes** `idCards/**`, `idTrades/**`, `users.idMode` or `chats.groupId` — only the callables `issueIdCard` / `setIdMode` / `setGroupIdCard` / `idSkinInventory` / `idSkinTrade` (europe-west1). `reissueIdCard` is dead server-side (always `reason: outdated`); rolling a skin replaced it. Errors carry `details.reason` (`cooldown` + `waitMs`, `no-card`, `not-enough-bits`, `no-slot`, `equipped`, `already-listed`…) → `IdCardException`; `ui/idcard/IdSkinUi.kt` `skinErrorText` maps every reason to an `idskin_err_*` string. Reading someone else's card returns `PERMISSION_DENIED` unless the reader's own mode is special; that is `IdCardState.Denied`, not an error.
- `data/idcard/IdCardGenerator.kt` is a **byte-exact port** of `idCardModel.js` (mulberry32, `deriveDetails` call order, signature, MRZ with transliteration). `IdCardGeneratorTest` holds reference values computed by the original JS with node; if it fails, cards look different from the web. Never reorder `r()` calls.
- `data/idcard/ModeTheme.kt` ports `modeTheme.js` (tests in `ModeThemeTest` mirror `modeTheme.test.js`). `MainActivity` computes `IdModeUiState` (signed in, own `idMode`, mask, chat context) and provides it as `LocalIdModeState`, plus the effective `ViewerTheme` as `LocalViewerTheme`; `ProfileAppearanceTheme` layers owner customization over **that**, not over the settings. `ChatScreen` declares its context with `DeclareChatTheme(chatId, …)` (DM: partner's mode, bots → MODE; groups: `groupId.enabled`). The chat theme follows the **navigation back stack**, not the screen's lifetime: NavGraph reports the topmost Chat entry (`ChatThemeController.setActive`) the moment navigation happens, so the theme flips at the start of the transition — never after the exit animation (the chat list used to show in the chat's theme) and never half a second late. Until the partner/chat loads, the last known context of that chat (`PrefsChatThemeMemory`, `visorlink_chat_theme`) is used. Whole-theme changes go through `ThemeCrossfade` (snapshot fully opaque before the swap, 320 ms like the nav transition); `VisorLinkTheme` animates colors only within one style (`key(appTheme, darkTheme)`), otherwise Forge colors bled into Biolume shapes under the fade.
- After sign-in the theme is decided by the mode (Standard → Biolume, Protogen / Beast → Forge v2), the theme selector in Customization is hidden and `customization.theme` is ignored. Before sign-in (auth, onboarding) the selection from settings applies.
- The card (`ui/components/idcard/VlIdCard.kt`, art in `CardArt.kt`) is a physical object: it looks the same in every theme. Its colors live in `ui/theme/IdCardMaterials.kt` / `IdCardPalette.kt` and its corners go through `IdCardMaterials.corner()`, deliberately **not** `shapes.adapt()`. Layout is in em (card width = 36em) like `idcard.css`. `IdCardScreenshotTest` snapshots every mode front/back.
- **Skins** (`data/idcard/IdSkinModel.kt`): a skin is a card blank (`seed` → traits + serial) in `idCards/{uid}/skins`; mode and species belong to the *wearer*, so every skin preview is drawn on the viewer's own card (`IdSkinLook.asCard(wearer)`, `IdSkinThumb`) — in a chat with Mask Mode on, as Standard, which keeps someone else's special mode hidden. Cards issued before the inventory have no `skinId` and an empty subcollection until the server migrates them on the first inventory action; `IdSkinRules.inventoryOf` shows the card itself as the single equipped *virtual* skin meanwhile. Roll cooldown counts from `rolledAt ?: reissuedAt ?: issuedAt`. A skin snapshot without `mintedAt` (trades, offers) shows «Выдана» as today via `LocalIdCardClock`; screenshot tests pin that clock, otherwise their goldens drift every day. Inventory UI lives in the ID card settings (`IdSkinInventory`, `IdSkinSheet`, `IdSkinRoll` — call `roll` first, animate the known result after).
- **Trades**: a chat message `type: id_trade` carries only `tradeId` (`Message.tradeId`, also persisted in `ChatDataCache`). It proves nothing — `IdTradeMessage` renders the card only if `idTrades/{tradeId}` belongs to *this* `chatId` and `messageId`, otherwise «Обмен недоступен». Posting goes through the attachment picker (`VlMediaPickerSheet` `onOpenIdTrade`, DMs and groups only, not bots); `lastMessage` previews are localized via `isIdTradePreview`. Forwarding `id_trade` is hidden. Full-screen flows (`IdOverlay`) are `Dialog`s, so screenshot them with `captureScreenRoboImage` (`IdSkinScreenshotTest`).
- Rendering spec: `ANDROID_ID_CARD_RENDER_SPEC.md` (replaces §4 of the cards spec). **Custom skins** (`custom {base, primary, secondary, holo, label}` on the card, skins and trade snapshots → `IdCustomLook`): all three colors must be valid HEX, then `customPalette` replaces the mode palette (layout and art stay the mode's), `custom.holo` replaces the seed's hologram shape, `labelText` is printed in the edition block, and the edition is always EPIC (`IdCard.edition`). `VlIdCard(signature = …)` draws a user's own signature (`IdCardGenerator.parseSignature`, server format `M/L` only, 100×30) instead of the seed flourish — centered on the front, left-aligned on the back; invalid → flourish. Palette mixes are **sRGB component-wise** (`mix()` in `IdCardPalette.kt`), never Compose `lerp` (Oklab) — CSS `color-mix(in srgb)` is what the web does. Known conscious deviations from the web: tilt spring with overshoot + haptics and press scale, gyroscope integration instead of the rotation vector, Inter instead of Space Grotesk for the name and edition (no Cyrillic in Space Grotesk), mono weights capped at Medium (bundled fonts).
- **A failed card read is not "no card".** `IdCardRepository.cardFlow` emits `Ready(null)` only from a server snapshot; a cached "missing" is skipped, a non-permission error is `IdCardState.Unavailable`. Otherwise `IdCardGate` would show the issuance ceremony for an already issued card (it happened on the web after a new `auth_time` without 2FA).
- «Моя визитка» (ID card settings): `publicCardManage {action: publish | unpublish, tagline?, skinId?}`, state in `idCards/{uid}.publicCard` (`IdCard.publicCard`), link `https://visorlink.org/card/{slug}` opened in the browser. `publicCards/**` is closed to clients.
- Mask Mode (`utils/MaskModeManager`, prefs `visorlink_mask_prefs`) is device-local; `until == -1` means indefinite; it expires on read, on a timer and on `onResume`. UI for it and every other mode feature is shown only to special-mode users (spec §10).

### Theming

Two selectable themes: `AppTheme.MATERIAL3_EXPRESSIVE` (clean M3E) and `AppTheme.BIOLUME` (neumorphic relief), plus the mode-driven Forge v2 (below). `AppTheme.id` is the stable persistence key (`"m3e"`, `"biolume"`). The old industrial `AppTheme.FORGE` was removed; a stored `"forge"` (prefs or a profile's `customization.theme`) reads as the default / the viewer's theme.

**The whole system hangs off one CompositionLocal.** `VisorLinkTheme` provides `LocalVlTokens` alongside `MaterialTheme`, carrying everything M3 has no role for: neumorphic depth, Forge v2's outline and terminal labels, signal glow, `success`/`warning`, and monospace `data*` text roles. Read it as `VlTheme.tokens`. This is the load-bearing rule of the UI layer:

> **Components adapt to the theme; screens never mention it.** No composable takes `appTheme` as a parameter. A screen calls `VlSurface(...)` / `VlButton(...)` identically in all themes. The deleted theme system (pre-94a7fbb) threaded `appTheme: AppTheme` through every component signature — that is the mistake this design exists to avoid. If you find yourself wanting to pass a theme down, add a token instead.

Files in `ui/theme/`:

| File | Holds |
|---|---|
| `VlTokens.kt` | `VlTokens` contract, `VlDepth` (Raised/Inset/Flat), `LocalVlTokens`, `VlTheme` accessor |
| `BiolumePalette.kt` | Abyss (dark) / Tidepool (light) `ColorScheme`s, Biolume shapes |
| `ForgeV2Palette.kt` | Forge v2 (Protogen / Beast) `ColorScheme`s, shapes, outline structure, terminal tokens |
| `VlDepth.kt` | `Modifier.vlRaised` / `vlInset` / `vlSignalGlow` / `vlSignalBorder` / `vlHairline` / `vlBiopulse`, Forge v2's `vlNeonRule` / `vlTerminalBackdrop` |
| `Type.kt` | Font families, per-theme type scales, the `data*` roles, Forge v2 terminal typography |
| `Theme.kt` | `VisorLinkTheme` — dispatches scheme/shapes/typography/tokens |

**Fonts.** Biolume runs on **Inter** (all text roles) + **JetBrains Mono** (`data*` roles). Space Grotesk is bundled but used *only* by `VlBrandText` for the "VisorLink" wordmark, because **it contains zero Cyrillic glyphs** — all 66 letters are absent from its cmap. Guideline §5 assigns it display/headline/titleLarge, but this app is Russian-first, so that would render Russian headlines in system Roboto and mix two faces inside strings like "Чат с Ivan". If you ever move a role onto Space Grotesk, verify the text is Latin-only first. M3E deliberately stays on system Roboto — "clean M3E" includes its font. Forge v2 titles use **Unbounded** (Medium + SemiBold bundled, OFL in `licenses/`; SemiBold exists so bold titles such as the dialog's are not synthesized) — it is wide, so very long titles wrap sooner than in Inter.

`BiolumeTypography` sets the family on **every** M3 role, not just the ones §5 names. The guideline only specifies eight, but leaving the rest on the default would mix Inter with Roboto in the same screen (`bodySmall`, `titleSmall`, `labelMedium` are all widely used). Sizes and line heights stay at M3 defaults, per §5.

Depth modifiers are deliberately **non-composable** and take tokens explicitly, so they can sit in conditional modifier chains without breaking composition. They no-op when `structure.enabled == false`, which is how one component body serves both themes — write the M3 path, add `.vlStructure(tokens.structure, depth, shape)`, done.

Biolume's two rules, both from the guidelines, both easy to violate accidentally:

1. **Structure ≠ signal.** Neutral relief gives shape and depth; color/glow appears *only* when an element is reporting something (focus, press, live). Nothing glows at rest except `VlFab`.
2. **One glow per screen.** Usually spent on the focused `VlTextField`. This is a convention, not an enforced invariant — see TODO.md.

Raised vs Inset is semantic, not decorative: things that *press on* something are raised (cards, chips at rest, switch thumb, icon trays, settings sections, chat list rows, the input panel); things that *receive* are inset (text fields, switch track, pressed buttons, selected chips, the selected nav pill, quoted-reply blocks).

**Forge v2 moves like a terminal, not a liquid — but never shakes.** `VlMotionTokens.glitch` (read as `VlTheme.tokens.glitchMotion`, off under system "remove animations") switches the helpers in `LiquidPhysics.kt` to `ui/components/TerminalMotion.kt`: no springs, overshoot, squash/stretch or jelly impulses; everything uses `TerminalMotion.tween` (sharp start, long smooth deceleration). Cards fade in and settle 12dp from above, panels unroll evenly, the nav runner glides without stretch, screen transitions are a short shift + fade. The cyberpunk lives in the look, not in motion: `VlTopAppBar` reveals its title behind a block cursor with converging RGB ghosts (`Modifier.terminalTitleReveal`), section labels decode (`TerminalDecodeText`). Stepped easings, horizontal glitch jolts and flicker were tried and rejected — the app must stay smooth. Screenshot tests advance the clock 1.5 s, so every effect must finish within that. New liquid helpers need a terminal branch too.

**The "liquid" variant is the only one.** The `animation_test` flag and `rememberLiquidEnabled()` were removed: jelly, pop-in, pill slide-out, pour and the liquid nav runner (`NeumorphicLiquidNavBarContent`) are always on, and the helpers in `LiquidPhysics.kt` no longer take an `enabled` switch (`liquidDragStretch` keeps one — it is a real gesture condition there). Every ad-hoc shape in `ui/components` and `ui/screens` goes through `VlTheme.tokens.shapes.adapt(shape)` or `.rounded(r)`, which apply the theme's limits (Forge v2's `maxRadius` clamp, or `RectangleShape` on a square scale) and return the shape unchanged otherwise. A bare `RoundedCornerShape` or `CircleShape` there is how the old square Forge shipped with rounded sections, sheets and dialogs. `checkUiRules` counts them as `raw-shape`. `section` and `row` are separate shape roles because Biolume's `card` (16dp) differs from its settings section (24dp).

**Relief needs clearance.** `vlRaised` draws outside the component's bounds, so a raised element wants ≥6–8dp of air. Where there isn't any — `ChatListItem` in compact mode, chat bubbles — use `vlHairline` alone instead; overlapping soft shadows read as dirty bands, which is worse than no relief. That trade-off is why bubbles have no relief at all (they also read `tokens.bubbles` instead of `colorScheme`, since Biolume's `primaryContainer` at alpha .12 is too faint for a bubble).

`tokens.selectionFill` is the opaque fill for a selected pill/chip/segment. It exists because `primaryContainer` in Biolume is `primary` at 12% alpha (§3), which over a container surface reads as *no selection at all*. Reach for it any time "selected" needs to be visible; `primaryContainer` alone is not enough in this theme.

`vlBiopulse` is the only animated glow in the app and belongs solely to live indicators (`VlLiveDot`, `AvatarWithPresence`). It falls back to a static peak-intensity glow when the system animation scale is 0 — `VisorLinkTheme` reads that once into `tokens.reduceMotion` so components don't each query `Settings`. `VlAmbientGlow` is disabled entirely in Biolume (three always-on colored blooms contradict rule 1).

**PRO profile color is the accent of the whole app** (`ui/theme/ProAccent.kt`, a port of the web `profileAccent.js`; `ProAccentTest` holds reference values computed by the JS in node). Color = `customization.accentHex`, else the legacy preset via `ProAccent.LEGACY_HEX` (web hexes, not `ColorPreset.seedColor`), only with active PRO. `ProAccent.readable` lightens (dark) / darkens (light) in 2 % steps until ≥4.5:1 against **every** web surface; text on it is white or `#0B0F12`. It travels as `ViewerTheme.proAccent` → `VisorLinkTheme(proAccent)`. Priorities: a chat/profile accent (`accentOverride` or the owner's preset) wins; in Biolume an explicitly chosen device preset wins too (`withProAccent` + `biolumeProSignal` + 15/20 % `selectionFill` apply only while the preset is `DEFAULT`, whose circle in Settings is then painted with the profile color and captioned «Цвет профиля (PRO)»); in Forge v2 it always applies. The VisorLink logo stays brand cyan.

`ColorPreset` re-tints only the **signal** layer (primary + glow) in Biolume; Abyss/Tidepool surfaces stay canonical, since those surfaces are the theme's identity. `DEFAULT` means the exact guideline colors. In M3E a non-`DEFAULT` preset switches dynamic color off and is applied through `withMaterialAccent`. M3 containers are opaque (`primaryContainer` is the background of my own bubbles, `secondaryContainer` is the M3E `selectionFill`), so they are blended from surface + accent rather than set to the accent. `primary` is pushed to ≥3:1 against the background, because preset BLUE alone gives 2.7:1 there. Before this, the preset only swapped in the static indigo palette and the chosen accent appeared nowhere. Biolume's `withSignalAccent` does the same (≥3:1, towards black on Tidepool / white on Abyss): the accent is also the color of @usernames, links and section titles, and a PRO profile can carry *any* HEX — a light one on Tidepool or a dark one on Abyss made them invisible. Presets that already read are untouched; `ThemeContrastTest` checks arbitrary HEX accents, not only presets.

`withSignalAccent` must recompute `onPrimary`/`onPrimaryContainer` alongside `primary` — the base values are tuned for the canonical accent, and a dark preset over a dark theme otherwise yields unreadable button text. Pick the on-color by **comparing actual WCAG contrast** for black vs white, never by a luminance threshold: `#0EA5E9` (preset BLUE) reads as a light color at luminance 0.33, yet white on it gives 2.8:1 while dark gives 6.8:1.

`ThemeContrastTest` is the guard for all of the above: it checks every `on*`/`*` pair in both palettes, every `ColorPreset` override, and the §10 invariants, at a full 4.5:1. It implements WCAG itself rather than calling `Color.luminance()`, so the formula is part of the assertion. It has already caught three real defects (two under-contrast `on*Container` roles and the preset bug), so treat a failure there as a genuine finding, not as a threshold to relax.

`ThemeViewModel` is a `SharedPreferences` façade over `visorlink_settings` that registers an `OnSharedPreferenceChangeListener` on itself, so every instance across every screen stays in sync. **Setters write prefs, not state** — never assign to the `MutableStateFlow`s directly.

Profile customization is typed: `data/model/ProfileAppearance.kt` is the only place that parses `UserProfile.customization` (keys `theme`, `accent`, `font`, `layout`, `bgUrl`, `gifUrl`, `emojis`) and the only place that decides visibility. `ProfileAppearance.resolve(owner, viewer)` always shows your own customization, and shows someone else's only when they have active PRO and the viewer's `ignoreCustomizations` is off. Use it everywhere, including the chat wallpaper "other" mode, instead of reading the map. `writeTo` preserves unrelated keys (the backend keeps `diaryEnabled` and similar in the same map). `UserProfileTheme(profile, currentUser)` → `ProfileAppearanceTheme(appearance)` applies the result over the viewer's own settings; light/dark always stays the viewer's. It wraps `ChatScreen`, `ProfileScreen` and `OtherProfileScreen`, and the editor's `ProfilePreview`, so the preview is drawn by the same code path other users see. The VM-free overload exists for screenshot tests. The `rounded` font is bundled Nunito; previously it mapped to `SansSerif`, which is Roboto.

**Profile pages.** `ProfileScreen` (own) and `OtherProfileScreen` share `screens/profile/ProfileContent.kt`: `ProfileBackdrop` (the owner's photo under a scrim of the theme's *background* color, 40–85% — a 20% white veil used to leave light-theme text unreadable on dark photos), `ProfileHeader` (avatar / name / @username in compact or banner layout) and `ProfileDetails` (online, admin/PRO badges, bio, bottom ID-card slot). Fix profile visuals there, once; the `profilepage` screenshot scene renders them over a busy "photo" with a light PRO accent in every theme.

**Forge v2** (`ForgeV2Palette.kt`, `AppTheme.FORGE_PROTOGEN` / `FORGE_BEAST`, `selectable = false`) is the special-mode cyberpunk terminal. Palette, shapes and structure are the web's (`variables.css`): dark only, `structure.outline` (1dp `primary` 16% contour) instead of relief, small radii with `VlShapeTokens.maxRadius` clamping every `adapt()`/`rounded()` shape to 8–10dp. On top Android adds a layer of *light, not geometry* the web does not have yet (listed in `ANDROID_ID_CARDS_SPEC.md` §7) — cut corners, neon rims on every panel, background grids and bubble borders were tried and rejected as too busy: titles (display/headline/titleLarge) are **Unbounded** with a soft neon text glow (`forgeV2Typography(f)`), labelLarge is mono; `VlTokens.terminal` drives mono-caps section labels with a rule to the edge (Protogen: `> `), the neon line under `VlTopAppBar` (`vlNeonRule`) and `vlTerminalBackdrop` at the app root (top haze, Protogen scanlines — drawn *over* content at low alpha, because screens paint opaque backgrounds); `signal.buttonRestAlpha` keeps the primary `VlButton` glowing at rest (not counted against §10, never on destructive buttons); the switch is a glowing "tick" bar instead of a round thumb (`ForgeV2Switch` → `VlTokens.switch`; a circle did not fit the 6–8dp track). Beast is the calmer flavor (`Flavor.neon = 0.7`; identify it by `Flavor.beast`, not by reference — tinted copies are new objects). **PRO color:** the profile color **replaces** the Protogen/Beast hue — `VisorLinkTheme(proAccent = …)` → `Flavor.tintedBy(accent)` sets only `primary` (`ProAccent.readable(…, dark)`, kept ≥4.5:1 against `containerHigh`) and `onPrimary`; neon lines, glow, bubbles, selection, containers and haze derive from primary, surfaces/secondary/error/status stay the mode's. `proAccent` is separate from `colorPreset`/`accentOverride` because Forge ignores the device preset. Sources: `MainActivity` (own `UserProfile.proAccent()`, carried in `ViewerTheme.proAccent`), `UserProfileTheme` (the owner's, for their profile/chat and for the owner-mode gamut). It is never read from prefs or profiles (`AppTheme.fromId` only knows selectable themes).

**Adding a theme:** add an `AppTheme` entry with a fresh `id`, build its `ColorScheme` + `VlShapeTokens`, add the branch in `VisorLinkTheme` (the `when` is exhaustive, so the compiler will point at every place needing an arm), and add its name/description strings. The selector picks it up automatically — it iterates `AppTheme.selectableEntries`. No component needs touching.

### UI conventions

Shared composables in `ui/components/` are prefixed `Vl`: `VlSurface`, `VlCard`, `VlButton`, `VlSwitch`, `VlSegmentedControl`, `VlSettingsSection`/`VlSettingsItem`, `VlOptionRow`, `VlDialog`, `VlToast`, `VlAmbientGlow`, `VlGlassPanel`, `VlTextField`, `VlFab`, `VlLiveDot`/`VlPresenceDot`, `VlNavigationBar`, `VlBrandText`, `VlTopAppBar`.

`VlTopAppBar` should be used instead of `TopAppBar` on main screens. Media viewers (`ImageViewerScreen`, `ImageEditorScreen`) are the deliberate exception: their bars are transparent over the image.

Dialogs are `VlAlertDialog` + `VlDialogButton`, never M3 `AlertDialog`/`TextButton`. One overload mirrors the M3 API (`confirmButton` / `dismissButton` / `icon`), so moving a screen over is a rename; use `isDestructive` instead of `colors = …error`. It enforces M3's 280dp minimum width. Category icon tints in settings live in `VlCategoryTint`, not as `Color(0x…)` in the screen.

`VlFab` supports both standard icon-only and extended (icon + text) modes. It also has a `content` slot for custom icon morphs.

`VlNavigationBar` (in `ui/components/VlNavBar.kt`, moved out of `MainScreen`) is a full-width floating stadium bar: the **container** carries the structure (raised + hairline), the **active item** is a *flat* `selectionFill` pill — no inset, no glow. §4.2 lists "чип, вкладка, nav-item" under flat `*Container` fill, so the chip rule from §7 (inset on select) does **not** apply here; a 30dp-tall pill with an inset shadow reads as a smudge. Three constraints this component learned the hard way:

- The indicator is a **sibling** of the icon, never its parent. Scaling a parent scales the icon with it, which silently rendered unselected icons at 70% size.
- It applies `navigationBarsPadding()` itself, because `MainScreen`'s Scaffold zeroes `contentWindowInsets`, so `innerPadding.calculateBottomPadding()` is 0 and the bar would sit in the gesture area.
- Items get `weight(1f)`, not content sizing — with two tabs a content-sized bar collapsed to ~164dp and read as an accident, and uneven label lengths made the items ragged.

Invitations are not a top-bar button but a synthetic row in the chat list (`ui/screens/chatlist/InvitesEntry.kt`, like the `saved_<uid>` «Избранное» row): `ChatListViewModel.listEntries` inserts an `invites_<uid>` `Chat` dated by the newest pending invite, so it sorts among chats by time and sinks to the bottom when there are none; `ChatListItem(isInvites = true, previewOverride = …)` draws it, the unread badge is the invite count, and a tap opens the existing invitations screen (`Screen.Notifications`). Nothing can be written there.

Tab labels come from `nav_tab_*`, not the screen titles: `chatlist_title` is `"VisorLink"`, which as a tab label sat oddly next to "Discover" and "Дневник". (The feed tab is the exception: its label is `feed_title`, «Каналы».)

The theme selector lives in `ui/components/settings/ThemeSelector.kt` (`VlThemeSelector`) and renders each option's preview by wrapping **real components in a real `VisorLinkTheme`** — a preview therefore cannot drift from what the theme actually looks like. `ThemePreviewGrid` is a thin back-compat wrapper over it. It is wired into both `SettingsScreen` and the onboarding appearance step.

`MainScreen` is a tab host (Chats / Каналы (`discoverEnabled`) / Music / Diary, plus Profile = `NAV_TAB_PROFILE` behind `enable_profile_navbar`) whose bottom bar appears only if at least one tab besides Chats is on. The profile tab is `ProfileScreen(asTab = true)`: its header is the ID card itself (`ProfileIdCardHeader` — the avatar and name are on the card), with the avatar header as fallback when there is no card or Mask Mode hides it and while editing (the avatar is changed by tapping it); the owner's `idCardPosition` slots are skipped so the card is not drawn twice, and the chat list hides its avatar chip (`showProfileButton = false`). With the flag off everything is as before: avatar chip → `Screen.Profile`. Five tabs do not fit a 360dp bar, so `calculateTabSlots` squeezes inactive tabs to 40dp and then ellipsizes the active label; the Diary route is intentionally absent from NavGraph to avoid a duplicate PIN prompt. Its nav bar now lives in `ui/components/VlNavBar.kt` — it was theme-dependent drawing inside a screen, which the rule above forbids.

Neumorphic relief draws **outside** a component's bounds, so a Biolume raised element needs ≥6–8dp of clearance; a tight parent with `clip()` will shear the shadow off. Keep that in mind when adding padding-free containers.

### SharedPreferences files

`visorlink_settings` (theme, locale, UI toggles, onboarding version, last 2FA method) · `visorlink_flags_prefs` (device_id, cached claims, "off" overrides) · `visorlink_stealth_prefs` · `visorlink_update_prefs` (install_id, channel) · `visorlink_drafts` · `visorlink_tfa_prefs` (encrypted) · `biometric_prefs` · `fcm_prefs` · `visorlink_mask_prefs` (Mask Mode).

## Conventions

- Code comments and commit messages are in Russian; identifiers and log tags in English. Match that.
- UI strings belong in `values/strings.xml` + `values-ru/strings.xml` (kept in lockstep, one entry per key in both) and are read with `stringResource`. ~32 files still hold hardcoded Russian literals — prefer extracting when you touch them.
- Screenshot tests live in `ui/theme/ThemeScreenshotTest.kt`. They run under a bare `Application` (no Firebase), with Koin started on a stub `FlagsRepository` for components that `koinInject()` it. The Compose clock is paused (`autoAdvance = false`) because `VlLiveDot` and the FAB glow animate forever and would hang `waitForIdle`. Content sits in a `Surface`, not `Box.background`, so `LocalContentColor` is set the way `Scaffold` sets it. When a visual change is intentional, re-record and commit the PNGs together with the code. CI currently runs `compare`, not `verify`: the goldens were recorded on Windows and have not yet been confirmed pixel-identical on Linux.
- `checkUiRules` is a ratchet: it fails only when a file gains violations. After you remove some, run it with `-PupdateUiBaseline` so the lower count is locked in.
- Tests are JUnit4 + `mockito-kotlin` + `kotlinx-coroutines-test`, with backtick method names and `Dispatchers.setMain(testDispatcher)`. `unitTests.isReturnDefaultValues = true`, so Android stubs return defaults instead of throwing. Existing coverage is ViewModel/logic only.
- `Log.d`/`Log.e` calls in hot or noisy paths are wrapped in `if (BuildConfig.DEBUG)` (see `FlagsRepository`).
- `app/google-services.json` is committed intentionally.
- `TODO.md` (repo root) tracks consciously deferred work on the theme system — read it before "finishing" anything theme-related.
- `analyzer.py` is a standalone Tkinter LOC-counting toy, unrelated to the build.
