package org.visorlink.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.ProfileFont
import org.visorlink.app.data.model.ProfileLayout
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.components.settings.displayName
import org.visorlink.app.ui.theme.ProfileAppearanceTheme

/**
 * Редактор оформления профиля (PRO; вход закрыт проверкой в SettingsScreen).
 *
 * Превью рисуется внутри [ProfileAppearanceTheme] — той же обёртки, через которую
 * оформление видят другие, поэтому превью не может разойтись с результатом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    onNavigateBack: () -> Unit,
    viewModel: CustomizationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val errorPrefix = stringResource(R.string.custom_save_error)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar("$errorPrefix: $it")
            viewModel.clearError()
        }
    }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.upload(it, AppearanceUpload.BACKGROUND) }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.upload(it, AppearanceUpload.BANNER) }
    }

    Scaffold(
        topBar = {
            VlTopAppBar(
                title = { Text(stringResource(R.string.settings_custom_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.custom_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val profile = uiState.profile
        if (profile == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        CustomizationContent(
            profile = profile,
            appearance = uiState.appearance,
            uploading = uiState.uploading,
            onChange = viewModel::update,
            onPickBackground = { bgPicker.launch("image/*") },
            onPickBanner = { bannerPicker.launch("image/gif") },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun CustomizationContent(
    profile: UserProfile,
    appearance: ProfileAppearance,
    uploading: AppearanceUpload?,
    onChange: ((ProfileAppearance) -> ProfileAppearance) -> Unit,
    onPickBackground: () -> Unit,
    onPickBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        VlSettingsSection(title = stringResource(R.string.custom_section_preview)) {
            ProfileAppearanceTheme(appearance = appearance) {
                ProfilePreview(
                    profile = profile,
                    appearance = appearance,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                stringResource(R.string.custom_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }

        // Тема: null — «как у зрителя», поэтому отдельная первая строка
        VlSettingsSection(title = stringResource(R.string.custom_section_theme)) {
            val options: List<AppTheme?> = listOf(null) + AppTheme.entries
            options.forEachIndexed { i, theme ->
                VlOptionRow(
                    icon = if (theme == null) Icons.Default.PersonOutline else Icons.Default.Palette,
                    label = theme?.displayName() ?: stringResource(R.string.custom_option_viewer),
                    desc = if (theme == null) stringResource(R.string.custom_theme_viewer_desc) else null,
                    selected = appearance.theme == theme,
                    onClick = { onChange { it.copy(theme = theme) } },
                    index = i,
                    total = options.size,
                )
            }
        }

        VlSettingsSection(title = stringResource(R.string.custom_section_accent)) {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            var showPicker by remember { mutableStateOf(false) }
            // Свой цвет: HEX задан и не совпадает с цветом выбранного пресета
            val isCustom = appearance.accentHex != null &&
                !appearance.accentHex.equals(ProfileAppearance.hexOf(appearance.accent), ignoreCase = true)
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorPreset.entries.forEach { preset ->
                    ColorPresetCircle(
                        preset = preset,
                        // DEFAULT в модели хранится как null — «акцент зрителя»
                        isSelected = !isCustom && (appearance.accent ?: ColorPreset.DEFAULT) == preset,
                        isDark = isDark,
                        onClick = {
                            onChange {
                                val chosen = preset.takeIf { p -> p != ColorPreset.DEFAULT }
                                // accentHex пишем вместе с пресетом, иначе веб покажет старый цвет; «по умолчанию» — удаляем
                                it.copy(accent = chosen, accentHex = ProfileAppearance.hexOf(chosen))
                            }
                        },
                    )
                }
                CustomColorCircle(
                    color = if (isCustom) appearance.accentHexColor else null,
                    isSelected = isCustom,
                    onClick = { showPicker = true },
                )
            }
            if (showPicker) {
                VlColorPickerDialog(
                    initial = appearance.accentHexColor
                        ?: appearance.accent?.seedColor
                        ?: MaterialTheme.colorScheme.primary,
                    onDismiss = { showPicker = false },
                    onConfirm = { hex ->
                        showPicker = false
                        onChange { it.copy(accentHex = hex) }
                    },
                )
            }
        }

        VlSettingsSection(title = stringResource(R.string.custom_section_font)) {
            val fonts: List<ProfileFont?> = listOf(null) + ProfileFont.entries
            fonts.forEachIndexed { i, font ->
                VlOptionRow(
                    icon = Icons.Default.TextFields,
                    label = fontName(font),
                    selected = appearance.font == font,
                    onClick = { onChange { it.copy(font = font) } },
                    index = i,
                    total = fonts.size,
                )
            }
        }

        VlSettingsSection(title = stringResource(R.string.custom_section_layout)) {
            VlSegmentedControl(
                labels = listOf(
                    stringResource(R.string.custom_layout_default),
                    stringResource(R.string.custom_layout_compact),
                ),
                selectedIndex = ProfileLayout.entries.indexOf(appearance.layout),
                onSelected = { i -> onChange { it.copy(layout = ProfileLayout.entries[i]) } },
                modifier = Modifier.padding(16.dp),
            )
        }

        VlSettingsSection(title = stringResource(R.string.custom_section_media)) {
            MediaItem(
                icon = Icons.Default.Image,
                title = stringResource(R.string.custom_media_background),
                isSet = appearance.backgroundUrl != null,
                isUploading = uploading == AppearanceUpload.BACKGROUND,
                enabled = uploading == null,
                onPick = onPickBackground,
                onRemove = { onChange { it.copy(backgroundUrl = null) } },
                index = 0,
            )
            MediaItem(
                icon = Icons.Default.Gif,
                title = stringResource(R.string.custom_media_banner),
                isSet = appearance.bannerUrl != null,
                isUploading = uploading == AppearanceUpload.BANNER,
                enabled = uploading == null,
                onPick = onPickBanner,
                onRemove = { onChange { it.copy(bannerUrl = null) } },
                index = 1,
            )
        }
    }
}

@Composable
private fun MediaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSet: Boolean,
    isUploading: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    index: Int,
) {
    VlSettingsItem(
        icon = icon,
        title = title,
        subtitle = stringResource(
            when {
                isUploading -> R.string.custom_media_uploading
                isSet -> R.string.custom_media_set
                else -> R.string.custom_media_none
            }
        ),
        onClick = if (enabled) onPick else null,
        index = index,
        trailing = {
            when {
                isUploading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                isSet -> IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Default.Close, stringResource(R.string.custom_media_remove))
                }
            }
        },
    )
}

@Composable
private fun fontName(font: ProfileFont?): String = stringResource(
    when (font) {
        null -> R.string.custom_option_viewer
        ProfileFont.MONO -> R.string.custom_font_mono
        ProfileFont.SERIF -> R.string.custom_font_serif
        ProfileFont.ROUNDED -> R.string.custom_font_rounded
    }
)

/**
 * Мини-карточка профиля. Рисуется внутри [ProfileAppearanceTheme], поэтому
 * тема, акцент и шрифт берутся из MaterialTheme, а фон и баннер — из [appearance].
 */
@Composable
fun ProfilePreview(
    profile: UserProfile,
    appearance: ProfileAppearance,
    modifier: Modifier = Modifier,
) {
    val compact = appearance.layout == ProfileLayout.COMPACT
    VlSurface(modifier = modifier.fillMaxWidth()) {
        Box {
            appearance.backgroundUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                appearance.bannerUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentScale = ContentScale.Crop,
                    )
                }
                val avatar = @Composable {
                    AvatarWithPresence(
                        avatarUrl = profile.avatarUrl,
                        displayName = profile.displayName,
                        isOnline = true,
                        size = if (compact) 44.dp else 56.dp,
                    )
                }
                val names = @Composable { align: Alignment.Horizontal ->
                    Column(horizontalAlignment = align) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.displayName.ifBlank { profile.username },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            appearance.emojis?.let { Text(" $it", style = MaterialTheme.typography.titleMedium) }
                        }
                        Text(
                            "@${profile.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (compact) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        avatar()
                        names(Alignment.Start)
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        avatar()
                        names(Alignment.CenterHorizontally)
                    }
                }
                // Кнопка показывает акцент — главное, что меняет пресет
                VlButton(
                    onClick = {},
                    hapticEnabled = false,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(),
                ) { Text(stringResource(R.string.custom_preview_button)) }
            }
        }
    }
}
