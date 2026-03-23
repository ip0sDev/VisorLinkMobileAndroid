package by.iposdev.visorlink.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.TagSearchResult
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.screens.chatlist.GroupChannelAvatar
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String? = null,
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String, otherUid: String) -> Unit, // Для юзеров
    onJoinedGroup: (chatId: String) -> Unit,                // Для каналов
    viewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(initialQuery) {
        viewModel.initSearch(initialQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search users or channels") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty())
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus(); viewModel.search()
                }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { focusManager.clearFocus(); viewModel.search() },
                enabled = uiState.query.isNotEmpty() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (uiState.isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                else {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_search))
                }
            }
            Spacer(Modifier.height(24.dp))

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Если найден пользователь
                if (uiState.userResult != null) {
                    item {
                        UserResultCard(
                            user = uiState.userResult!!,
                            onChatClick = {
                                scope.launch {
                                    val chatId = viewModel.openOrCreateChat(uiState.userResult!!)
                                    onOpenChat(chatId, uiState.userResult!!.uid)
                                }
                            }
                        )
                    }
                }

                // Если найден канал/группа
                if (uiState.chatResult != null) {
                    item {
                        GroupResultCard(
                            result = uiState.chatResult!!,
                            isJoining = uiState.isJoining,
                            onJoinClick = {
                                scope.launch {
                                    val chatId = viewModel.joinChatByTag(uiState.chatResult!!.tag)
                                    onJoinedGroup(chatId)
                                }
                            }
                        )
                    }
                }

                // Если ничего не найдено
                if (uiState.notFound && uiState.error == null) {
                    item {
                        if (uiState.query.length > 10) {
                            // Если запрос длинный, предлагаем попробовать как инвайт-токен
                            InviteTokenCard(
                                token = uiState.query,
                                isJoining = uiState.isJoining,
                                onJoinClick = {
                                    scope.launch {
                                        val chatId = viewModel.joinByInviteToken(uiState.query)
                                        onJoinedGroup(chatId)
                                    }
                                }
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.search_not_found),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserResultCard(user: UserProfile, onChatClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarWithPresence(avatarUrl = user.avatarUrl, displayName = user.displayName,
                isOnline = user.online, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (user.bio.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(user.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onChatClick, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.search_action_chat))
            }
        }
    }
}

@Composable
private fun GroupResultCard(result: TagSearchResult, isJoining: Boolean, onJoinClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupChannelAvatar(avatarUrl = result.avatarUrl, name = result.name, isChannel = result.type == "channel", size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("@${result.tag}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (result.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(result.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("${result.memberCount} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onJoinClick,
                enabled = result.joinByTag && !isJoining,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (result.joinByTag) "Join" else "Join disabled")
            }
        }
    }
}

@Composable
private fun InviteTokenCard(token: String, isJoining: Boolean, onJoinClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Link, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Nothing found by tag.", style = MaterialTheme.typography.bodyMedium)
            Text("Try to join using this as an invite token?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onJoinClick,
                enabled = !isJoining,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Join via invite link")
            }
        }
    }
}