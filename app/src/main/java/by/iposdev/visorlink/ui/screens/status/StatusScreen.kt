package by.iposdev.visorlink.ui.screens.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.Incident
import by.iposdev.visorlink.ui.components.VlCard
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlLiveDot
import by.iposdev.visorlink.ui.theme.VlTheme
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatusViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text("Статус системы", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatus() }, enabled = !uiState.isRefreshing) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            VlAmbientGlow()
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "СЕРВИСЫ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                    )
                }

                items(uiState.services) { service ->
                    ServiceStatusCard(service)
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ИСТОРИЯ ИНЦИДЕНТОВ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                    )
                }

                if (uiState.incidents.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = tokens.shapes.card,
                            color = cs.surfaceContainerLow,
                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Text(
                                "Все системы работают в штатном режиме. Инцидентов не зафиксировано.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                items(uiState.incidents) { incident ->
                    IncidentCard(incident)
                }
            }
        }
    }
}

@Composable
fun ServiceStatusCard(service: ServiceStatus) {
    val cs = MaterialTheme.colorScheme
    
    val statusColor = if (service.isUp) Color(0xFF10B981) else Color(0xFFEF4444)
    val statusIcon = if (service.isUp) Icons.Default.CheckCircle else Icons.Default.Error

    VlCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(statusColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(24.dp))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(service.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (service.isUp) {
                    Text("Работает", style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
                } else {
                    Text(service.error ?: "Ошибка подключения", style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                }
            }
            
            if (!service.isUp) {
                VlLiveDot(color = Color.Red)
            }
        }
    }
}

@Composable
fun IncidentCard(incident: Incident) {
    val cs = MaterialTheme.colorScheme
    
    val severityColor = when (incident.severity) {
        "critical" -> Color(0xFFEF4444)
        "major" -> Color(0xFFF59E0B)
        else -> Color(0xFF6B7280)
    }

    VlCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = severityColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        incident.severity.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.width(8.dp))
                
                Text(
                    incident.service,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.weight(1f))
                
                val dateStr = remember(incident.timestamp) {
                    val df = SimpleDateFormat("dd MMM, HH:mm", Locale("ru"))
                    incident.timestamp?.toDate()?.let { df.format(it) } ?: ""
                }
                
                Text(
                    dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(incident.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            
            if (incident.description.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    incident.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            
            if (incident.resolved) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Решено",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
