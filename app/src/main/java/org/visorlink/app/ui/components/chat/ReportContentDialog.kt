package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.repository.ReportCategory
import org.visorlink.app.data.repository.ReportRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ReportContentDialog(
    targetType: String,
    targetId: String,
    targetSenderUid: String?,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit,
    reportRepository: ReportRepository = koinInject()
) {
    var selectedCategory by remember { mutableStateOf(ReportCategory.SPAM) }
    var details by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.report_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val categories = listOf(
                    ReportCategory.SPAM to stringResource(R.string.report_cat_spam),
                    ReportCategory.VIOLENCE to stringResource(R.string.report_cat_violence),
                    ReportCategory.HARASSMENT to stringResource(R.string.report_cat_harassment),
                    ReportCategory.ILLEGAL to stringResource(R.string.report_cat_illegal),
                    ReportCategory.COPYRIGHT to stringResource(R.string.report_cat_copyright)
                )

                categories.forEach { (cat, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategory = cat }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(stringResource(R.string.report_details_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )

                if (submitError != null) {
                    Text(
                        text = submitError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        submitError = null
                        scope.launch {
                            val res = reportRepository.submitReport(
                                targetType = targetType,
                                targetId = targetId,
                                targetSenderUid = targetSenderUid,
                                category = selectedCategory,
                                details = details
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                onReportSubmitted()
                            } else {
                                submitError = res.exceptionOrNull()?.message ?: "Ошибка при отправке"
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.report_submit), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
