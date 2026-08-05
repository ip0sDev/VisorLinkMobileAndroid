package by.iposdev.visorlink.ui.screens.diary

import android.app.DatePickerDialog
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.SavedMessage
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.rememberExthruStyle
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
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
    val appTheme by themeViewModel.appTheme.collectAsState()
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val context = LocalContext.current

    val isExthru = appTheme.isExthruFamily
    val isForge = appTheme.name == "FORGE"

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
        } else if (!uiState.showPinInput) {
            autoBioTriggered = false
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    if (uiState.showPinInput) {
        // Simple reuse of logic, though ideally we'd have a shared PIN component
        DiaryPinDialog(
            pinError = uiState.pinError,
            hasBiometric = viewModel.hasBiometricPinSaved(),
            onPinEntered = { pin, saveBio -> viewModel.onPinEntered(pin, saveBio) },
            onBiometric = { (context as? FragmentActivity)?.let { viewModel.launchBiometricUnlock(it) } },
            onDismiss = onNavigateBack,
            clearError = { viewModel.clearPinError() }
        )
        return
    }

    val exportChooserTitle = stringResource(R.string.diary_export_chooser)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = if (isForge) 64.dp else 80.dp)) {
                VlSurface(
                    appTheme = appTheme,
                    isButton = true,
                    customRadius = if (isForge) 0.dp else 28.dp,
                    overrideColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        onAddEntry()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Add,
                            stringResource(R.string.diary_action_add),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isExthru) VlAmbientGlow(appTheme = appTheme)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Statistics
                item {
                    DiaryStatsCard(uiState.stats, appTheme)
                }

                // Date Selector
                item {
                    DateSelector(
                        selectedDate = uiState.selectedDate,
                        onDateSelected = { viewModel.onDateSelected(it) },
                        appTheme = appTheme
                    )
                }

                // Entries for selected date
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
                        DiaryEntryCard(
                            entry = entry,
                            appTheme = appTheme,
                            onClick = { onEditEntry(entry.id) },
                            onDelete = { viewModel.deleteEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiaryStatsCard(stats: DiaryStats, appTheme: AppTheme) {
    val isForge = appTheme.name == "FORGE"
    VlSurface(
        appTheme = appTheme,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(stringResource(R.string.diary_stats_total), stats.totalEntries.toString(), Icons.Default.LibraryBooks, isForge)
            StatItem(stringResource(R.string.diary_stats_monthly), stats.monthlyEntries.toString(), Icons.Default.CalendarMonth, isForge)
            StatItem(stringResource(R.string.diary_stats_streak), stringResource(R.string.pro_streak, stats.currentStreak), Icons.Default.Whatshot, isForge)
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector, isForge: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = if(isForge) FontFamily.Monospace else null)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = if(isForge) FontFamily.Monospace else null)
    }
}

@Composable
fun DateSelector(selectedDate: Calendar, onDateSelected: (Calendar) -> Unit, appTheme: AppTheme) {
    val context = LocalContext.current
    val dates = remember {
        List(14) { i ->
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
        }.reversed()
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(dates) { date ->
            val isSelected = date.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR) &&
                             date.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)
            
            val dayName = remember(date) { SimpleDateFormat("EEE", Locale.getDefault()).format(date.time) }
            val dayNum = date.get(Calendar.DAY_OF_MONTH).toString()

            VlSurface(
                appTheme = appTheme,
                isButton = true,
                onClick = { onDateSelected(date) },
                overrideColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
                modifier = Modifier.width(60.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(dayName, fontSize = 10.sp, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dayNum, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        
        item {
            IconButton(onClick = {
                val picker = DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val cal = Calendar.getInstance().apply { set(year, month, day) }
                        onDateSelected(cal)
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
                )
                picker.show()
            }) {
                Icon(Icons.Default.CalendarToday, "Pick date", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun DiaryEntryCard(entry: SavedMessage, appTheme: AppTheme, onClick: () -> Unit, onDelete: () -> Unit) {
    val time = remember(entry.createdAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(entry.createdAt?.toDate() ?: Date())
    }
    
    VlSurface(
        appTheme = appTheme,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            MarkdownText(entry.text ?: "")
        }
    }
}

@Composable
fun DiaryPinDialog(
    pinError: Boolean,
    hasBiometric: Boolean,
    onPinEntered: (String, Boolean) -> Unit,
    onBiometric: () -> Unit,
    onDismiss: () -> Unit,
    clearError: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var useBiometrics by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diary_locked)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin, onValueChange = { 
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            pin = it
                            clearError()
                        }
                    },
                    label = { Text(stringResource(R.string.diary_pin_prompt)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pinError,
                    singleLine = true
                )
                if (pinError) {
                    Text(stringResource(R.string.saved_pin_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }

                if (!hasBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { useBiometrics = !useBiometrics }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = useBiometrics, onCheckedChange = { useBiometrics = it })
                        Text(stringResource(R.string.saved_biometric_enable), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPinEntered(pin, useBiometrics) },
                enabled = pin.length in 4..8
            ) { Text(stringResource(R.string.saved_action_unlock)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasBiometric) {
                    IconButton(onClick = onBiometric) { 
                        Icon(Icons.Default.Fingerprint, stringResource(R.string.diary_bio_unlock), tint = MaterialTheme.colorScheme.primary) 
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
