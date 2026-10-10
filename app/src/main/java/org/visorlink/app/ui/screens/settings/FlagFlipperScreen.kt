package org.visorlink.app.ui.screens.settings

import org.visorlink.app.R
import org.visorlink.app.ui.components.VlSettingsItem
import org.visorlink.app.ui.components.VlSettingsSection
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.VlTopAppBar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagFlipperScreen(
    onBack: () -> Unit,
    viewModel: FlagFlipperViewModel = koinViewModel()
) {
    val flagsState by viewModel.flags.collectAsState()

    // Flipper открывает только test_flag с сервера: сняли флаг — экран закрывается
    if (!flagsState.testFlag && !org.visorlink.app.BuildConfig.DEBUG) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val keys = flagsState.flippableKeys

    Scaffold(
        topBar = {
            VlTopAppBar(
                title = { Text(stringResource(R.string.flag_flipper_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (keys.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.flag_flipper_empty),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    val sectionTitle = if (org.visorlink.app.BuildConfig.DEBUG) {
                        stringResource(R.string.flag_flipper_section_debug)
                    } else {
                        stringResource(R.string.flag_flipper_section)
                    }
                    VlSettingsSection(title = sectionTitle) {
                        keys.forEachIndexed { index, key ->
                            val enabled = flagsState.isEnabled(key)
                            val serverOn = flagsState.serverValue(key)
                            val override = flagsState.overrideOf(key)
                            val subtitleRes = when {
                                serverOn && override == false -> R.string.flag_flipper_off_locally
                                serverOn -> R.string.flag_flipper_on_server
                                override == true -> R.string.flag_flipper_on_locally
                                else -> R.string.flag_flipper_off_server
                            }
                            VlSettingsItem(
                                icon = if (enabled) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                title = key,
                                subtitle = stringResource(subtitleRes),
                                index = index,
                                total = keys.size,
                                onClick = { viewModel.toggleFlag(key, !enabled) },
                                trailing = {
                                    VlSwitch(
                                        checked = enabled,
                                        onCheckedChange = { viewModel.toggleFlag(key, it) }
                                    )
                                }
                            )
                        }
                    }
                    val footerText = if (org.visorlink.app.BuildConfig.DEBUG) {
                        stringResource(R.string.flag_flipper_footer_debug)
                    } else {
                        stringResource(R.string.flag_flipper_footer)
                    }
                    Text(
                        text = footerText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
