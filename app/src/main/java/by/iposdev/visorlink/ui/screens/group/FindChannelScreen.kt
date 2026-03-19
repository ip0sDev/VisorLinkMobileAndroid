package by.iposdev.visorlink.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import by.iposdev.visorlink.data.model.TagSearchResult
import by.iposdev.visorlink.data.repository.ChatRepository
import kotlinx.coroutines.launch
import by.iposdev.visorlink.R
import org.koin.compose.koinInject
// Для работы со строками
import androidx.compose.ui.res.stringResource

// Для Modifier и dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Для иконок (Icons)
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindChannelScreen(
    onNavigateBack: () -> Unit,
    onJoined: (chatId: String) -> Unit,
    chatRepository: ChatRepository = koinInject()
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<TagSearchResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val notFoundFmt  = stringResource(R.string.find_not_found, "")   // prefix only; we append tag below
    val errorDefault = stringResource(R.string.find_error_default)

    fun search() {
        if (input.isBlank()) return
        focusManager.clearFocus()
        scope.launch {
            isLoading = true; error = null; result = null
            try {
                val trimmed = input.trim().removePrefix("@")
                if (trimmed.length > 10 && !input.startsWith("@")) {
                    val (chatId, _) = chatRepository.joinByInvite(trimmed)
                    onJoined(chatId)
                } else {
                    val r = chatRepository.findByTag(trimmed)
                    if (!r.found) error = notFoundFmt + trimmed
                    else result = r
                }
            } catch (e: Exception) {
                error = e.message ?: errorDefault
            } finally { isLoading = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.find_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null; result = null },
                label = { Text(stringResource(R.string.find_field_hint)) },
                leadingIcon = { Icon(Icons.Default.Tag, null) },
                trailingIcon = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = { input = "" }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { search() },
                enabled = input.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                else {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.find_button))
                }
            }

            Spacer(Modifier.height(20.dp))

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }

            result?.let { r ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.type == "channel") "📢" else "👥",
                                style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("@${r.tag}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (r.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(r.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.find_member_count, r.memberCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isJoining = true
                                    try {
                                        val chatId = chatRepository.joinByTag(r.tag)
                                        onJoined(chatId)
                                    } catch (e: Exception) {
                                        error = e.message
                                    } finally { isJoining = false }
                                }
                            },
                            enabled = r.joinByTag && !isJoining,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (isJoining)
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            else Text(
                                if (r.joinByTag) stringResource(R.string.find_join)
                                else stringResource(R.string.find_join_disabled)
                            )
                        }
                    }
                }
            }
        }
    }
}