package org.visorlink.app.ui.components.chat

import org.visorlink.app.ui.theme.VlTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.visorlink.app.R
import org.visorlink.app.data.repository.ReportCategory
import org.visorlink.app.data.repository.ReportRepository
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlTextField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Жалоба на сообщение, пост канала или пользователя.
 *
 * @param chatId чат сообщения / канал поста: без него сервер не сможет скрыть сообщение после
 *   трёх жалоб. Для жалобы на пользователя — `null`.
 */
@Composable
fun ReportContentDialog(
    targetType: String,
    targetId: String,
    chatId: String?,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit,
    reportRepository: ReportRepository = koinInject()
) {
    var selectedCategory by remember { mutableStateOf(ReportCategory.SPAM) }
    var details by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fallbackError = stringResource(R.string.report_error)

    VlAlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        dismissible = !isSubmitting,
        title = { Text(stringResource(R.string.report_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            .clip(VlTheme.tokens.shapes.rounded(8.dp))
                            .clickable(enabled = !isSubmitting) { selectedCategory = cat }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            enabled = !isSubmitting
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                VlTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    placeholder = stringResource(R.string.report_details_hint),
                    singleLine = false,
                    maxLines = 4,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                submitError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            VlDialogButton(
                onClick = {
                    isSubmitting = true
                    submitError = null
                    scope.launch {
                        val res = reportRepository.submitReport(
                            targetType = targetType,
                            targetId = targetId,
                            chatId = chatId,
                            category = selectedCategory,
                            details = details
                        )
                        isSubmitting = false
                        if (res.isSuccess) onReportSubmitted()
                        else submitError = res.exceptionOrNull()?.message?.takeIf { it.isNotBlank() } ?: fallbackError
                    }
                },
                isPrimary = true,
                isLoading = isSubmitting
            ) {
                Text(stringResource(R.string.report_submit))
            }
        },
        dismissButton = {
            VlDialogButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
