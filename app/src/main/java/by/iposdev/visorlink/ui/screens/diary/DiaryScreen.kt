package by.iposdev.visorlink.ui.screens.diary

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlFab
import by.iposdev.visorlink.ui.components.VlTopAppBar
import by.iposdev.visorlink.ui.components.diary.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
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
    val uiState by viewModel.uiState.collectAsState()
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val context = LocalContext.current

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
                title = { Text(stringResource(R.string.diary_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val xml = viewModel.exportToXml()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, xml)
                            type = "text/xml"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, exportChooserTitle)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, stringResource(R.string.diary_action_export))
                    }
                }
            )
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                VlFab(
                    onClick = onAddEntry,
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.diary_action_add),
                    hapticEnabled = hapticEnabled
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            VlAmbientGlow()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { DiaryStatsCard(uiState.stats) }
                item { DateSelector(selectedDate = uiState.selectedDate, onDateSelected = { viewModel.onDateSelected(it) }) }

                val filteredEntries = uiState.entries.filter { 
                    val entryCal = Calendar.getInstance().apply { time = it.createdAt?.toDate() ?: Date() }
                    entryCal.get(Calendar.DAY_OF_YEAR) == uiState.selectedDate.get(Calendar.DAY_OF_YEAR) &&
                    entryCal.get(Calendar.YEAR) == uiState.selectedDate.get(Calendar.YEAR)
                }

                if (filteredEntries.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.EditNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                                Text(stringResource(R.string.diary_empty_date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        DiaryEntryCard(entry = entry, onClick = { onEditEntry(entry.id) }, onDelete = { viewModel.deleteEntry(entry.id) })
                    }
                }
            }
        }
    }
}
