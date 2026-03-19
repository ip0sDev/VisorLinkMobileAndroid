package by.iposdev.visorlink.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.repository.ChatRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChatScreen(
    onNavigateBack: () -> Unit,
    onCreated: (chatId: String) -> Unit,
    chatRepository: ChatRepository = koinInject()
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("group") }
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val titleRes = if (type == "group") R.string.create_title_group else R.string.create_title_channel

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = if (type == "group") 0 else 1) {
                Tab(selected = type == "group", onClick = { type = "group" },
                    text = { Text(stringResource(R.string.create_tab_group)) })
                Tab(selected = type == "channel", onClick = { type = "channel" },
                    text = { Text(stringResource(R.string.create_tab_channel)) })
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.create_field_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = tag,
                onValueChange = {
                    tag = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase()
                },
                label = { Text(stringResource(R.string.create_field_tag)) },
                prefix = { Text("@") },
                supportingText = { Text(stringResource(R.string.create_tag_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = description, onValueChange = { description = it.take(160) },
                label = { Text(stringResource(R.string.create_field_description)) },
                supportingText = { Text("${description.length}/160") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            if (type == "channel") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        stringResource(R.string.create_channel_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            val createLabel = stringResource(
                if (type == "group") R.string.create_button_group else R.string.create_button_channel
            )
            val errorDefault = stringResource(R.string.create_error_default)

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true; error = null
                        try {
                            val (chatId, _) = chatRepository.createChat(type, name, tag, description)
                            onCreated(chatId)
                        } catch (e: Exception) {
                            error = e.message ?: errorDefault
                        } finally { isLoading = false }
                    }
                },
                enabled = name.isNotBlank() && tag.length >= 3 && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                else Text(createLabel)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}