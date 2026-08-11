package by.iposdev.visorlink.ui.aegis

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.aegis.LinkResponse
import by.iposdev.visorlink.data.model.aegis.SimulatedContext
import by.iposdev.visorlink.data.model.aegis.VisorIcon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisDebugScreen(
    viewModel: LinkDebugViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAegisDebugMode by viewModel.isAegisDebugMode.collectAsState()

    if (!isAegisDebugMode) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Access Denied: Aegis Debug Mode is disabled")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aegis Project Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Block 1: Context Simulator
            item {
                ContextSimulator(
                    context = uiState.simulatedContext,
                    onContextChange = viewModel::updateContext
                )
            }

            // Block 2: Input Simulator
            item {
                val currentEngineName by viewModel.currentEngineName.collectAsState()
                var useLlm by remember { mutableStateOf(false) }

                InputSimulator(
                    onAnalyze = viewModel::analyzeText,
                    onIdle = viewModel::simulateIdle,
                    onReset = viewModel::resetState,
                    onSafeModeToggle = viewModel::toggleSafeMode,
                    onEngineToggle = {
                        useLlm = it
                        viewModel.setEngine(it)
                    },
                    useLlm = useLlm,
                    currentEngineName = currentEngineName,
                    isSafeMode = uiState.isSafeMode
                )
            }

            // Block 3: Link UI Mockup
            item {
                LinkUiMockup(
                    lastResponse = uiState.lastResponse,
                    isProcessing = uiState.isProcessing
                )
            }

            // Block 4: JSON Inspector
            item {
                JsonInspector(uiState.lastResponse)
            }
        }
    }
}

@Composable
fun ContextSimulator(
    context: SimulatedContext,
    onContextChange: (SimulatedContext) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("1. Context Simulator", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = context.sysTime,
                onValueChange = { onContextChange(context.copy(sysTime = it)) },
                label = { Text("System Time (HH:mm)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text("Battery Level: ${context.batteryLevel}%")
            Slider(
                value = context.batteryLevel.toFloat(),
                onValueChange = { onContextChange(context.copy(batteryLevel = it.toInt())) },
                valueRange = 0f..100f
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark Mode")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = context.isDarkMode,
                    onCheckedChange = { onContextChange(context.copy(isDarkMode = it)) }
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Charging")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = context.isCharging,
                    onCheckedChange = { onContextChange(context.copy(isCharging = it)) }
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text("Day of Week: ${getDayName(context.dayOfWeek)}")
            Slider(
                value = context.dayOfWeek.toFloat(),
                onValueChange = { onContextChange(context.copy(dayOfWeek = it.toInt())) },
                valueRange = 1f..7f,
                steps = 5
            )
        }
    }
}

private fun getDayName(day: Int): String = when(day) {
    1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"
    else -> "???"
}

@Composable
fun InputSimulator(
    onAnalyze: (String) -> Unit,
    onIdle: () -> Unit,
    onReset: () -> Unit,
    onSafeModeToggle: (Boolean) -> Unit,
    onEngineToggle: (Boolean) -> Unit,
    useLlm: Boolean,
    currentEngineName: String,
    isSafeMode: Boolean
) {
    var inputText by remember { mutableStateOf("") }

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("2. Input Simulator ($currentEngineName)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use LLM Engine")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = useLlm,
                    onCheckedChange = onEngineToggle
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Diary Entry Text") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = { onAnalyze(inputText) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyze Text")
            }
            
            Spacer(Modifier.height(8.dp))
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = onIdle,
                    label = { Text("Idle 40s") },
                    leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = onReset,
                    label = { Text("Reset Cache") },
                    leadingIcon = { Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = isSafeMode,
                    onClick = { onSafeModeToggle(!isSafeMode) },
                    label = { Text("Safe Mode") }
                )
            }
        }
    }
}

@Composable
fun LinkUiMockup(
    lastResponse: LinkResponse?,
    isProcessing: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("3. Link UI Mockup (Grey Box)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Character Stub
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = lastResponse?.visorIcon?.toVisual() ?: "(-_-)",
                                color = Color.Cyan,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lastResponse?.action?.name ?: "IDLE",
                                color = Color.LightGray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                // Message Bubble
                AnimatedVisibility(
                    visible = (lastResponse != null && !isProcessing),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = lastResponse?.message ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun JsonInspector(response: LinkResponse?) {
    Card {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("4. JSON Inspector", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = response?.let { 
                        """
                        {
                          "emotion": "${it.emotion}",
                          "action": "${it.action}",
                          "visor": "${it.visorIcon}",
                          "reply": ${it.requiresUserReply},
                          "msg": "${it.message}",
                          "exec": ${it.executionTimeMs}ms
                        }
                        """.trimIndent()
                    } ?: "No data analyzed yet",
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun VisorIcon.toVisual(): String = when(this) {
    VisorIcon.HEART -> "<3"
    VisorIcon.EXCLAMATION -> "!!"
    VisorIcon.DOTS -> "..."
    VisorIcon.CROSS -> "XX"
    VisorIcon.CHECKMARK -> "OK"
    VisorIcon.ZZZ -> "ZZZ"
    VisorIcon.QUESTION -> "??"
    VisorIcon.SMILE -> "^_^"
}
