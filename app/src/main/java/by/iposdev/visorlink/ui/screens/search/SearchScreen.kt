package by.iposdev.visorlink.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String, otherUid: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find User") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.query, onValueChange = viewModel::onQueryChange,
                label = { Text("Search by @username") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty())
                        IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Default.Clear, null) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); viewModel.search() }),
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { focusManager.clearFocus(); viewModel.search() },
                enabled = uiState.query.isNotEmpty() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.large
            ) {
                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search") }
            }
            Spacer(Modifier.height(24.dp))
            when {
                uiState.notFound -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("User not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                uiState.result != null -> {
                    val user = uiState.result!!
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarWithPresence(avatarUrl = user.avatarUrl, displayName = user.displayName,
                                isOnline = user.online, size = 56.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.displayName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("@${user.username}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (user.bio.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(user.bio, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    val chatId = viewModel.openOrCreateChat(user)
                                    onOpenChat(chatId, user.uid)
                                }
                            }, shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Chat")
                            }
                        }
                    }
                }
            }
        }
    }
}