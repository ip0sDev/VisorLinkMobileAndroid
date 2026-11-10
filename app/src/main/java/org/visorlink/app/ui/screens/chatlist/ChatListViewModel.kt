package org.visorlink.app.ui.screens.chatlist

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.model.*
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.DraftManager
import org.visorlink.app.utils.NotificationHelper
import org.visorlink.app.utils.PresenceManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch

import org.visorlink.app.utils.NetworkMonitor

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val draftManager: DraftManager,
    private val context: Context? = null,
    private val sidebarTypingManager: org.visorlink.app.utils.SidebarTypingManager? = null,
    private val networkMonitor: NetworkMonitor? = null,
    private val blockRepository: org.visorlink.app.data.repository.BlockRepository? = null,
    private val chatFoldersRepository: org.visorlink.app.data.repository.ChatFoldersRepository? = null,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor?.isOnline
        ?: MutableStateFlow(true).asStateFlow()

    val syncState: StateFlow<SyncState> = isOnline.map { online ->
        if (!online) SyncState.WAITING_FOR_NETWORK else SyncState.SYNCED
    }.stateIn(viewModelScope, SharingStarted.Eagerly, if (networkMonitor?.isOnline?.value == false) SyncState.WAITING_FOR_NETWORK else SyncState.SYNCED)

    val currentUid: String get() = auth.currentUser?.uid.orEmpty()
    val diagInfo: StateFlow<org.visorlink.app.data.repository.ChatDiagInfo> = chatRepository.diagInfo

    private val authUidFlow: Flow<String> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.uid.orEmpty())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private val rawTypingMap: StateFlow<Map<String, Boolean>> = sidebarTypingManager?.typingMap
        ?: MutableStateFlow<Map<String, Boolean>>(emptyMap()).asStateFlow()

    val currentUser: StateFlow<UserProfile?> = userRepository.currentUserFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chats: StateFlow<List<Chat>> = authUidFlow.flatMapLatest { uid ->
        if (uid.isEmpty()) flowOf(emptyList())
        else chatRepository.allChatsFlow(uid)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Непрочитанные приглашения в группы и каналы (`users/{uid}/notifications`). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val invites: StateFlow<List<AppNotification>> = authUidFlow.flatMapLatest { uid ->
        if (uid.isEmpty()) flowOf(emptyList())
        else chatRepository.notificationsFlow(uid)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Чаты со строкой «Приглашения», вставленной по дате последнего приглашения. */
    private val prefs = context?.getSharedPreferences("visorlink_settings", Context.MODE_PRIVATE)

    private val _activeFolderId = MutableStateFlow<String?>(prefs?.getString("vl_active_folder", null))
    val activeFolderId: StateFlow<String?> = _activeFolderId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<ChatFolder>> = if (chatFoldersRepository != null) {
        authUidFlow.flatMapLatest { uid ->
            if (uid.isEmpty()) flowOf(emptyList())
            else combine(chatFoldersRepository.foldersFlow(uid), currentUser) { folderList, user ->
                if (user?.isPro == true) folderList else emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    } else {
        MutableStateFlow<List<ChatFolder>>(emptyList()).asStateFlow()
    }

    val unreadByFolder: StateFlow<Map<String, Int>> = combine(chats, folders) { list, folderList ->
        folderList.associate { folder ->
            val count = list.filter { chatInFolder(it, folder, it.unreadCountFor(currentUid)) }
                .sumOf { it.unreadCountFor(currentUid) }
            folder.id to count
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val totalUnread: StateFlow<Int> = chats.map { list ->
        list.sumOf { it.unreadCountFor(currentUid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun selectFolder(folderId: String?) {
        _activeFolderId.value = folderId
        prefs?.edit()?.let {
            if (folderId == null) it.remove("vl_active_folder") else it.putString("vl_active_folder", folderId)
            it.apply()
        }
    }

    val listEntries: StateFlow<List<Chat>> = combine(chats, invites, folders, activeFolderId) { list, inv, folderList, activeId ->
        val filteredChats = if (activeId == null) {
            list
        } else {
            val folder = folderList.firstOrNull { it.id == activeId }
            if (folder == null) list else list.filter { chatInFolder(it, folder, it.unreadCountFor(currentUid)) }
        }
        withEntryByDate(filteredChats, invitesEntry(currentUid, inv))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())






    fun isInvitesEntry(chat: Chat): Boolean = chat.id == invitesEntryId(currentUid)

    /**
     * «Печатает» в списке чатов. Личные чаты с блокировкой (в любую сторону) не показываем:
     * заблокировавший меня не светит активность, а чужое «печатает» от заблокированного мной не нужно.
     */
    val typingMap: StateFlow<Map<String, Boolean>> = combine(
        rawTypingMap,
        chats,
        blockRepository?.blocks ?: flowOf(emptySet<String>()),
        blockRepository?.blockedBy ?: flowOf(emptySet<String>()),
    ) { typing, list, blocks, blockedBy ->
        if (typing.isEmpty() || (blocks.isEmpty() && blockedBy.isEmpty())) typing
        else {
            val byId = list.associateBy { it.id }
            typing.filterKeys { chatId ->
                val chat = byId[chatId]
                if (chat == null || chat.chatType() != ChatType.DIRECT) true
                else chat.otherParticipantId(currentUid).let { it !in blocks && it !in blockedBy }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val drafts: StateFlow<Map<String, String>> = draftManager.draftsFlow

    private val _profileCache = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    /** Профили собеседников. Заблокировавшим меня аватарка и «в сети» скрыты (кэш мог их помнить). */
    val profileCache: StateFlow<Map<String, UserProfile>> = combine(
        _profileCache,
        blockRepository?.blockedBy ?: flowOf(emptySet<String>()),
    ) { cache, blockedBy ->
        if (blockedBy.isEmpty()) cache
        else cache.mapValues { (uid, p) -> if (uid in blockedBy) p.copy(avatarUrl = null, online = false) else p }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val savedMessagesEntry: Chat = Chat(
        id   = "saved_${currentUid}",
        type = "direct",
        name = "Избранное",
        lastMessage = "Нажмите, чтобы открыть",
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                userRepository.getUserProfile(currentUid)
                kotlinx.coroutines.delay(800)
            } catch (e: Exception) {
                Log.e("ChatListVM", "Failed to refresh chats: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private val observedPresenceUids = mutableSetOf<String>()
    private val observedProfileUids = mutableSetOf<String>()

    init {
        sidebarTypingManager?.startListening(currentUid)
        viewModelScope.launch {
            chats.collect { list ->
                sidebarTypingManager?.syncChats(list.map { it.id })
                launch(Dispatchers.IO) {
                    list.forEach { chat ->
                        if (chat.unreadCountFor(currentUid) == 0) {
                            context?.let { NotificationHelper.clearNotification(it, chat.id) }
                        }
                    }
                }

                val directChats = list.filter { it.chatType() == ChatType.DIRECT || it.chatType() == ChatType.EMERGENCY }

                // Мгновенный посев кэша профилей из RAM-кэша UserRepository и participantData чатов для отображения аватарок без ожидания сети
                val seedProfiles = mutableMapOf<String, UserProfile>()
                directChats.forEach { chat ->
                    val otherId = chat.otherParticipantId(currentUid)
                    if (otherId.isNotEmpty() && otherId !in _profileCache.value) {
                        val memCached = userRepository.getMemoryCachedProfile(otherId)
                        if (memCached != null) {
                            seedProfiles[otherId] = memCached
                        } else {
                            val pData = chat.participantData[otherId]
                            val name = pData?.get("displayName") ?: pData?.get("username")
                            val av = pData?.get("avatarUrl") ?: chat.avatarUrl
                            if (name != null || av != null) {
                                seedProfiles[otherId] = UserProfile(
                                    uid = otherId,
                                    username = pData?.get("username").orEmpty(),
                                    displayName = name.orEmpty(),
                                    avatarUrl = av
                                )
                            }
                        }
                    }
                }
                if (seedProfiles.isNotEmpty()) {
                    _profileCache.update { it + seedProfiles }
                }

                val directUids = directChats
                    .map { it.otherParticipantId(currentUid) }
                    .filter { it.isNotEmpty() }
                    .distinct()

                directUids.forEach { uid ->
                    if (observedProfileUids.add(uid)) {
                        launch {
                            try {
                                userRepository.userProfileFlow(uid).collect { profile ->
                                    if (profile != null) {
                                        _profileCache.update { currentMap ->
                                            val existing = currentMap[uid]
                                            val merged = if (existing != null) {
                                                profile.copy(
                                                    avatarUrl = profile.avatarUrl?.takeIf { it.isNotBlank() } ?: existing.avatarUrl,
                                                    online = existing.online
                                                )
                                            } else {
                                                profile
                                            }
                                            if (existing != merged) {
                                                currentMap + (uid to merged)
                                            } else {
                                                currentMap
                                            }
                                        }
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                org.visorlink.app.utils.DiagnosticLogBuffer.e("ChatListVM", "Failed to observe profile flow for $uid: ${e.message}", e)
                            }
                        }
                    }

                    if (observedPresenceUids.add(uid)) {
                        launch {
                            PresenceManager.observePresence(uid).collect { presence ->
                                if (presence != null) {
                                    _profileCache.update { currentMap ->
                                        val existing = currentMap[uid]
                                        if (existing != null && existing.online != presence.online) {
                                            currentMap + (uid to existing.copy(online = presence.online))
                                        } else {
                                            currentMap
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sidebarTypingManager?.stopListening()
    }
}