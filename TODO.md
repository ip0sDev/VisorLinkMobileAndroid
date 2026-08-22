# TODO

Отложенное по системе тем. Каждый пункт — то, что НЕ сделано, с причиной и точкой
входа. Закрытые пункты убраны, а не отмечены галочками: историю держит git.

## Завершение темы Forge

- [x] **Фаза 1: Укрепление фундамента.**
    - [x] Исправить форму Nav-бара для Forge (нужен stadium оверрайд в `VlNavBar.kt`).
    - [x] Добавить тест на `motionSpec` для Forge (линейность 80мс).
    - [x] Исправлены контрасты `on*Container` и разница светлости `primary`/`error` в Forge.
- [x] **Фаза 5: Полировка.**
    - [x] Проверить все нажатия на использование `tokens.motion.motionSpec()`.
    - [x] Аудит сигнального слоя (§10: "Один glow на экран").
    - [x] Проверка производительности теней в Forge (`setShadowLayer`) — требует профилирования на устройстве.
    - [x] Видимость `selectionFill` на обеих палитрах Forge (подтверждена тестами контраста).

Шрифты подключены: **Inter** (весь текст) + **JetBrains Mono** (data-роли) в Biolume,
Space Grotesk — только на вордмарке через `VlBrandText`.

- [ ] **Space Grotesk на заголовках — нельзя без замены гарнитуры.** В шрифте нет
      ни одного кириллического глифа (проверено по cmap: отсутствуют все 66 букв),
      поэтому §5-роли display / headline / titleLarge отданы Inter SemiBold. Если
      нужен именно «парный» заголовочный характер из гайдлайна, нужна гарнитура с
      кириллицей и похожим сложением — например Unbounded, Manrope ExtraBold или
      Cabinet Grotesk. Точка входа: `DisplayFamily` в `ui/theme/Type.kt`.
- [ ] **Размер APK.** Статические инстансы Inter — 4 × ~340KB, суммарно шрифты
      занимают ~2.1MB. Если это станет проблемой, у всех трёх семейств в
      `temp_fonts/` есть variable-версии (Inter 856KB на все веса), но тогда нужен
      `FontVariation.Settings` и проверка, что веса 500/600 реально интерполируются,
      а не молча падают в 400.
- [ ] **`temp_fonts/` можно удалить** — нужные файлы уже скопированы в `res/font`.
      Оставил на случай, если понадобятся другие веса или variable-версии.

## Принятие компонентов экранами

- [x] **`VlTextField` в экранах.** Замена механическая: `OutlinedTextField(label = { Text(x) })` → `VlTextField(label = x)`.
      - [x] `auth/LoginScreen`
      - [x] `auth/RegisterScreen`
      - [x] `auth/TfaScreen`
      - [x] `search/SearchScreen`
      - [x] `profile/ProfileScreen`
      - [x] `group/CreateChatScreen`
      - [x] `group/ChatSettingsScreen`
      - [x] `decoy/DecoyUnlockSheet`
      - [x] `saved/SavedMessagesSettingsScreen`
      - [x] `stickers/StickerPickerBottomSheet`
- [x] **`VlFab` как общий компонент.**
      - [x] `chatlist/ChatListFab.kt`
      - [x] `screens/chat/ChatScreen.kt`
      - [x] `screens/chatlist/ChatListScreen.kt`
      - [x] `screens/diary/DiaryScreen.kt`
      - [x] `screens/feed/FeedScreen.kt`
      - [x] `screens/profile/OtherProfileScreen.kt`
- [x] **Остатки плоских поверхностей (Shape Tokenization).** Замена хардкода `RoundedCornerShape` на `tokens.shapes.*`.
      - [x] `chat/ChatSheets.kt`
      - [x] `chat/MessageActionOverlay.kt`
      - [x] `chat/ChatBubbles.kt`
      - [x] `chat/ChatComponents.kt`
      - [x] `chat/ForwardBanner.kt`
      - [x] `chat/GiftMessage.kt`
      - [x] `chat/CommentsButton.kt`
      - [x] `chat/CdnMediaViewer.kt`
      - [x] `chatlist/ChatListItem.kt`
      - [x] `chatlist/ChatListFab.kt`
      - [x] `feed/FeedComponents.kt`
      - [x] `diary/DiaryComponents.kt`
      - [x] `diary/DiaryMarkdown.kt`
      - [x] `saved/SavedComponents.kt`
      - [x] `settings/ThemeSelector.kt`
      - [x] `screens/chat/ImageEditorScreen.kt`
      - [x] `screens/chat/ForwardPickerDialog.kt`
      - [x] `screens/diary/DiaryEntryScreen.kt`
      - [x] `screens/profile/ProfileScreen.kt`
      - [x] `screens/profile/OtherProfileScreen.kt`
      - [x] `screens/group/CreateChatScreen.kt`
      - [x] `screens/saved/SavedMessagesSettingsScreen.kt`
      - [x] `screens/search/SearchScreen.kt`
      - [x] `screens/settings/SettingsScreen.kt`
      - [x] `screens/settings/StorageManagerScreen.kt`
      - [x] `screens/stickers/StickerPickerBottomSheet.kt`
      - [x] `screens/decoy/DecoyUnlockSheet.kt`
      - [x] `aegis/AegisBody.kt`
      - [x] `aegis/AegisDebugScreen.kt`
      - [x] `components/FlagsOverlay.kt`

## Известные ограничения реализации

- [ ] **`setShadowLayer` — программный путь отрисовки.** Мягкие смещённые тени
      произвольной формы в Compose иначе не получить (`Modifier.shadow` не умеет
      смещение и вторую тень от противоположного источника). После расширения
      рельефа на списки чатов и карточки фида это стоит прогнать профайлером на
      живом устройстве. Если будет джанк — кэшировать тень в `graphicsLayer` или
      перейти на `RenderEffect.createBlurEffect` (API 31+, minSdk 30 → нужен фолбэк).
- [ ] **«Один сигнал за раз» (§1.2, §10) — соглашение, а не гарантия.** Ничто не
      мешает поставить два `vlSignalGlow` на один экран. Проверить статически
      нельзя (glow ставится модификатором в draw-фазе), но можно ввести
      debug-only счётчик: `CompositionLocal` с `AtomicInteger`, компонент с
      включённым сигналом делает `DisposableEffect { claim() / release() }`, и при
      значении > 1 в `BuildConfig.DEBUG` пишется предупреждение в лог.
- [ ] **Полноэкранный просмотр медиа.** В `CdnMediaViewer` добавлен параметр
      `isFullscreen`, но логика переключения и адаптации вёрстки (скрытие UI)
      реализована частично.
- [ ] **Прогресс загрузки для видео и голоса.** Реализован `UploadProgressOverlay`
      для фото, нужно адаптировать/проверить отображение для `VideoBubble` и
      `VoiceBubble` при отправке.
- [ ] **Опечатка в CHANNEL.** В `build.gradle.kts` случайно введено `NIGHTLYт`.
- [ ] **Кольцо биопульса не обрезается формой.** В `vlBiopulse` оно растёт наружу
      (иначе не было бы видно), поэтому на плотной вёрстке может залезать на
      соседей. Сейчас используется только на точках присутствия, где это безопасно.
- [ ] **Компактный список чатов — без рельефа.** При `isCompactList` вертикальных
      отступов между строками нет, тени наложились бы друг на друга грязными
      полосами, поэтому там остаётся только грань. Если хочется рельеф и в
      компактном режиме — нужен разделяющий отступ хотя бы 4dp.

## Тесты

- [ ] **Скриншот-тесты тем.** `ThemeContrastTest` покрывает палитру (контраст всех
      `on*`/`*` пар и всех пресетов акцента по WCAG на 4.5:1, §10-инварианты,
      стабильность `AppTheme.id`), но визуальных регрессий не ловит. Нужен Paparazzi
      или Roborazzi — в проекте сейчас нет ни того, ни другого.
- [ ] **Тест на покрытие глифов.** Ошибка со Space Grotesk была найдена руками.
      Проверку стоит автоматизировать: юнит-тест, который парсит cmap каждого
      шрифта из `res/font` и падает, если гарнитура, назначенная текстовой роли, не
      покрывает кириллицу. Парсер cmap занимает ~40 строк без внешних зависимостей.

## Недавние нововведения и улучшения

- [ ] **Синхронизация инцидентов.** Текущая реализация `StatusViewModel` делает живую диагностику при открытии экрана. Можно добавить периодическую фоновую проверку (WorkManager), если критично уведомлять пользователя о сбоях CDN/Firestore.
- [ ] **История редактирования (UI).** В базе хранится `editHistory`, но в интерфейсе сейчас видна только пометка «(изменено)». Можно добавить диалог просмотра истории изменений по тапу на эту пометку.
- [ ] **Валидация привязки Telegram.** Cloud Function `generateTgCode` возвращает код, но процесс завершения привязки происходит на стороне бота. Стоит добавить слушатель изменений в профиле пользователя, чтобы экран настроек обновлялся мгновенно, когда бот запишет `tg_uid`.
- [ ] **Оптимизация Timeline.** Сейчас 24-часовой график пересчитывается при каждом обновлении списка инцидентов. Для списка из 100+ элементов это может быть накладно, стоит кэшировать результат.
