package org.visorlink.app.ui.screens.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.visorlink.app.BuildConfig
import org.visorlink.app.data.model.CuratedChannel
import org.visorlink.app.data.model.FeedPost
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.FeedRepository

enum class FeedTab { SUBSCRIPTIONS, CURATED }

data class FeedUiState(
    val tab: FeedTab = FeedTab.SUBSCRIPTIONS,
    /** Посты моих каналов, с учётом ещё не подтверждённых нажатий 👍. */
    val posts: List<FeedPost> = emptyList(),
    val postsLoading: Boolean = true,
    val postsError: Boolean = false,
    val curated: List<CuratedChannel> = emptyList(),
    val curatedLoading: Boolean = false,
    val curatedError: Boolean = false,
    /** Каналы, где я участник: в каталоге у них «Перейти» вместо «Подписаться». */
    val subscribedIds: Set<String> = emptySet(),
    /** Каналы, на которые сейчас идёт подписка. */
    val joining: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
)

sealed interface FeedEvent {
    data object LikeFailed : FeedEvent
    data class JoinFailed(val message: String?) : FeedEvent
}

/**
 * Экран «Каналы». Лайк — оптимистичный: кнопка переключается сразу, запрос уходит в фоне.
 * На один пост одновременно идёт не больше одного запроса; если за это время нажали ещё раз,
 * следом уходит итоговое состояние — двойной тап не даёт двойного голоса, а сервер к тому же
 * идемпотентен. Просмотр засчитывается, когда пост побывал на экране (см. FeedScreen), а не по
 * тапу.
 */
class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FeedEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<FeedEvent> = _events.asSharedFlow()

    /** Посты как их видит сервер — без [likeIntents]. */
    private var serverPosts: List<FeedPost> = emptyList()

    /** Желаемое состояние 👍 по ключу поста, пока сервер его не подтвердил. */
    private val likeIntents = HashMap<String, Boolean>()
    private val likeJobs = HashMap<String, Job>()

    /** Посты, просмотр которых уже отправлен в этой сессии. */
    private val viewedKeys = HashSet<String>()

    /** Пользователь сам выбрал вкладку — больше не переключаем её за него. */
    private var tabChosen = false

    private var subscriptionsJob: Job? = null
    private var curatedLoaded = false

    init {
        observeSubscriptions()
    }

    fun selectTab(tab: FeedTab) {
        tabChosen = true
        _uiState.update { it.copy(tab = tab) }
        if (tab == FeedTab.CURATED && !curatedLoaded) loadCurated()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        when (_uiState.value.tab) {
            FeedTab.CURATED -> loadCurated()
            FeedTab.SUBSCRIPTIONS -> observeSubscriptions()
        }
    }

    fun retry() = refresh()

    private fun observeSubscriptions() {
        val uid = currentUid
        if (uid.isEmpty()) {
            _uiState.update { it.copy(postsLoading = false, isRefreshing = false) }
            return
        }
        subscriptionsJob?.cancel()
        subscriptionsJob = viewModelScope.launch {
            try {
                feedRepository.subscriptionsFlow(uid).collect { subs ->
                    serverPosts = subs.posts
                    reconcileLikes()
                    _uiState.update {
                        it.copy(
                            posts = displayed(),
                            postsLoading = false,
                            postsError = false,
                            isRefreshing = if (it.tab == FeedTab.SUBSCRIPTIONS) false else it.isRefreshing,
                            subscribedIds = subs.channels.map { ch -> ch.id }.toSet(),
                        )
                    }
                    // Подписок нет — показываем каталог, пока человек сам не выбрал вкладку
                    if (subs.channels.isEmpty() && !tabChosen && _uiState.value.tab != FeedTab.CURATED) {
                        _uiState.update { it.copy(tab = FeedTab.CURATED) }
                        if (!curatedLoaded) loadCurated()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "subscriptions failed", e)
                _uiState.update { it.copy(postsLoading = false, postsError = true, isRefreshing = false) }
            }
        }
    }

    private fun loadCurated() {
        curatedLoaded = true
        _uiState.update { it.copy(curatedLoading = it.curated.isEmpty(), curatedError = false) }
        viewModelScope.launch {
            try {
                val list = feedRepository.curatedChannels()
                _uiState.update { it.copy(curated = list, curatedLoading = false, isRefreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "getCuratedChannels failed", e)
                curatedLoaded = false
                _uiState.update { it.copy(curatedLoading = false, curatedError = it.curated.isEmpty(), isRefreshing = false) }
            }
        }
    }

    fun join(channel: CuratedChannel) {
        if (channel.id in _uiState.value.joining || channel.id in _uiState.value.subscribedIds) return
        _uiState.update { it.copy(joining = it.joining + channel.id) }
        viewModelScope.launch {
            try {
                val ok = chatRepository.joinChannel(channel.id, channel.tag.ifBlank { null })
                // Слушатель моих каналов добавит его и сам, но кнопка не должна ждать сети
                _uiState.update {
                    it.copy(joining = it.joining - channel.id, subscribedIds = if (ok) it.subscribedIds + channel.id else it.subscribedIds)
                }
                if (!ok) _events.tryEmit(FeedEvent.JoinFailed(null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(joining = it.joining - channel.id) }
                _events.tryEmit(FeedEvent.JoinFailed(e.message))
            }
        }
    }

    fun toggleLike(post: FeedPost) {
        val uid = currentUid
        if (uid.isEmpty() || !post.likesEnabled) return
        val key = post.key
        val current = likeIntents[key] ?: serverPosts.firstOrNull { it.key == key }?.isLikedBy(uid) ?: post.isLikedBy(uid)
        likeIntents[key] = !current
        publish()
        if (likeJobs[key]?.isActive == true) return // текущий запрос отправит и это нажатие

        val chatId = post.channel.id
        val messageId = post.message.id
        likeJobs[key] = viewModelScope.launch {
            var sent: Boolean? = null
            while (true) {
                val want = likeIntents[key] ?: break
                if (want == sent) break
                try {
                    feedRepository.setLike(chatId, messageId, want)
                    sent = want
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "toggleLike failed for $key", e)
                    likeIntents.remove(key)
                    _events.tryEmit(FeedEvent.LikeFailed)
                    break
                }
            }
            likeJobs.remove(key)
            reconcileLikes()
            publish()
        }
    }

    /**
     * Посты, которые пробыли на экране достаточно долго. Каждый засчитывается один раз за сессию
     * (сервер и сам считает человека один раз); свои посты не считаются.
     */
    fun onPostsSeen(keys: Collection<String>) {
        val uid = currentUid
        if (uid.isEmpty()) return
        val fresh = serverPosts.filter { it.key in keys && it.message.senderId != uid && viewedKeys.add(it.key) }
        fresh.groupBy { it.channel.id }.forEach { (chatId, posts) ->
            viewModelScope.launch {
                try {
                    feedRepository.recordViews(chatId, posts.map { it.message.id })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Не дошло — пусть засчитается, когда пост попадётся снова
                    viewedKeys.removeAll(posts.map { it.key }.toSet())
                }
            }
        }
    }

    /** Убирает намерения, которые сервер уже подтвердил (или чей пост исчез). */
    private fun reconcileLikes() {
        val uid = currentUid
        val byKey = serverPosts.associateBy { it.key }
        likeIntents.entries.removeAll { (key, want) ->
            if (likeJobs[key]?.isActive == true) return@removeAll false
            val server = byKey[key] ?: return@removeAll true
            server.isLikedBy(uid) == want
        }
    }

    private fun displayed(): List<FeedPost> {
        if (likeIntents.isEmpty()) return serverPosts
        val uid = currentUid
        return serverPosts.map { p -> likeIntents[p.key]?.let { p.withLike(uid, it) } ?: p }
    }

    private fun publish() {
        _uiState.update { it.copy(posts = displayed()) }
    }

    private companion object {
        const val TAG = "FeedViewModel"
    }
}
