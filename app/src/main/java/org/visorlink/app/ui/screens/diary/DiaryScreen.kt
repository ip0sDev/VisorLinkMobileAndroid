package org.visorlink.app.ui.screens.diary

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlFab
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.diary.*
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.liquidPillCardSlideOut
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import org.koin.compose.viewmodel.koinViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onNavigateBack: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (String) -> Unit,
    viewModel: DiaryViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val isLiquidEnabled = rememberLiquidEnabled()
    val uiState by viewModel.uiState.collectAsState()
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val context = LocalContext.current

    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)
    val fabJelly = rememberLiquidJellyState(softness = 0.12f, damping = 0.65f, stiffness = 320f)
    val emptyJelly = rememberLiquidJellyState(softness = 0.08f)

    val snackbarHostState = remember { SnackbarHostState() }
    var autoBioTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.showPinInput) {
        if (uiState.showPinInput && !autoBioTriggered && viewModel.hasBiometricPinSaved()) {
            autoBioTriggered = true
            (context as? FragmentActivity)?.let { activity ->
                viewModel.launchBiometricUnlock(activity) { success ->
                    if (!success) autoBioTriggered = false
                }
            }
        } else if (!uiState.showPinInput) { autoBioTriggered = false }
    }

    LaunchedEffect(uiState.error) { 
        uiState.error?.let { 
            snackbarHostState.showSnackbar(it) 
        } 
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfoMessage()
        }
    }

    if (uiState.showPinInput) {
        DiaryPinDialog(
            pinError              = uiState.pinError,
            hasBiometric          = viewModel.hasBiometricPinSaved(),
            keystoreSecurityLevel = viewModel.getKeystoreSecurityLevel(),
            onPinEntered          = { pin, saveBio -> viewModel.onPinEntered(pin, saveBio) },
            onBiometric           = { (context as? FragmentActivity)?.let { viewModel.launchBiometricUnlock(it) } },
            onDismiss             = onNavigateBack,
            clearError            = { viewModel.clearPinError() }
        )
        return
    }

    val exportChooserTitle = stringResource(R.string.diary_export_chooser)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VlTopAppBar(
                modifier = Modifier.liquidJelly(topBarJelly, enabled = isLiquidEnabled),
                title = { Text(stringResource(R.string.diary_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        if (isLiquidEnabled) topBarJelly.press(0.06f)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val exportJelly = rememberLiquidJellyState()
                    IconButton(
                        onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) exportJelly.pulse(0.12f)
                            val xml = viewModel.exportToXml()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, xml)
                                type = "text/xml"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, exportChooserTitle)
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.liquidJelly(exportJelly, enabled = isLiquidEnabled)
                    ) {
                        Icon(Icons.Default.Share, stringResource(R.string.diary_action_export))
                    }
                }
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .padding(bottom = 80.dp)
                    .liquidJelly(fabJelly, enabled = isLiquidEnabled)
            ) {
                VlFab(
                    onClick = {
                        if (isLiquidEnabled) fabJelly.pulse(0.14f)
                        onAddEntry()
                    },
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.diary_action_add),
                    hapticEnabled = hapticEnabled
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            VlAmbientGlow()

            val filteredEntries = remember(uiState.entries, uiState.selectedDate) {
                uiState.entries.filter { 
                    val entryCal = Calendar.getInstance().apply { time = it.createdAt?.toDate() ?: Date() }
                    entryCal.get(Calendar.DAY_OF_YEAR) == uiState.selectedDate.get(Calendar.DAY_OF_YEAR) &&
                    entryCal.get(Calendar.YEAR) == uiState.selectedDate.get(Calendar.YEAR)
                }
            }

            val triggerKey = remember(uiState.selectedDate.timeInMillis, filteredEntries.size) {
                "${uiState.selectedDate.timeInMillis}_${filteredEntries.size}"
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "stats") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidPillCardSlideOut(
                                index = 0,
                                enabled = isLiquidEnabled,
                                triggerKey = triggerKey
                            )
                    ) {
                        DiaryStatsCard(uiState.stats)
                    }
                }
                item(key = "date_selector") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidPillCardSlideOut(
                                index = 1,
                                enabled = isLiquidEnabled,
                                triggerKey = triggerKey
                            )
                    ) {
                        DateSelector(
                            selectedDate = uiState.selectedDate,
                            onDateSelected = { viewModel.onDateSelected(it) }
                        )
                    }
                }

                if (filteredEntries.isEmpty()) {
                    item(key = "empty_state") {
                        LaunchedEffect(uiState.selectedDate) {
                            if (isLiquidEnabled) emptyJelly.pulse(0.12f)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                                .liquidJelly(emptyJelly, enabled = isLiquidEnabled),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.EditNote,
                                    null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isLiquidEnabled) 0.5f else 0.3f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.diary_empty_date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredEntries,
                        key = { _, entry -> entry.id }
                    ) { index, entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidPillCardSlideOut(
                                    index = index + 2,
                                    enabled = isLiquidEnabled,
                                    triggerKey = triggerKey
                                )
                        ) {
                            DiaryEntryCard(
                                entry = entry,
                                onClick = { onEditEntry(entry.id) },
                                onDelete = { viewModel.deleteEntry(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
