package by.iposdev.visorlink.ui.screens.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.ui.components.VlTextField
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class ChatSettingsUiState(
    val chat: Chat? = null,
    val chatType: ChatType = ChatType.GROUP,
    val myMember: Member? = null,
    val members: List<Member> = emptyList(),
    val memberProfiles: Map<String, by.iposdev.visorlink.data.model.UserProfile> = emptyMap(),
    // Edit state
    val isEditingInfo: Boolean = false,
    val editName: String = "",
    val editDescription: String = "",
    // Settings toggles (local copy from chat)
    val joinByLink: Boolean = true,
    val joinByTag: Boolean = false,
    val allowReactions: Boolean = true,
    val inviteLink: String = "",
    // UI
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: Int? = null, // Изменили String на Int (ID ресурса)
    val selectedTab: Int = 0  // 0=Info, 1=Members, 2=Moderation
) {
    val isAdmin get() = myMember?.isAdmin() ?: false
    val isOwner get() = myMember?.isOwner() ?: false
    val bannedMembers get() = members.filter { it.banned }
    val mutedMembers get() = members.filter { it.muted && !it.banned }
    val restrictedMembers get() = members.filter { it.mediaRestricted && !it.banned && !it.muted }
    val sortedMembers get() = members.sortedWith(
        compareBy {
            when (it.role) {
                "owner" -> 0; "admin" -> 1; else -> 2
            }
        }
    )
}

class ChatSettingsViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: by.iposdev.visorlink.data.repository.UserRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    val chatId: String
) : ViewModel() {

    val currentUid: String get() = auth.currentUser!!.uid

    private val _uiState = MutableStateFlow(ChatSettingsUiState())
    val uiState: StateFlow<ChatSettingsUiState> = _uiState.asStateFlow()

    init {
        // Listen to chat document
        viewModelScope.launch {
            db.collection("chats").document(chatId)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    val chat = try { snap.toObject(Chat::class.java)?.copy(id = chatId) }
                    catch (_: Exception) { null } ?: return@addSnapshotListener
                    val settings = chat.settings
                    _uiState.update {
                        it.copy(
                            chat = chat,
                            chatType = chat.chatType(),
                            editName = if (!it.isEditingInfo) chat.name else it.editName,
                            editDescription = if (!it.isEditingInfo) chat.description else it.editDescription,
                            joinByLink = settings.joinByLink,
                            joinByTag = settings.joinByTag,
                            allowReactions = settings.allowReactions,
                            inviteLink = settings.inviteLink
                        )
                    }
                }
        }

        // Listen to members
        viewModelScope.launch {
            chatRepository.membersFlow(chatId).collect { members ->
                _uiState.update { it.copy(members = members) }
                val mine = members.find { it.uid == currentUid }
                if (mine != null) _uiState.update { s -> s.copy(myMember = mine) }

                // Load profiles for all members
                members.forEach { member ->
                    if (member.uid !in _uiState.value.memberProfiles) {
                        viewModelScope.launch {
                            val profile = try { userRepository.getUserProfile(member.uid) }
                            catch (_: Exception) { null }
                            if (profile != null) {
                                _uiState.update { s ->
                                    s.copy(memberProfiles = s.memberProfiles + (member.uid to profile))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectTab(index: Int) = _uiState.update { it.copy(selectedTab = index) }

    fun startEditingInfo() = _uiState.update {
        it.copy(
            isEditingInfo = true,
            editName = it.chat?.name ?: "",
            editDescription = it.chat?.description ?: ""
        )
    }

    fun cancelEditingInfo() = _uiState.update { it.copy(isEditingInfo = false) }
    fun onNameChange(v: String) = _uiState.update { it.copy(editName = v) }
    fun onDescChange(v: String) = _uiState.update { it.copy(editDescription = v.take(160)) }

    fun saveInfo() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                chatRepository.updateChatSettings(chatId, mapOf(
                    "name" to state.editName,
                    "description" to state.editDescription
                ))
                _uiState.update { it.copy(isLoading = false, isEditingInfo = false, successMessage = R.string.settings_info_saved) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleJoinByLink(enabled: Boolean) {
        viewModelScope.launch {
            try { chatRepository.updateChatSettings(chatId, mapOf("settings.joinByLink" to enabled)) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleJoinByTag(enabled: Boolean) {
        viewModelScope.launch {
            try { chatRepository.updateChatSettings(chatId, mapOf("settings.joinByTag" to enabled)) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleAllowReactions(enabled: Boolean) {
        viewModelScope.launch {
            try { chatRepository.updateChatSettings(chatId, mapOf("settings.allowReactions" to enabled)) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun regenerateInviteLink() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val newLink = chatRepository.regenerateInviteLink(chatId)
                _uiState.update { it.copy(isLoading = false, inviteLink = newLink, successMessage = R.string.settings_info_saved) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun moderateUser(targetUid: String, action: String, durationMinutes: Int? = null) {
        viewModelScope.launch {
            try { chatRepository.moderateUser(chatId, targetUid, action, durationMinutes) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun setMemberRole(targetUid: String, role: String) {
        viewModelScope.launch {
            try { chatRepository.setMemberRole(chatId, targetUid, role) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun inviteUser(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                chatRepository.inviteUser(chatId, username)
                _uiState.update { it.copy(isLoading = false, successMessage = R.string.settings_invitation_sent) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(error = null, successMessage = null) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    chatId: String,
    onNavigateBack: () -> Unit
) {
    val viewModel: ChatSettingsViewModel = koinViewModel(parameters = { parametersOf(chatId) })
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Получаем перевод строки по её ID перед вызовом LaunchedEffect
    val successMsgRes = uiState.successMessage
    val successMsg = successMsgRes?.let { stringResource(it) }

    LaunchedEffect(successMsg) {
        successMsg?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar("Error: $it")
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.chat?.name ?: stringResource(R.string.settings_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isEditingInfo) viewModel.cancelEditingInfo()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.isAdmin && !uiState.isEditingInfo) {
                        IconButton(onClick = { viewModel.startEditingInfo() }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.action_edit))
                        }
                    }
                    if (uiState.isEditingInfo) {
                        TextButton(
                            onClick = { viewModel.saveInfo() },
                            enabled = uiState.editName.isNotBlank() && !uiState.isLoading
                        ) {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (uiState.chat == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text(stringResource(R.string.settings_tab_info)) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text(stringResource(R.string.settings_tab_members)) }
                )
                if (uiState.isAdmin) {
                    Tab(
                        selected = uiState.selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        text = { Text(stringResource(R.string.settings_tab_moderation)) }
                    )
                }
            }

            val copiedMessage = stringResource(R.string.settings_link_copied)

            when (uiState.selectedTab) {
                0 -> InfoTab(
                    uiState = uiState,
                    onNameChange = viewModel::onNameChange,
                    onDescChange = viewModel::onDescChange,
                    onToggleJoinByLink = viewModel::toggleJoinByLink,
                    onToggleJoinByTag = viewModel::toggleJoinByTag,
                    onToggleAllowReactions = viewModel::toggleAllowReactions,
                    onRegenerateLink = { viewModel.regenerateInviteLink() },
                    onCopyLink = { link ->
                        clipboard.setText(AnnotatedString(link))
                        scope.launch { snackbar.showSnackbar(copiedMessage) }
                    },
                    onLeave = onNavigateBack
                )
                1 -> MembersTab(
                    uiState = uiState,
                    currentUid = viewModel.currentUid,
                    onModerate = viewModel::moderateUser,
                    onSetRole = viewModel::setMemberRole,
                    onInvite = viewModel::inviteUser
                )
                2 -> ModerationTab(
                    uiState = uiState,
                    onModerate = viewModel::moderateUser
                )
            }
        }
    }
}

// ─── Info Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun InfoTab(
    uiState: ChatSettingsUiState,
    onNameChange: (String) -> Unit,
    onDescChange: (String) -> Unit,
    onToggleJoinByLink: (Boolean) -> Unit,
    onToggleJoinByTag: (Boolean) -> Unit,
    onToggleAllowReactions: (Boolean) -> Unit,
    onRegenerateLink: () -> Unit,
    onCopyLink: (String) -> Unit,
    onLeave: () -> Unit
) {
    val chat = uiState.chat ?: return
    val isChannel = uiState.chatType == ChatType.CHANNEL

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(
                            if (isChannel) androidx.compose.ui.graphics.Color(0xFF6366F1)
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!chat.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = chat.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            if (isChannel) "📢" else (chat.name.firstOrNull()?.uppercase() ?: "G"),
                            fontSize = 28.sp
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        chat.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "@${chat.tag}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (isChannel) stringResource(R.string.settings_subscriber_count, chat.memberCount)
                        else stringResource(R.string.settings_member_count, chat.memberCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!uiState.isEditingInfo) {
            if (chat.description.isNotBlank()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            chat.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        } else {
            item {
                VlTextField(
                    value = uiState.editName,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.create_field_name),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                VlTextField(
                    value = uiState.editDescription,
                    onValueChange = onDescChange,
                    label = stringResource(R.string.create_field_description),
                    supportingText = "${uiState.editDescription.length}/160",
                    maxLines = 4,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (uiState.isAdmin) {
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_invite_link))
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (uiState.inviteLink.isNotBlank()) {
                            Text(
                                uiState.inviteLink,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onCopyLink(uiState.inviteLink) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_copy))
                                }
                                OutlinedButton(
                                    onClick = onRegenerateLink,
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_regenerate_link))
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.settings_invite_link_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = onRegenerateLink,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) { Text(stringResource(R.string.settings_generate_link)) }
                        }
                    }
                }
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.settings_toggle_join_by_link),
                    subtitle = stringResource(R.string.settings_toggle_join_by_link_sub),
                    checked = uiState.joinByLink,
                    onCheckedChange = onToggleJoinByLink
                )
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Tag,
                    title = stringResource(R.string.settings_toggle_join_by_tag),
                    subtitle = stringResource(R.string.settings_toggle_join_by_tag_sub),
                    checked = uiState.joinByTag,
                    onCheckedChange = onToggleJoinByTag
                )
            }

            if (isChannel) {
                item { SettingsSectionHeader(stringResource(R.string.settings_section_channel)) }
                item {
                    SettingsToggleRow(
                        icon = Icons.Default.EmojiEmotions,
                        title = stringResource(R.string.settings_toggle_reactions),
                        subtitle = stringResource(R.string.settings_toggle_reactions_sub),
                        checked = uiState.allowReactions,
                        onCheckedChange = onToggleAllowReactions
                    )
                }
            }
        }

        if (!uiState.isOwner) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                OutlinedButton(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isChannel) stringResource(R.string.settings_leave_channel)
                        else stringResource(R.string.settings_leave_group)
                    )
                }
            }
        }
    }
}

// ─── Members Tab ──────────────────────────────────────────────────────────────

@Composable
private fun MembersTab(
    uiState: ChatSettingsUiState,
    currentUid: String,
    onModerate: (String, String, Int?) -> Unit,
    onSetRole: (String, String) -> Unit,
    onInvite: (String) -> Unit
) {
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteUsername by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<Member?>(null) }
    var showMuteDialog by remember { mutableStateOf(false) }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false; inviteUsername = "" },
            title = { Text(stringResource(R.string.settings_dialog_invite_title)) },
            text = {
                VlTextField(
                    value = inviteUsername,
                    onValueChange = { inviteUsername = it.lowercase().removePrefix("@").trim() },
                    label = stringResource(R.string.settings_dialog_invite_field),
                    prefix = "@",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onInvite(inviteUsername)
                        showInviteDialog = false
                        inviteUsername = ""
                    },
                    enabled = inviteUsername.length >= 3
                ) { Text(stringResource(R.string.settings_dialog_invite_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false; inviteUsername = "" }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showMuteDialog && actionTarget != null) {
        val target = actionTarget!!
        var duration by remember { mutableStateOf("60") }
        AlertDialog(
            onDismissRequest = { showMuteDialog = false; actionTarget = null },
            title = { Text(stringResource(R.string.dialog_mute_title)) },
            text = {
                VlTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = stringResource(R.string.dialog_mute_field),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onModerate(target.uid, "mute", duration.toIntOrNull() ?: 60)
                    showMuteDialog = false; actionTarget = null
                }) { Text(stringResource(R.string.dialog_mute_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showMuteDialog = false; actionTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    actionTarget?.let { member ->
        if (!showMuteDialog) {
            val profile = uiState.memberProfiles[member.uid]
            AlertDialog(
                onDismissRequest = { actionTarget = null },
                title = { Text("@${profile?.username ?: member.uid}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (member.role == "member") {
                            TextButton(
                                onClick = { onSetRole(member.uid, "admin"); actionTarget = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_promote)) }
                        } else if (member.role == "admin") {
                            TextButton(
                                onClick = { onSetRole(member.uid, "member"); actionTarget = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_demote)) }
                        }

                        if (member.muted) {
                            TextButton(
                                onClick = { onModerate(member.uid, "unmute", null); actionTarget = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_unmute)) }
                        } else {
                            TextButton(
                                onClick = { showMuteDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_mute)) }
                        }

                        if (member.mediaRestricted) {
                            TextButton(
                                onClick = { onModerate(member.uid, "unrestrictMedia", null); actionTarget = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_allow_media)) }
                        } else {
                            TextButton(
                                onClick = { onModerate(member.uid, "restrictMedia", null); actionTarget = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_restrict_media)) }
                        }

                        if (member.banned) {
                            TextButton(
                                onClick = { onModerate(member.uid, "unban", null); actionTarget = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_unban), color = MaterialTheme.colorScheme.primary) }
                        } else {
                            TextButton(
                                onClick = { onModerate(member.uid, "ban", null); actionTarget = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.member_ban), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { actionTarget = null }) { Text(stringResource(R.string.action_close)) }
                }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (uiState.isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showInviteDialog = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_invite_member),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(0.3f))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(uiState.sortedMembers, key = { it.uid }) { member ->
                val profile = uiState.memberProfiles[member.uid]
                MemberRow(
                    member = member,
                    displayName = profile?.displayName ?: member.uid,
                    username = profile?.username ?: "",
                    avatarUrl = profile?.avatarUrl,
                    isSelf = member.uid == currentUid,
                    canTakeAction = uiState.isAdmin && member.uid != currentUid && !member.isOwner(),
                    onActionClick = { if (uiState.isAdmin) actionTarget = member }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: Member,
    displayName: String,
    username: String,
    avatarUrl: String?,
    isSelf: Boolean,
    canTakeAction: Boolean,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canTakeAction) Modifier.clickable(onClick = onActionClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Если это текущий юзер, отображаем "Имя (вы)" с помощью ресурсов
                val finalDisplayName = if (isSelf) stringResource(R.string.settings_member_you, displayName) else displayName

                Text(
                    finalDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (username.isNotEmpty()) {
                Text(
                    "@$username",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (member.role) {
                "owner" -> RoleBadge(stringResource(R.string.role_owner), MaterialTheme.colorScheme.tertiary)
                "admin" -> RoleBadge(stringResource(R.string.role_admin), MaterialTheme.colorScheme.primary)
            }
            if (member.banned) RoleBadge(stringResource(R.string.badge_banned), MaterialTheme.colorScheme.error)
            else if (member.muted) RoleBadge(stringResource(R.string.badge_muted), MaterialTheme.colorScheme.onSurfaceVariant)
            else if (member.mediaRestricted) RoleBadge(stringResource(R.string.badge_no_media), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoleBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// ─── Moderation Tab ───────────────────────────────────────────────────────────

@Composable
private fun ModerationTab(
    uiState: ChatSettingsUiState,
    onModerate: (String, String, Int?) -> Unit
) {
    if (uiState.bannedMembers.isEmpty() && uiState.mutedMembers.isEmpty() && uiState.restrictedMembers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircleOutline,
                    null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.moderation_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (uiState.bannedMembers.isNotEmpty()) {
            item { SettingsSectionHeader(stringResource(R.string.moderation_section_banned, uiState.bannedMembers.size)) }
            items(uiState.bannedMembers, key = { "banned_${it.uid}" }) { member ->
                val profile = uiState.memberProfiles[member.uid]
                ModerationRow(
                    displayName = profile?.displayName ?: member.uid,
                    username = profile?.username ?: "",
                    statusLabel = stringResource(R.string.moderation_status_banned),
                    actionLabel = stringResource(R.string.member_unban),
                    actionColor = MaterialTheme.colorScheme.primary,
                    onAction = { onModerate(member.uid, "unban", null) }
                )
            }
        }

        if (uiState.mutedMembers.isNotEmpty()) {
            item { SettingsSectionHeader(stringResource(R.string.moderation_section_muted, uiState.mutedMembers.size)) }
            items(uiState.mutedMembers, key = { "muted_${it.uid}" }) { member ->
                val profile = uiState.memberProfiles[member.uid]
                ModerationRow(
                    displayName = profile?.displayName ?: member.uid,
                    username = profile?.username ?: "",
                    statusLabel = stringResource(R.string.moderation_status_muted),
                    actionLabel = stringResource(R.string.member_unmute),
                    actionColor = MaterialTheme.colorScheme.primary,
                    onAction = { onModerate(member.uid, "unmute", null) }
                )
            }
        }

        if (uiState.restrictedMembers.isNotEmpty()) {
            item { SettingsSectionHeader(stringResource(R.string.moderation_section_restricted, uiState.restrictedMembers.size)) }
            items(uiState.restrictedMembers, key = { "restricted_${it.uid}" }) { member ->
                val profile = uiState.memberProfiles[member.uid]
                ModerationRow(
                    displayName = profile?.displayName ?: member.uid,
                    username = profile?.username ?: "",
                    statusLabel = stringResource(R.string.moderation_status_restricted),
                    actionLabel = stringResource(R.string.member_allow),
                    actionColor = MaterialTheme.colorScheme.primary,
                    onAction = { onModerate(member.uid, "unrestrictMedia", null) }
                )
            }
        }
    }
}

@Composable
private fun ModerationRow(
    displayName: String,
    username: String,
    statusLabel: String,
    actionLabel: String,
    actionColor: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (username.isNotEmpty()) {
                Text("@$username", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction) {
            Text(actionLabel, color = actionColor)
        }
    }
}

// ─── Shared UI helpers ────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}