package org.visorlink.app.ui.screens.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.Incident
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlLiveDot
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.liquidPillCardSlideOut
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.theme.VlTheme
import org.koin.compose.koinInject
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

    val flagsRepository: FlagsRepository = koinInject()
    val flags by flagsRepository.flags.collectAsState()
    val isLiquidEnabled = flags.isEnabled("animation_test")
    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        if (isLiquidEnabled) {
            topBarJelly.pulse(0.06f)
        }
    }

    val isAllSystemsUp = uiState.incidents.none { it.isActive }

    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        VlAmbientGlow()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VlTopAppBar(
                    modifier = Modifier.liquidJelly(topBarJelly, enabled = isLiquidEnabled),
                    title = { Text("Статус системы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onSurface) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            onNavigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = cs.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            viewModel.refreshStatus()
                        }, enabled = !uiState.isRefreshing) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.primary)
                            } else {
                                Icon(Icons.Default.Refresh, null, tint = cs.onSurface)
                            }
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "СЕРВИСЫ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = cs.primary,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        if (isAllSystemsUp) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.1f),
                                shape = CircleShape
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ВСЕ СИСТЕМЫ ОК", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                itemsIndexed(uiState.services) { idx, service ->
                    ServiceStatusCard(
                        service = service,
                        modifier = Modifier.liquidPillCardSlideOut(index = idx, enabled = isLiquidEnabled),
                        isLiquidEnabled = isLiquidEnabled
                    )
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "АПТАЙМ (24Ч)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = cs.primary,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    UptimeTimeline(uiState.timeline)
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "ЖУРНАЛ СОБЫТИЙ",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = cs.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (uiState.incidents.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = if (isLiquidEnabled) RoundedCornerShape(32.dp) else tokens.shapes.card,
                            color = cs.surfaceContainerLow.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle, 
                                    null, 
                                    tint = Color(0xFF10B981).copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Все системы работают штатно. Инцидентов не обнаружено.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                itemsIndexed(uiState.incidents, key = { _, it -> it.id }) { idx, incident ->
                    IncidentCard(
                        incident = incident,
                        modifier = Modifier.liquidPillCardSlideOut(index = uiState.services.size + idx, enabled = isLiquidEnabled),
                        isLiquidEnabled = isLiquidEnabled
                    )
                }
                
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
fun UptimeTimeline(timeline: List<TimelineBar>) {
    val cs = MaterialTheme.colorScheme
    val green = Color(0xFF27AE60)
    val red = Color(0xFFE74C3C)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            timeline.forEach { bar ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (bar.isUp) green.copy(alpha = 0.8f) else red)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("24 часа назад", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text("Сейчас", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun ServiceStatusCard(
    service: ServiceStatus,
    modifier: Modifier = Modifier,
    isLiquidEnabled: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    
    val statusColor = if (service.isUp) Color(0xFF10B981) else Color(0xFFEF4444)
    val statusIcon = if (service.isUp) Icons.Default.CheckCircle else Icons.Default.Error
    val cardShape = if (isLiquidEnabled) RoundedCornerShape(32.dp) else null

    VlCard(modifier = modifier.fillMaxWidth(), shape = cardShape) {
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
                Text(service.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
                if (service.isUp) {
                    Text("Работает", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                } else {
                    Text(service.error ?: "Ошибка подключения", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444))
                }
            }
            
            if (!service.isUp) {
                VlLiveDot(color = Color.Red)
            }
        }
    }
}

@Composable
fun IncidentCard(
    incident: Incident,
    modifier: Modifier = Modifier,
    isLiquidEnabled: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val green = Color(0xFF27AE60)
    val red = Color(0xFFE74C3C)
    val cardShape = if (isLiquidEnabled) RoundedCornerShape(32.dp) else null

    VlCard(modifier = modifier.fillMaxWidth(), shape = cardShape) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = (if (incident.resolved) green else red).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (incident.resolved) "РЕШЕНО" else "АКТИВНО",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (incident.resolved) green else red,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.width(8.dp))
                
                Text(
                    incident.service.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(Modifier.weight(1f))
                
                val dateStr = remember(incident.timestamp) {
                    val df = SimpleDateFormat("dd MMM, HH:mm", Locale("ru"))
                    df.format(Date(incident.timestamp))
                }
                
                Text(
                    dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            if (incident.resolved) {
                Text("✅ Работа сервиса полностью восстановлена.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = cs.onSurface)
                if (incident.resolvedAt != null) {
                    val resolvedStr = remember(incident.resolvedAt) {
                        val df = SimpleDateFormat("HH:mm", Locale("ru"))
                        df.format(Date(incident.resolvedAt))
                    }
                    Text(
                        "Восстановлено в $resolvedStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else {
                Text(
                    "Наблюдается повышенный процент ошибок в сервисе ${incident.service}, некоторые функции могут не работать",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }
            
            // Техническая информация (errorTelemetry) скрыта от пользователя согласно требованиям.
            // if (incident.errorTelemetry.isNotEmpty()) { ... }

            if (incident.isLocal) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = cs.errorContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, red.copy(alpha = 0.3f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "⚠️ Системный сбой связи: Ваш телефон обнаружил проблему, но не смог связаться с сервером отчётов. Сделайте скриншот и отправьте в поддержку.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface
                        )
                    }
                }
            }
        }
    }
}
