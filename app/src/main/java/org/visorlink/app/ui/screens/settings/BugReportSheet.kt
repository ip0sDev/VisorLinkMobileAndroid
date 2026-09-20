package org.visorlink.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.visorlink.app.R
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.theme.VlTheme
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportSheet(
    onDismiss: () -> Unit,
    viewModel: BugReportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadScreenshot(context, uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        containerColor = cs.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = cs.error,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = stringResource(R.string.bug_report_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = {
                    viewModel.reset()
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (uiState.successReport != null) {
                // Success screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cs.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.bug_report_success_title, uiState.successReport?.number ?: 0L),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = stringResource(R.string.bug_report_success_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.reset()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            } else {
                // Form
                Text(
                    text = stringResource(R.string.bug_report_field_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                VlTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.onTitleChange(it) },
                    placeholder = stringResource(R.string.bug_report_field_title_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // Category selection
                Text(
                    text = stringResource(R.string.bug_report_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        "ui" to stringResource(R.string.bug_cat_ui),
                        "chat" to stringResource(R.string.bug_cat_chat),
                        "calls" to stringResource(R.string.bug_cat_calls),
                        "media" to stringResource(R.string.bug_cat_media),
                        "auth" to stringResource(R.string.bug_cat_auth),
                        "other" to stringResource(R.string.bug_cat_other)
                    )
                    categories.forEach { (catKey, catLabel) ->
                        val isSelected = uiState.category == catKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onCategoryChange(catKey) },
                            label = { Text(catLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primaryContainer,
                                selectedLabelColor = cs.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Severity selection
                Text(
                    text = stringResource(R.string.bug_report_severity_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val severities = listOf(
                        "low" to stringResource(R.string.bug_sev_low),
                        "medium" to stringResource(R.string.bug_sev_medium),
                        "high" to stringResource(R.string.bug_sev_high),
                        "critical" to stringResource(R.string.bug_sev_critical)
                    )
                    severities.forEach { (sevKey, sevLabel) ->
                        val isSelected = uiState.severity == sevKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onSeverityChange(sevKey) },
                            label = { Text(sevLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (sevKey == "critical") cs.errorContainer else cs.primaryContainer,
                                selectedLabelColor = if (sevKey == "critical") cs.onErrorContainer else cs.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Description
                Text(
                    text = stringResource(R.string.bug_report_field_desc),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                VlTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    placeholder = stringResource(R.string.bug_report_field_desc_hint),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp)
                )

                Spacer(Modifier.height(14.dp))

                // Steps to reproduce
                Text(
                    text = stringResource(R.string.bug_report_field_steps),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                VlTextField(
                    value = uiState.stepsToReproduce,
                    onValueChange = { viewModel.onStepsChange(it) },
                    placeholder = stringResource(R.string.bug_report_field_steps_hint),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp)
                )

                Spacer(Modifier.height(14.dp))

                // Screenshots section
                Text(
                    text = stringResource(R.string.bug_report_screenshots_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiState.screenshots.forEach { attachment ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, cs.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            CachedImage(
                                model = attachment.url,
                                contentDescription = attachment.fileName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { viewModel.removeScreenshot(attachment) },
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Удалить",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (uiState.isUploadingScreenshot) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (uiState.screenshots.size < 5) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = cs.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.bug_report_add_screenshot),
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "+ Фото",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Attach logs toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bug_report_attach_logs),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.bug_report_attach_logs_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    VlSwitch(
                        checked = uiState.attachLogs,
                        onCheckedChange = { viewModel.onAttachLogsChange(it) }
                    )
                }

                if (uiState.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = uiState.error!!,
                        color = cs.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Submit button
                Button(
                    onClick = { viewModel.submit(context) },
                    enabled = !uiState.isSubmitting && !uiState.isUploadingScreenshot,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = cs.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bug_report_submit))
                    }
                }
            }
        }
    }
}
