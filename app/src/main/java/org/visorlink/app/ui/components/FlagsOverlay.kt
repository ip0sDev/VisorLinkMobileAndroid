package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.data.repository.FlagsRepository
import org.koin.compose.koinInject

@Composable
fun FlagsOverlay(
    flagsRepository: FlagsRepository = koinInject()
) {
    val flagsState by flagsRepository.flags.collectAsState()
    var showDetails by remember { mutableStateOf(false) }

    if (flagsState.isEnabled("test_flag")) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp), // Avoiding bottom nav if present
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .clickable { showDetails = true },
                color = Color.Red.copy(alpha = 0.8f),
                shape = VlTheme.tokens.shapes.indicator,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "TEST MODE ENABLED",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp
                )
            }
        }

        if (showDetails) {
            Dialog(onDismissRequest = { showDetails = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f),
                    shape = VlTheme.tokens.shapes.button,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Active Feature Flags",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(flagsState.serverClaims.toList()) { (key, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = key, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Text(
                                        text = value?.toString() ?: "null",
                                        color = if (value == true) VlTheme.tokens.status.success else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                            }
                        }

                        Button(
                            onClick = { showDetails = false },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 16.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
