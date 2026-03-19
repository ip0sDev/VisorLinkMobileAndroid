package by.iposdev.visorlink.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import by.iposdev.visorlink.R
// Для работы со строками
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    uid: String,
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String, otherUid: String) -> Unit
) {
    val viewModel: OtherProfileViewModel = koinViewModel(parameters = { parametersOf(uid) })
    val user by viewModel.user.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.displayName ?: stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch {
                        val chatId = viewModel.openOrCreateChat()
                        onOpenChat(chatId, viewModel.targetUid)
                    }
                },
                icon = { Icon(Icons.Default.Chat, null) },
                text = { Text(stringResource(R.string.other_profile_message)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (user == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val u = user!!
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(96.dp).clip(CircleShape)) {
                if (!u.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(model = u.avatarUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(u.displayName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(u.displayName,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${u.username}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (u.online) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall) {
                        Text(stringResource(R.string.other_profile_online),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            if (u.bio.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(u.bio, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    }
}